const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('jarvisDesktop', {
  toggleFullscreen: () => ipcRenderer.invoke('window:toggleFullscreen')
});
