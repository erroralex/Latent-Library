/**
 * @file main.js
 * @description The main entry point for the Electron application, responsible for lifecycle management and backend orchestration.
 *
 * This script manages the creation of the main browser window and the lifecycle of the Spring Boot
 * backend process. It handles the secure handshake between Electron and the backend, provides
 * IPC handlers for system-level operations (like folder selection), and ensures a clean
 * shutdown of all processes when the application is closed.
 */
const {app, BrowserWindow, ipcMain, dialog, shell} = require('electron');
const path = require('path');
const {spawn} = require('child_process');
const http = require('http');
const fs = require('fs');

process.on('uncaughtException', (error) => {
    const errorLogPath = path.join(path.dirname(app.getPath('exe')), 'startup_error.log');
    const message = `[Uncaught Exception]: ${error.name}: ${error.message}\n${error.stack}\n`;
    try {
        fs.appendFileSync(errorLogPath, message);
    } catch (e) {
        console.error(message);
    }
    dialog.showErrorBox("Critical Startup Error", `An unexpected error occurred:\n${error.message}\n\nCheck 'startup_error.log' for details.`);
    app.quit();
});

let mainWindow;
let splashWindow;
let backendProcess;
let backendPort = null;
let handshakeToken = null;
let logStream = null;

const JAR_NAME = 'backend.jar';

function getBackendPaths() {
    let javaExe, jarPath, workingDir, appDataDir;

    if (app.isPackaged) {
        javaExe = path.join(process.resourcesPath, 'runtime', 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
        jarPath = path.join(process.resourcesPath, 'runtime', 'app', JAR_NAME);
        workingDir = path.join(process.resourcesPath, 'runtime', 'app');

        if (process.env.PORTABLE_EXECUTABLE_DIR) {
            appDataDir = process.env.PORTABLE_EXECUTABLE_DIR;
        } else {
            appDataDir = path.dirname(app.getPath('exe'));
        }
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
    const logFile = path.join(dataDir, 'backend.log');
    
    return {javaExe, jarPath, workingDir, portFile, appDataDir, logFile};
}

function createSplashWindow() {
    splashWindow = new BrowserWindow({
        width: 400,
        height: 300,
        transparent: true,
        frame: false,
        alwaysOnTop: true,
        webPreferences: {
            nodeIntegration: false,
            contextIsolation: true
        }
    });

    splashWindow.loadFile(path.join(__dirname, 'splash.html'));
    splashWindow.center();
}

function createWindow() {
    if (!backendPort) return;

    mainWindow = new BrowserWindow({
        width: 1600,
        height: 900,
        title: "Latent Library",
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

    mainWindow.webContents.setWindowOpenHandler(({ url }) => {
        shell.openExternal(url);
        return { action: 'deny' };
    });

    mainWindow.maximize();

    mainWindow.once('ready-to-show', () => {
        if (splashWindow) {
            splashWindow.close();
            splashWindow = null;
        }
        mainWindow.show();
    });

    mainWindow.loadURL(`http://127.0.0.1:${backendPort}`);

    mainWindow.on('closed', function () {
        mainWindow = null;
    });
}

function startBackend() {
    try {
        const {javaExe, jarPath, workingDir, portFile, appDataDir, logFile} = getBackendPaths();

        try {
            logStream = fs.createWriteStream(logFile, {flags: 'w'});
        } catch (e) {
            dialog.showErrorBox("Log Error", `Failed to create log file at ${logFile}:\n${e.message}`);
            return;
        }

        const log = (msg) => {
            console.log(msg);
            if (logStream) logStream.write(msg + '\n');
        };

        if (fs.existsSync(portFile)) {
            try {
                fs.unlinkSync(portFile);
            } catch (e) {
            }
        }

        log(`Starting backend with: ${javaExe} -jar ${jarPath}`);
        log(`App Data Dir: ${appDataDir}`);

        if (app.isPackaged && !fs.existsSync(javaExe)) {
            log(`CRITICAL ERROR: Java executable not found at ${javaExe}`);
            if (splashWindow) splashWindow.close();
            dialog.showErrorBox("Startup Error", `Java runtime not found.\nExpected at: ${javaExe}`);
            return;
        }

        backendProcess = spawn(javaExe, [
            '-jar', jarPath,
            `--app.data.dir=${appDataDir}`
        ], {
            cwd: workingDir,
            detached: process.platform !== 'win32'
        });

        backendProcess.stdout.on('data', (data) => {
            const msg = data.toString();
            if (logStream) logStream.write(`[Backend]: ${msg}`);
        });

        backendProcess.stderr.on('data', (data) => {
            const msg = data.toString();
            console.error(`[Backend Error]: ${msg}`);
            if (logStream) logStream.write(`[Backend Error]: ${msg}`);
        });

        backendProcess.on('error', (err) => {
            log(`Failed to start backend process: ${err.message}`);
            dialog.showErrorBox("Backend Error", `Failed to start backend process:\n${err.message}`);
        });

        backendProcess.on('close', (code) => {
            log(`Backend process exited with code ${code}`);
            if (code !== 0 && !mainWindow) {
                 if (splashWindow) splashWindow.close();
                 dialog.showErrorBox("Startup Error", `Backend exited unexpectedly with code ${code}.\nCheck ${logFile} for details.`);
            }
        });

        const pollInterval = setInterval(() => {
            if (fs.existsSync(portFile)) {
                try {
                    const content = fs.readFileSync(portFile, 'utf8').trim();
                    const parts = content.split(':');
                    if (parts.length === 2) {
                        backendPort = parseInt(parts[0]);
                        handshakeToken = parts[1];
                        log(`Backend detected on port: ${backendPort}`);
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
                log("Backend failed to start within 30s");
                clearInterval(pollInterval);
                if (splashWindow) splashWindow.close();
                dialog.showErrorBox("Timeout", "Backend failed to start within 30 seconds.\nCheck logs for details.");
            }
        }, 30000);
    } catch (e) {
        dialog.showErrorBox("Critical Error", `Failed to initialize backend:\n${e.message}`);
        if (splashWindow) splashWindow.close();
    }
}

app.on('ready', () => {
    createSplashWindow();

    ipcMain.handle('dialog:openDirectory', async () => {
        const {canceled, filePaths} = await dialog.showOpenDialog(mainWindow, {
            properties: ['openDirectory']
        });
        return canceled ? null : filePaths[0];
    });

    ipcMain.on('open-external-link', (event, url) => {
        shell.openExternal(url);
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
    if (logStream) logStream.end();

    if (backendProcess && backendPort) {
        event.preventDefault();
        const req = http.request({
            hostname: '127.0.0.1', // Use 127.0.0.1 for shutdown request too
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
                console.error("Failed to kill backend process on Windows:", e);
            }
        } else {
            try {
                process.kill(-backendProcess.pid, 'SIGTERM');
            } catch (e) {
                backendProcess.kill('SIGKILL');
            }
        }
        backendProcess = null;
    }
}
