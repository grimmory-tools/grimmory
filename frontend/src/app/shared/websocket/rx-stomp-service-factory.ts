import { RxStompService } from './rx-stomp.service';
import { createRxStompConfig } from './rx-stomp.config';
import {AuthService} from '../service/auth.service';

export function rxStompServiceFactory(authService: AuthService) {
  const rxStomp = new RxStompService(authService);
  const stompConfig = createRxStompConfig(authService);
  rxStomp.configure(stompConfig);
  return rxStomp;
}
