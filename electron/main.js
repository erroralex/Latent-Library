/**
 * @file main.js (Electron)
 * @description The main process entry point for the Electron-based desktop application.
 *
 * This script manages the application lifecycle, creates the native window, and orchestrates
 * the integration between the Electron frontend and the Spring Boot backend. It handles
 * process spawning, IPC communication for window controls, and graceful shutdown procedures.
 *
 * Key responsibilities:
 * - Backend Lifecycle: Spawns the Java Spring Boot process and monitors its health.
 * - Window Management: Configures a frameless, maximized BrowserWindow with custom title bar support.
 * - IPC Bridge: Implements handlers for native dialogs (folder selection) and window state (min/max/close).
 * - Graceful Shutdown: Sends a shutdown signal to the backend before terminating the Electron process.
 * - Development Support: Assumptions for JAR paths in development vs. production environments.
 */
const {app, BrowserWindow, ipcMain, dialog} = require('electron');
const path = require('path');
const {spawn} = require('child_process');
const http = require('http');
const fs = require('fs');

let mainWindow;
let backendProcess;
let backendPort = null; // Dynamic port

const JAR_NAME = 'backend-0.0.1-SNAPSHOT.jar';
const PORT_FILE = path.join(__dirname, '../backend/data/port.txt');

function createWindow() {
    if (!backendPort) return;

    mainWindow = new BrowserWindow({
        width: 1280,
        height: 800,
        title: "AI Toolbox",
        icon: path.join(__dirname, '../frontend/src/assets/icon.png'),
        frame: false,
        webPreferences: {
            nodeIntegration: false,
            contextIsolation: true,
            preload: path.join(__dirname, 'preload.js')
        },
        autoHideMenuBar: true,
        show: false
    });

    mainWindow.maximize();
    mainWindow.show();

    mainWindow.loadURL(`http://localhost:${backendPort}`);
    //mainWindow.webContents.openDevTools();
    mainWindow.on('closed', function () {
        mainWindow = null;
    });
}

function startBackend() {
    const devJarPath = path.join(__dirname, '../backend/target', JAR_NAME);
    const projectRoot = path.join(__dirname, '../backend');

    console.log('Starting backend JAR:', devJarPath);
    console.log('Backend Working Directory:', projectRoot);

    // Remove old port file if it exists
    if (fs.existsSync(PORT_FILE)) {
        fs.unlinkSync(PORT_FILE);
    }

    backendProcess = spawn('java', ['-jar', devJarPath], {
        cwd: projectRoot
    });

    backendProcess.stdout.on('data', (data) => {
        console.log(`Backend: ${data.toString()}`);
    });

    backendProcess.stderr.on('data', (data) => {
        console.error(`Backend Error: ${data.toString()}`);
    });

    backendProcess.on('close', (code) => {
        console.log(`Backend process exited with code ${code}`);
    });

    // Poll for the port file
    const pollInterval = setInterval(() => {
        if (fs.existsSync(PORT_FILE)) {
            try {
                const portStr = fs.readFileSync(PORT_FILE, 'utf8').trim();
                backendPort = parseInt(portStr);
                console.log(`Backend detected on port: ${backendPort} (via port.txt)`);
                clearInterval(pollInterval);
                createWindow();
            } catch (e) {
                console.error("Error reading port file:", e);
            }
        }
    }, 200);

    // Timeout after 30 seconds
    setTimeout(() => {
        if (!backendPort) {
            console.error("Backend failed to start or write port file within 30s");
            clearInterval(pollInterval);
        }
    }, 30000);
}

app.on('ready', () => {
    ipcMain.handle('dialog:openDirectory', async () => {
        const {canceled, filePaths} = await dialog.showOpenDialog(mainWindow, {
            properties: ['openDirectory']
        });
        if (canceled) {
            return null;
        } else {
            return filePaths[0];
        }
    });

    ipcMain.on('window-minimize', () => {
        if (mainWindow) mainWindow.minimize();
    });

    ipcMain.on('window-maximize', () => {
        if (mainWindow) {
            if (mainWindow.isMaximized()) {
                mainWindow.unmaximize();
            } else {
                mainWindow.maximize();
            }
        }
    });

    ipcMain.on('window-close', () => {
        if (mainWindow) mainWindow.close();
    });

    startBackend();
});

app.on('window-all-closed', function () {
    if (process.platform !== 'darwin') app.quit();
});

app.on('will-quit', async (event) => {
    if (backendProcess && backendPort) {
        event.preventDefault();
        console.log('Requesting backend shutdown...');

        const req = http.request({
            hostname: 'localhost',
            port: backendPort,
            path: '/api/system/shutdown',
            method: 'POST'
        }, (res) => {
            console.log(`Backend shutdown response: ${res.statusCode}`);
            setTimeout(() => {
                backendProcess = null;
                app.quit();
            }, 1000);
        });

        req.on('error', (e) => {
            console.error(`Problem with shutdown request: ${e.message}`);
            killBackendProcess();
            app.quit();
        });

        req.end();
    } else {
        killBackendProcess();
    }
});

function killBackendProcess() {
    if (backendProcess) {
        console.log('Force killing backend process...');
        if (process.platform === 'win32') {
            try {
                const {execSync} = require('child_process');
                execSync(`taskkill /pid ${backendProcess.pid} /f /t`);
            } catch (e) {
                console.error('Failed to kill backend process:', e);
            }
        } else {
            backendProcess.kill();
        }
        backendProcess = null;
    }
}
