'use strict';

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('pyteDesktop', {
  setPlaying(playing) {
    ipcRenderer.send('pyte:playing', Boolean(playing));
  },
});
