const { app, BrowserWindow, ipcMain, dialog } = require('electron');
const path = require('path');
const { spawn } = require('child_process');
const http = require('http');

let mainWindow;
let backendProcess;

const BACKEND_PORT = 8080;
const JAR_NAME = 'backend-0.0.1-SNAPSHOT.jar'; // Adjust version if needed

function createWindow() {
    mainWindow = new BrowserWindow({
        width: 1280,
        height: 800,
        title: "AI Toolbox", // Set application title
        icon: path.join(__dirname, '../frontend/src/assets/icon.png'), // Set application icon
        frame: false, // Remove OS titlebar
        webPreferences: {
            nodeIntegration: false,
            contextIsolation: true,
            preload: path.join(__dirname, 'preload.js')
        },
        autoHideMenuBar: true
    });

    // Load the Spring Boot app
    mainWindow.loadURL(`http://localhost:${BACKEND_PORT}`);

    mainWindow.on('closed', function () {
        mainWindow = null;
    });
}

function startBackend() {
    const jarPath = path.join(process.resourcesPath, 'backend', JAR_NAME);
    
    // In development, we might want to point to the target folder directly
    // const devJarPath = path.join(__dirname, '..', 'backend', 'target', JAR_NAME);
    
    // For this setup, let's assume we are running in dev mode and the user has built the jar
    const devJarPath = path.join(__dirname, '../backend/target', JAR_NAME);

    console.log('Starting backend from:', devJarPath);

    backendProcess = spawn('java', ['-jar', devJarPath], {
        cwd: path.dirname(devJarPath) // Set working directory to jar location so ./data is created there
    });

    backendProcess.stdout.on('data', (data) => {
        console.log(`Backend: ${data}`);
    });

    backendProcess.stderr.on('data', (data) => {
        console.error(`Backend Error: ${data}`);
    });

    backendProcess.on('close', (code) => {
        console.log(`Backend process exited with code ${code}`);
    });
}

function checkBackendReady() {
    const req = http.get(`http://localhost:${BACKEND_PORT}/api/folders/roots`, (res) => {
        if (res.statusCode === 200) {
            createWindow();
        } else {
            setTimeout(checkBackendReady, 1000);
        }
    });

    req.on('error', () => {
        setTimeout(checkBackendReady, 1000);
    });
    
    req.end();
}

app.on('ready', () => {
    ipcMain.handle('dialog:openDirectory', async () => {
        const { canceled, filePaths } = await dialog.showOpenDialog(mainWindow, {
            properties: ['openDirectory']
        });
        if (canceled) {
            return null;
        } else {
            return filePaths[0];
        }
    });

    // Window Controls
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
    checkBackendReady();
});

app.on('window-all-closed', function () {
    if (process.platform !== 'darwin') app.quit();
});

app.on('will-quit', async (event) => {
    if (backendProcess) {
        event.preventDefault(); // Prevent immediate quit
        console.log('Requesting backend shutdown...');
        
        const req = http.request({
            hostname: 'localhost',
            port: BACKEND_PORT,
            path: '/api/system/shutdown',
            method: 'POST'
        }, (res) => {
            console.log(`Backend shutdown response: ${res.statusCode}`);
            // Give it a moment to shut down gracefully
            setTimeout(() => {
                backendProcess = null;
                app.quit();
            }, 1000);
        });

        req.on('error', (e) => {
            console.error(`Problem with shutdown request: ${e.message}`);
            // If request fails, fall back to killing the process
            killBackendProcess();
            app.quit();
        });

        req.end();
    }
});

function killBackendProcess() {
    if (backendProcess) {
        console.log('Force killing backend process...');
        if (process.platform === 'win32') {
            try {
                const { execSync } = require('child_process');
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
