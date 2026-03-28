import {inject, Injectable} from '@angular/core';
import {RxStomp, RxStompConfig} from '@stomp/rx-stomp';
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
  }

  public updateConfig(config: RxStompConfig) {
    this.configure(config);
  }
}
