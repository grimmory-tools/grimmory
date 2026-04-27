import {describe, expect, it} from 'vitest';
import {NgswBypassPipe} from './ngsw-bypass.pipe';

describe('NgswBypassPipe', () => {
  const pipe = new NgswBypassPipe();

  it('returns null for null input', () => {
    expect(pipe.transform(null)).toBeNull();
  });

  it('returns null for undefined input', () => {
    expect(pipe.transform(undefined)).toBeNull();
  });

  it('returns null for empty string', () => {
    expect(pipe.transform('')).toBeNull();
  });

  it('appends with ? when URL has no query string', () => {
    expect(pipe.transform('https://cdn.example.com/cover.jpg'))
      .toBe('https://cdn.example.com/cover.jpg?ngsw-bypass=true');
  });

  it('appends with & when URL already has a query string', () => {
    expect(pipe.transform('https://cdn.example.com/cover.jpg?w=200'))
      .toBe('https://cdn.example.com/cover.jpg?w=200&ngsw-bypass=true');
  });
});
