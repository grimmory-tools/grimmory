import {RxStompConfig} from '@stomp/rx-stomp';
import {AuthService} from '../../core/service/auth.service';
import {API_CONFIG} from '../../config/api-config';

export function createRxStompConfig(authService: AuthService): RxStompConfig {
  return {
    brokerURL: API_CONFIG.BROKER_URL,
    heartbeatIncoming: 0,
    heartbeatOutgoing: 20000,
    reconnectDelay: 200,
    beforeConnect: (stomp) => {
      const oidcToken = authService.getOidcAccessToken();
      const internalToken = authService.getInternalAccessToken();
      const token = oidcToken || internalToken;
      if (token) {
        stomp.stompClient.connectHeaders = {
          'Authorization': `Bearer ${token}`,
        };
      }
    },
    debug: (msg: string): void => {
      // console.log(new Date(), msg);
    },
  };
}
