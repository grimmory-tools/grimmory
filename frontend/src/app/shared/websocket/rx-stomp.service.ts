import { inject, Injectable } from '@angular/core';
import { RxStomp, RxStompConfig } from '@stomp/rx-stomp';
import { AuthService } from '../service/auth.service';
import { createRxStompConfig } from './rx-stomp.config';

@Injectable({
  providedIn: 'root',
})
export class RxStompService extends RxStomp {
  private authService = inject(AuthService);
  private deactivatedForBfCache = false;

  constructor() {
    super();
    const stompConfig = createRxStompConfig(this.authService);
    this.configure(stompConfig);
    this.setupBfCacheListeners();
  }

  public updateConfig(config: RxStompConfig) {
    this.configure(config);
  }

  private setupBfCacheListeners(): void {
    if (typeof globalThis.window === 'undefined') return;

    globalThis.window.addEventListener('pagehide', () => {
      if (this.active) {
        this.deactivatedForBfCache = true;
        this.deactivate();
      }
    });

    globalThis.window.addEventListener('freeze', () => {
      if (this.active) {
        this.deactivatedForBfCache = true;
        this.deactivate();
      }
    });

    globalThis.window.addEventListener('resume', () => {
      if (this.deactivatedForBfCache && !this.active && this.authService.isAuthenticated()) {
        this.deactivatedForBfCache = false;
        this.activate();
      }
    });

    globalThis.window.addEventListener('pageshow', (event) => {
      if (event.persisted && this.deactivatedForBfCache && !this.active && this.authService.isAuthenticated()) {
        this.deactivatedForBfCache = false;
        this.activate();
      }
    });
  }
}
