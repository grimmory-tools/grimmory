import {Injectable, OnDestroy} from '@angular/core';

@Injectable({providedIn: 'root'})
export class WakeLockService implements OnDestroy {
  private wakeLock: WakeLockSentinel | null = null;
  private enabled = false;

  async enable(): Promise<void> {
    if (this.enabled) return;
    this.enabled = true;

    document.addEventListener('visibilitychange', this.onVisibilityChange);
    await this.requestWakeLock();
  }

  async disable(): Promise<void> {
    this.enabled = false;
    document.removeEventListener('visibilitychange', this.onVisibilityChange);
    await this.releaseWakeLock();
  }

  ngOnDestroy(): void {
    this.disable();
  }

  private async requestWakeLock(): Promise<void> {
    if (!('wakeLock' in navigator)) return;
    try {
      this.wakeLock = await navigator.wakeLock.request('screen');
      this.wakeLock.addEventListener('release', () => {
        this.wakeLock = null;
      });
    } catch {
      // Wake lock request failed (e.g., page not visible)
    }
  }

  private async releaseWakeLock(): Promise<void> {
    if (this.wakeLock) {
      await this.wakeLock.release();
      this.wakeLock = null;
    }
  }

  private onVisibilityChange = async (): Promise<void> => {
    if (this.enabled && document.visibilityState === 'visible') {
      await this.requestWakeLock();
    }
  };
}
