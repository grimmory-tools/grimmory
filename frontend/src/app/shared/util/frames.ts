export function runOnNextTwoFrames(callback: () => void): void {
  queueMicrotask(() => {
    requestAnimationFrame(() => {
      callback();
      requestAnimationFrame(callback);
    });
  });
}
