import {beforeEach, describe, expect, it, vi} from 'vitest';

import {API_CONFIG} from '../../core/config/api-config';
import {AuthService} from '../service/auth.service';
import {createRxStompConfig} from './rx-stomp.config';

type BeforeConnectTarget = Parameters<NonNullable<ReturnType<typeof createRxStompConfig>['beforeConnect']>>[0];

describe('createRxStompConfig', () => {
  const authService = {
    getInternalAccessToken: vi.fn<() => string | null>(),
  };

  beforeEach(() => {
    vi.restoreAllMocks();
    authService.getInternalAccessToken.mockReset();
  });

  it('returns the expected connection defaults', () => {
    const config = createRxStompConfig(authService as unknown as AuthService);

    expect(config.brokerURL).toBe(API_CONFIG.BROKER_URL);
    expect(config.heartbeatIncoming).toBe(0);
    expect(config.heartbeatOutgoing).toBe(20000);
    expect(config.reconnectDelay).toBe(10000);
  });

  it('injects the bearer token into connect headers when one is available', () => {
    authService.getInternalAccessToken.mockReturnValue('access-token');
    const config = createRxStompConfig(authService as unknown as AuthService);
    const stomp = {
      stompClient: {
        connectHeaders: {},
      }
    };

    config.beforeConnect?.(stomp as unknown as BeforeConnectTarget);

    expect(stomp.stompClient.connectHeaders).toEqual({
      Authorization: 'Bearer access-token',
    });
  });

  it('does not add authorization headers when there is no access token', () => {
    authService.getInternalAccessToken.mockReturnValue(null);
    const config = createRxStompConfig(authService as unknown as AuthService);
    const stomp = {
      stompClient: {
        connectHeaders: {},
      }
    };

    config.beforeConnect?.(stomp as unknown as BeforeConnectTarget);

    expect(stomp.stompClient.connectHeaders).toEqual({});
  });
});
