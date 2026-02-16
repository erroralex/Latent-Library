/**
 * @file preload.js (Electron)
 * @description The preload script that bridges the gap between the Electron main process and the renderer process.
 *
 * This script runs in a privileged context before the web page is loaded. it uses the
 * {@code contextBridge} to safely expose specific Electron APIs to the frontend without
 * enabling full {@code nodeIntegration}. This ensures a secure communication channel
 * for native OS features and application-specific data.
 *
 * Key Responsibilities:
 * - **Security Handshake:** Extracts the unique security token passed from the main
 *   process and exposes it to the frontend for API authentication.
 * - **Native Dialogs:** Provides a wrapper for the system folder selection dialog.
 * - **Window Controls:** Exposes methods for the custom title bar to minimize,
 *   maximize, and close the application window.
 * - **External Links:** Allows opening URLs in the user's default browser via IPC.
 */
const {contextBridge, ipcRenderer} = require('electron');

const tokenArg = process.argv.find(arg => arg.startsWith('--handshake-token='));
const handshakeToken = tokenArg ? tokenArg.split('=')[1] : null;

contextBridge.exposeInMainWorld('electronAPI', {
    selectFolder: () => ipcRenderer.invoke('dialog:openDirectory'),
    openExternal: (url) => ipcRenderer.send('open-external-link', url),
    getHandshakeToken: () => handshakeToken
});

contextBridge.exposeInMainWorld('windowAPI', {
    minimize: () => ipcRenderer.send('window-minimize'),
    maximize: () => ipcRenderer.send('window-maximize'),
    close: () => ipcRenderer.send('window-close')
});
