import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {describe, expect, it, vi} from 'vitest';
import {QueryClient, queryOptions} from '@tanstack/angular-query-experimental';

import {AuthService} from './auth.service';
import {StartupService} from './startup.service';
import {UserService} from '../../features/settings/user-management/user.service';

describe('StartupService', () => {
  it('loads the current user when a token is present', async () => {
    const fetchQuery = vi.fn().mockResolvedValue({username: 'admin'});

    TestBed.configureTestingModule({
      providers: [
        StartupService,
        {provide: AuthService, useValue: {token: signal('token')}},
        {
          provide: UserService,
          useValue: {
            getUserQueryOptions: () => queryOptions({
              queryKey: ['user'],
              queryFn: async () => ({username: 'admin'}),
            }),
          },
        },
        {provide: QueryClient, useValue: {fetchQuery}},
      ]
    });

    const service = TestBed.inject(StartupService);

    await expect(service.load()).resolves.toBeUndefined();
    expect(fetchQuery).toHaveBeenCalledOnce();
  });

  it('resolves immediately when there is no token', async () => {
    const fetchQuery = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        StartupService,
        {provide: AuthService, useValue: {token: signal(null)}},
        {
          provide: UserService,
          useValue: {
            getUserQueryOptions: () => queryOptions({
              queryKey: ['user'],
              queryFn: async () => ({username: 'admin'}),
            }),
          },
        },
        {provide: QueryClient, useValue: {fetchQuery}},
      ]
    });

    const service = TestBed.inject(StartupService);

    await expect(service.load()).resolves.toBeUndefined();
    expect(fetchQuery).not.toHaveBeenCalled();
  });
});
