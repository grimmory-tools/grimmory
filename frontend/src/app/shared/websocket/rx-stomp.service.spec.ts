import {TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {RxStompConfig} from '@stomp/rx-stomp';

import {AuthService} from '../service/auth.service';
import {RxStompService} from './rx-stomp.service';

describe('RxStompService', () => {
  const authService = {
    getInternalAccessToken: vi.fn<() => string | null>(),
  };

  beforeEach(() => {
    vi.restoreAllMocks();
    authService.getInternalAccessToken.mockReset();

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        RxStompService,
        {provide: AuthService, useValue: authService},
      ]
    });
  });

  it('configures the stomp client when it is created', () => {
    const configureSpy = vi.spyOn(RxStompService.prototype, 'configure');
    const service = TestBed.inject(RxStompService);

    expect(service).toBeInstanceOf(RxStompService);
    expect(configureSpy).toHaveBeenCalled();
    const initialConfig = configureSpy.mock.calls[0]?.[0];
    expect(initialConfig?.beforeConnect).toBeTypeOf('function');
  });

  it('delegates updateConfig to configure', () => {
    const service = TestBed.inject(RxStompService);
    const configureSpy = vi.spyOn(service, 'configure');
    const config = {brokerURL: 'ws://example.test'} as RxStompConfig;

    service.updateConfig(config);

    expect(configureSpy).toHaveBeenCalledWith(config);
  });
});
