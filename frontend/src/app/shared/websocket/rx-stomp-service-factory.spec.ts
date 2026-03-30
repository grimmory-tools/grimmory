import {Injector, runInInjectionContext} from '@angular/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {AuthService} from '../service/auth.service';
import {rxStompServiceFactory} from './rx-stomp-service-factory';
import {RxStompService} from './rx-stomp.service';

describe('rxStompServiceFactory', () => {
  const authService = {
    getInternalAccessToken: vi.fn<() => string | null>(),
  };

  beforeEach(() => {
    vi.restoreAllMocks();
    authService.getInternalAccessToken.mockReset();
  });

  it('creates an RxStompService and configures it from the auth service', () => {
    const configureSpy = vi.spyOn(RxStompService.prototype, 'configure');
    const injector = Injector.create({
      providers: [
        {provide: AuthService, useValue: authService},
      ]
    });

    const service = runInInjectionContext(
      injector,
      () => rxStompServiceFactory(authService as unknown as AuthService)
    );

    expect(service).toBeInstanceOf(RxStompService);
    expect(configureSpy).toHaveBeenCalled();

    const lastConfig = configureSpy.mock.calls.at(-1)?.[0];
    expect(lastConfig?.brokerURL).toBeDefined();
    expect(lastConfig?.beforeConnect).toBeTypeOf('function');
  });
});
