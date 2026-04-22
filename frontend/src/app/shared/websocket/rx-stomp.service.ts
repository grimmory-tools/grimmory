import { inject, Injectable } from '@angular/core';
import { RxStomp, RxStompConfig } from '@stomp/rx-stomp';
import { AuthService } from '../service/auth.service';
import { createRxStompConfig } from './rx-stomp.config';

@Injectable({
  providedIn: 'root',
})
export class RxStompService extends RxStomp {
  private authService = inject(AuthService);

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
    if (typeof window === 'undefined') return;

    window.addEventListener('pagehide', () => {
      if (this.active) {
        this.deactivate();
      }
    });

    window.addEventListener('freeze', () => {
      if (this.active) {
        this.deactivate();
      }
    });

    window.addEventListener('resume', () => {
      if (!this.active && this.authService.isAuthenticated()) {
        this.activate();
      }
    });

    window.addEventListener('pageshow', (event) => {
      if (event.persisted && this.authService.isAuthenticated()) {
        this.activate();
      }
    });
  }
}
