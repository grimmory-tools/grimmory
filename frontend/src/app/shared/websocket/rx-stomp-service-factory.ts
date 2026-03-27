import { RxStompService } from './rx-stomp.service';
import { createRxStompConfig } from './rx-stomp.config';
import {AuthService} from '../service/auth.service';

export function rxStompServiceFactory(authService: AuthService) {
  void authService;
  const rxStomp = new RxStompService();
  const stompConfig = createRxStompConfig(authService);
  rxStomp.configure(stompConfig);
  return rxStomp;
}
