import {HttpErrorResponse} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import type {AuthService} from '../../shared/service/auth.service';
import {sseResponse, sseStream} from '../testing/query-testing';
import {postSseJson} from './query-transport';

interface Chunk {
  readonly id: number;
}

async function collect(events: AsyncIterable<Chunk>): Promise<Chunk[]> {
  const collected: Chunk[] = [];
  for await (const event of events) {
    collected.push(event);
  }
  return collected;
}

describe('postSseJson', () => {
  const ensureAccessToken = vi.fn((options: {forceRefresh?: boolean} = {}) =>
    of(options.forceRefresh ? 'refreshed-token' : 'token-123'));
  const auth = {ensureAccessToken} as unknown as AuthService;
  let fetchStub: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    ensureAccessToken.mockClear();
    fetchStub = vi.spyOn(globalThis, 'fetch');
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('posts the body with a bearer token and reads the streamed events', async () => {
    fetchStub.mockResolvedValue(sseResponse(sseStream(['data:{"id":1}\n\n'])));

    const events = await postSseJson<Chunk>(auth, '/stream', '{"q":1}', new AbortController().signal);

    expect(await collect(events)).toEqual([{id: 1}]);
    const [url, init] = fetchStub.mock.calls[0];
    expect(url).toBe('/stream');
    expect(init?.method).toBe('POST');
    expect(init?.body).toBe('{"q":1}');
    expect(init?.headers).toEqual({
      'Content-Type': 'application/json',
      'Authorization': 'Bearer token-123',
    });
  });

  it('refreshes the token once and retries when the request is rejected as unauthenticated', async () => {
    fetchStub
      .mockResolvedValueOnce(new Response(null, {status: 401}))
      .mockResolvedValueOnce(sseResponse(sseStream(['data:{"id":2}\n\n'])));

    const events = await postSseJson<Chunk>(auth, '/stream', '{}', new AbortController().signal);

    expect(await collect(events)).toEqual([{id: 2}]);
    expect(ensureAccessToken).toHaveBeenNthCalledWith(2, {forceRefresh: true});
    expect(fetchStub.mock.calls[1][1]?.headers).toEqual({
      'Content-Type': 'application/json',
      'Authorization': 'Bearer refreshed-token',
    });
  });

  it('fails with the http status when the retried request is still rejected', async () => {
    fetchStub.mockResolvedValue(new Response(null, {status: 401, statusText: 'Unauthorized'}));

    await expect(postSseJson(auth, '/stream', '{}', new AbortController().signal))
      .rejects.toMatchObject({status: 401, statusText: 'Unauthorized'});
    expect(fetchStub).toHaveBeenCalledTimes(2);
  });

  it('fails with the http status rather than a bare message', async () => {
    fetchStub.mockResolvedValue(new Response(null, {status: 503, statusText: 'Unavailable'}));

    const failure = await postSseJson(auth, '/stream', '{}', new AbortController().signal).catch((error: unknown) => error);

    expect(failure).toBeInstanceOf(HttpErrorResponse);
    expect(failure).toMatchObject({status: 503, url: '/stream'});
    expect(fetchStub).toHaveBeenCalledOnce();
  });

  it('passes the abort signal to the request', async () => {
    const controller = new AbortController();
    fetchStub.mockImplementation((_url: unknown, init?: RequestInit) => {
      controller.abort();
      return init?.signal?.aborted
        ? Promise.reject(new DOMException('Aborted', 'AbortError'))
        : Promise.resolve(sseResponse(sseStream(['data:{"id":3}\n\n'])));
    });

    await expect(postSseJson(auth, '/stream', '{}', controller.signal)).rejects.toThrow('Aborted');
  });

  it('fails when the token cannot be obtained', async () => {
    ensureAccessToken.mockReturnValueOnce(throwError(() => new Error('No refresh token available')));

    await expect(postSseJson(auth, '/stream', '{}', new AbortController().signal))
      .rejects.toThrow('No refresh token available');
    expect(fetchStub).not.toHaveBeenCalled();
  });
});
