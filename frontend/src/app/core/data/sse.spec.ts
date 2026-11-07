import {describe, expect, it} from 'vitest';

import {sseResponse, sseStream} from '../testing/query-testing';
import {readSseJson} from './sse';

interface Chunk {
  readonly id: number;
}

async function collect(chunks: readonly string[]): Promise<Chunk[]> {
  const events: Chunk[] = [];
  for await (const event of readSseJson<Chunk>(sseResponse(sseStream(chunks)))) {
    events.push(event);
  }
  return events;
}

describe('readSseJson', () => {
  it('reads one event per chunk', async () => {
    expect(await collect(['data:{"id":1}\n\n', 'data:{"id":2}\n\n'])).toEqual([{id: 1}, {id: 2}]);
  });

  it('reads two events arriving in one chunk', async () => {
    expect(await collect(['data:{"id":1}\n\ndata:{"id":2}\n\n'])).toEqual([{id: 1}, {id: 2}]);
  });

  it('reads one event split across chunks', async () => {
    expect(await collect(['data:{"id', '":1}\n\n'])).toEqual([{id: 1}]);
  });

  it('reads a trailing event that has no final newline', async () => {
    expect(await collect(['data:{"id":1}\n\n', 'data:{"id":2}'])).toEqual([{id: 1}, {id: 2}]);
  });

  it('skips an empty data line', async () => {
    expect(await collect(['data:\n\n', 'data:{"id":1}\n\n'])).toEqual([{id: 1}]);
  });

  it('throws when a payload is not JSON', async () => {
    await expect(collect(['data:not-json\n\n'])).rejects.toThrow(SyntaxError);
  });

  it('throws when the response has no body', async () => {
    const events = readSseJson<Chunk>(new Response(null, {status: 204}));
    await expect(events[Symbol.asyncIterator]().next()).rejects.toThrow('Response body is null');
  });
});
