import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {of, Subject} from 'rxjs';
import {IMessage} from '@stomp/rx-stomp';
import {provideTanStackQuery, QueryClient} from '@tanstack/angular-query-experimental';

import {LibraryHealthService} from './library-health.service';
import {RxStompService} from '../../../shared/websocket/rx-stomp.service';

describe('LibraryHealthService', () => {
  let service: LibraryHealthService;
  let queryClient: QueryClient;
  let httpGetSpy: ReturnType<typeof vi.fn>;
  let wsSubject: Subject<IMessage>;

  beforeEach(() => {
    httpGetSpy = vi.fn().mockReturnValue(of({}));
    wsSubject = new Subject<IMessage>();
    queryClient = new QueryClient();

    TestBed.configureTestingModule({
      providers: [
        provideTanStackQuery(queryClient),
        LibraryHealthService,
        {provide: HttpClient, useValue: {get: httpGetSpy}},
        {provide: RxStompService, useValue: {watch: vi.fn(() => wsSubject.asObservable())}},
      ]
    });

    service = TestBed.inject(LibraryHealthService);
  });

  async function fetchHealth(): Promise<void> {
    const fetchQuerySpy = vi.spyOn(queryClient, 'fetchQuery');
    service.fetchHealth();
    await fetchQuerySpy.mock.results[0]?.value;
  }

  it('fetches health', async () => {
    httpGetSpy.mockReturnValue(of({1: true, 2: false}));

    await fetchHealth();

    expect(httpGetSpy).toHaveBeenCalledOnce();
    expect(service.health()).toEqual({1: true, 2: false});
  });

  it('reports unhealthy for a library with false health', async () => {
    httpGetSpy.mockReturnValue(of({1: true, 2: false}));

    await fetchHealth();

    expect(service.isUnhealthy(2)).toBe(true);
  });

  it('reports healthy for a library with true health', async () => {
    httpGetSpy.mockReturnValue(of({1: true}));

    await fetchHealth();

    expect(service.isUnhealthy(1)).toBe(false);
  });

  it('reports false for an unknown library', async () => {
    httpGetSpy.mockReturnValue(of({}));

    await fetchHealth();

    expect(service.isUnhealthy(99)).toBe(false);
  });

  it('updates state from websocket messages', async () => {
    service.initWebsocket();
    wsSubject.next({body: JSON.stringify({libraryHealth: {1: false}})} as IMessage);

    expect(service.isUnhealthy(1)).toBe(true);
    expect(service.health()).toEqual({1: false});
  });
});
