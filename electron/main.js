/**
 * @file main.js (Electron)
 * @description The main process entry point for the AI Toolbox desktop application.
 *
 * This module serves as the core orchestrator for the Electron-based desktop environment.
 * It manages the entire application lifecycle, from initial process spawning to graceful
 * system shutdown. The script is responsible for bridging the gap between the native
 * operating system, the Electron frontend, and the Spring Boot backend service.
 *
 * Key Responsibilities:
 * - **Backend Lifecycle Management:** Spawns and monitors the Java Spring Boot backend
 *   process. It implements a dynamic port discovery mechanism via a shared filesystem
 *   handshake (port.txt) to ensure reliable communication.
 * - **Portable Runtime Support:** Implements a hybrid Java resolution strategy that
 *   prioritizes a bundled, private JRE for true "zero-dependency" portability while
 *   falling back to the system PATH during development.
 * - **Native Window Orchestration:** Configures and manages the primary BrowserWindow,
 *   implementing a frameless UI with custom title bar support and native window state
 *   management (minimize, maximize, close).
 * - **IPC Communication Bridge:** Provides a secure Inter-Process Communication (IPC)
 *   layer for the frontend to access native OS features, such as system file dialogs.
 * - **Graceful Shutdown Protocol:** Ensures system integrity by orchestrating a
 *   coordinated shutdown sequence, sending termination signals to the backend service
 *   before exiting the Electron process.
 */
const {app, BrowserWindow, ipcMain, dialog} = require('electron');
const path = require('path');
const {spawn} = require('child_process');
const http = require('http');
const fs = require('fs');

let mainWindow;
let backendProcess;
let backendPort = null;

const JAR_NAME = 'backend-0.0.1-SNAPSHOT.jar';

function getBackendPaths() {
    let jarPath, workingDir, portFile, appDataDir, javaBin;

    if (app.isPackaged) {
        jarPath = path.join(process.resourcesPath, 'backend', JAR_NAME);
        workingDir = path.join(process.resourcesPath, 'backend');

        // Bundled JRE support: Check for a local JRE in the resources folder
        // This ensures the app is truly portable and doesn't require a system-wide Java installation.
        const bundledJava = path.join(process.resourcesPath, 'jre', 'bin', process.platform === 'win32' ? 'java.exe' : 'java');

        if (fs.existsSync(bundledJava)) {
            javaBin = bundledJava;
            console.log('Using bundled JRE:', javaBin);
        } else {
            javaBin = 'java';
            console.warn('Bundled JRE not found at ' + bundledJava + '. Falling back to system java.');
        }

        appDataDir = path.dirname(app.getPath('exe'));
    } else {
        jarPath = path.join(__dirname, '../backend/target', JAR_NAME);
        workingDir = path.join(__dirname, '../backend');
        javaBin = 'java';
        appDataDir = workingDir;
    }

    const dataDir = path.join(appDataDir, 'data');
    if (!fs.existsSync(dataDir)) {
        fs.mkdirSync(dataDir, {recursive: true});
    }

    portFile = path.join(dataDir, 'port.txt');
    return {jarPath, workingDir, portFile, appDataDir, javaBin};
}

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
    mainWindow.on('closed', function () {
        mainWindow = null;
    });
}

function startBackend() {
    const {jarPath, workingDir, portFile, appDataDir, javaBin} = getBackendPaths();

    console.log('Starting backend JAR:', jarPath);
    console.log('Backend Working Directory:', workingDir);
    console.log('App Data Directory:', appDataDir);
    console.log('Java Executable:', javaBin);

    if (fs.existsSync(portFile)) {
        try {
            fs.unlinkSync(portFile);
        } catch (e) {
            console.warn("Could not delete old port file:", e.message);
        }
    }

    backendProcess = spawn(javaBin, [
        '-jar', jarPath,
        `--app.data.dir=${appDataDir}`
    ], {
        cwd: workingDir
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

    // Poll for the port file written by the Spring Boot backend on startup.
    // This allows the app to use a dynamic port and ensures the UI only loads
    // once the server is actually ready to accept connections.
    const pollInterval = setInterval(() => {
        if (fs.existsSync(portFile)) {
            try {
                const portStr = fs.readFileSync(portFile, 'utf8').trim();
                backendPort = parseInt(portStr);
                console.log(`Backend detected on port: ${backendPort} (via port.txt)`);
                clearInterval(pollInterval);
                createWindow();
            } catch (e) {
                console.error("Error reading port file:", e);
            }
        }
    }, 200);

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
