import {ApplicationRef, provideZonelessChangeDetection, signal, type EnvironmentProviders, type Provider, type WritableSignal} from '@angular/core';
import {provideHttpClient} from '@angular/common/http';
import {provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {provideTanStackQuery, QueryClient, QueryObserver} from '@tanstack/angular-query-experimental';

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
      provideZonelessChangeDetection(),
      provideHttpClient(),
      provideHttpClientTesting(),
      provideTanStackQuery(queryClient),
    ],
  };
}

export function flushSignalAndQueryEffects(): void {
  TestBed.flushEffects();
}

export function observeActiveQuery(queryClient: QueryClient, queryKey: readonly unknown[], data: unknown) {
  let fetchCount = 0;
  let abortCount = 0;
  const pendingResolutions: (() => void)[] = [];

  queryClient.setQueryData(queryKey, data);
  const observer = new QueryObserver(queryClient, {
    queryKey,
    staleTime: Infinity,
    queryFn: ({signal}) => new Promise(resolve => {
      fetchCount += 1;
      signal.addEventListener('abort', () => {
        abortCount += 1;
      });
      pendingResolutions.push(() => resolve(data));
    }),
  });
  const unsubscribe = observer.subscribe(() => undefined);

  return {
    fetchCount: () => fetchCount,
    abortCount: () => abortCount,
    finish: () => {
      pendingResolutions.splice(0).forEach(resolve => resolve());
      unsubscribe();
    },
  };
}

/**
 * Asynchronously flushes Angular effects and query state across multiple rounds.
 * Use this for tests involving async operations like HTTP requests or timers.
 * For synchronous signal/effect flushing, use `flushSignalAndQueryEffects()` instead.
 *
 * @param rounds Number of cycles to await macrotask and microtask resolutions. Defaults to 5.
 */
export async function flushQueryAsync(rounds = 5): Promise<void> {
  const appRef = TestBed.inject(ApplicationRef);
  for (let i = 0; i < rounds; i++) {
    TestBed.flushEffects();
    appRef.tick();
    await Promise.resolve();
    await new Promise(resolve => setTimeout(resolve, 0));
  }
  TestBed.flushEffects();
  appRef.tick();
}
