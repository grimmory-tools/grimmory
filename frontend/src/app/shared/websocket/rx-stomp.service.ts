import {inject, Service} from '@angular/core';
import {RxStomp, RxStompConfig} from '@stomp/rx-stomp';
import { AuthService } from '../service/auth.service';
import { createRxStompConfig } from './rx-stomp.config';

@Service()
export class RxStompService extends RxStomp {
  private readonly authService = inject(AuthService);

  constructor() {
    super();
    const stompConfig = createRxStompConfig(this.authService);
    this.configure(stompConfig);
  }

  public updateConfig(config: RxStompConfig) {
    this.configure(config);
  }
}
