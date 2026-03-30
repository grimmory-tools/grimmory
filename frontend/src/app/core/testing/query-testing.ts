import {signal, type EnvironmentProviders, type Provider, type WritableSignal} from '@angular/core';
import {provideHttpClient} from '@angular/common/http';
import {provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {provideTanStackQuery, QueryClient} from '@tanstack/angular-query-experimental';

interface AuthServiceStub {
  token: WritableSignal<string | null>;
  getInternalAccessToken: () => string | null;
}

export interface QueryClientHarness {
  providers: (Provider | EnvironmentProviders)[];
  queryClient: QueryClient;
}

export function createAuthServiceStub(initialToken: string | null = 'token-123'): AuthServiceStub {
  const token = signal<string | null>(initialToken);
  return {
    token,
    getInternalAccessToken: () => token(),
  };
}

export function createQueryClientHarness(): QueryClientHarness {
  const queryClient = new QueryClient();

  return {
    queryClient,
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideTanStackQuery(queryClient),
    ],
  };
}

export function flushSignalAndQueryEffects(): void {
  TestBed.flushEffects();
}
