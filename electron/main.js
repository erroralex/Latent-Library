/**
 * @file main.js
 * @description The main entry point for the Electron application, responsible for lifecycle management and backend orchestration.
 *
 * This script manages the creation of the main browser window and the lifecycle of the Spring Boot
 * backend process. It handles the secure handshake between Electron and the backend, provides
 * IPC handlers for system-level operations (like folder selection), and ensures a clean
 * shutdown of all processes when the application is closed.
 */
const {app, BrowserWindow, ipcMain, dialog} = require('electron');
const path = require('path');
const {spawn} = require('child_process');
const http = require('http');
const fs = require('fs');

let mainWindow;
let backendProcess;
let backendPort = null;
let handshakeToken = null;

const JAR_NAME = 'backend-0.0.1-SNAPSHOT.jar';

function getBackendPaths() {
    let javaExe, jarPath, workingDir, appDataDir;

    if (app.isPackaged) {
        javaExe = path.join(process.resourcesPath, 'runtime', 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
        jarPath = path.join(process.resourcesPath, 'runtime', 'app', JAR_NAME);
        workingDir = path.join(process.resourcesPath, 'runtime', 'app');
        appDataDir = path.dirname(app.getPath('exe'));
    } else {
        javaExe = 'java';
        jarPath = path.join(__dirname, '../backend/target', JAR_NAME);
        workingDir = path.join(__dirname, '../backend');
        appDataDir = workingDir;
    }

    const dataDir = path.join(appDataDir, 'data');
    if (!fs.existsSync(dataDir)) {
        fs.mkdirSync(dataDir, {recursive: true});
    }

    const portFile = path.join(dataDir, 'port.txt');
    return {javaExe, jarPath, workingDir, portFile, appDataDir};
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
            preload: path.join(__dirname, 'preload.js'),
            additionalArguments: [`--handshake-token=${handshakeToken}`]
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
    const {javaExe, jarPath, workingDir, portFile, appDataDir} = getBackendPaths();

    if (fs.existsSync(portFile)) {
        try {
            fs.unlinkSync(portFile);
        } catch (e) {
        }
    }

    console.log(`Starting backend with: ${javaExe} -jar ${jarPath}`);

    backendProcess = spawn(javaExe, [
        '-jar', jarPath,
        `--app.data.dir=${appDataDir}`
    ], {
        cwd: workingDir
    });

    backendProcess.stdout.on('data', (data) => {
        console.log(`[Backend]: ${data}`);
    });

    backendProcess.stderr.on('data', (data) => {
        console.error(`[Backend Error]: ${data}`);
    });

    const pollInterval = setInterval(() => {
        if (fs.existsSync(portFile)) {
            try {
                const content = fs.readFileSync(portFile, 'utf8').trim();
                const parts = content.split(':');
                if (parts.length === 2) {
                    backendPort = parseInt(parts[0]);
                    handshakeToken = parts[1];
                    console.log(`Backend detected on port: ${backendPort}`);
                    clearInterval(pollInterval);
                    createWindow();
                }
            } catch (e) {
                console.error("Error reading port file:", e);
            }
        }
    }, 200);

    setTimeout(() => {
        if (!backendPort) {
            console.error("Backend failed to start within 30s");
            clearInterval(pollInterval);
        }
    }, 30000);
}

app.on('ready', () => {
    ipcMain.handle('dialog:openDirectory', async () => {
        const {canceled, filePaths} = await dialog.showOpenDialog(mainWindow, {
            properties: ['openDirectory']
        });
        return canceled ? null : filePaths[0];
    });

    ipcMain.on('window-minimize', () => mainWindow?.minimize());
    ipcMain.on('window-maximize', () => {
        if (mainWindow?.isMaximized()) mainWindow.unmaximize();
        else mainWindow?.maximize();
    });
    ipcMain.on('window-close', () => mainWindow?.close());

    startBackend();
});

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
});

app.on('will-quit', async (event) => {
    if (backendProcess && backendPort) {
        event.preventDefault();
        const req = http.request({
            hostname: 'localhost',
            port: backendPort,
            path: '/api/system/shutdown',
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${handshakeToken}`
            }
        }, () => {
            setTimeout(() => {
                backendProcess = null;
                app.quit();
            }, 500);
        });
        req.on('error', () => {
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
        if (process.platform === 'win32') {
            try {
                const {execSync} = require('child_process');
                execSync(`taskkill /pid ${backendProcess.pid} /f /t`);
            } catch (e) {
            }
        } else {
            backendProcess.kill();
        }
        backendProcess = null;
    }
}
