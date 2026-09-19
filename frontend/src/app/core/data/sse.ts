async function* readLines(reader: ReadableStreamDefaultReader<Uint8Array>): AsyncIterable<string> {
  const decoder = new TextDecoder();
  let buffer = '';

  try {
    for (;;) {
      const {done, value} = await reader.read();
      if (done) {
        break;
      }

      buffer += decoder.decode(value, {stream: true});
      const lines = buffer.split('\n');
      buffer = lines.pop() ?? '';
      yield* lines;
    }

    yield buffer;
  } finally {
    reader.releaseLock();
  }
}

export async function* readSseJson<T>(response: Response): AsyncIterable<T> {
  const reader = response.body?.getReader();
  if (!reader) {
    throw new Error('Response body is null');
  }

  for await (const line of readLines(reader)) {
    if (!line.startsWith('data:')) {
      continue;
    }

    const payload = line.slice(5).trim();
    if (payload !== '') {
      const parsed: T = JSON.parse(payload);
      yield parsed;
    }
  }
}
