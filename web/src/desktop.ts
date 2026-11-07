declare global {
  interface Window {
    pyteDesktop?: { setPlaying(playing: boolean): void };
  }
}
export {};
