import {describe, expect, it} from 'vitest';

import {parseLogNotification, Severity} from './log-notification.model';

describe('log-notification.model', () => {
  it('parses message bodies with timestamp and severity', () => {
    const parsed = parseLogNotification(JSON.stringify({
      timestamp: '2026-03-26T12:34:56.000Z',
      message: 'Library scan completed',
      severity: 'WARN'
    }));

    expect(parsed.message).toBe('Library scan completed');
    expect(parsed.severity).toBe(Severity.WARN);
    expect(parsed.timestamp).toMatch(/\d{1,2}:\d{2}:\d{2}/);
  });

  it('leaves optional fields undefined when they are not provided', () => {
    expect(parseLogNotification(JSON.stringify({message: 'hello'}))).toEqual({
      message: 'hello',
      severity: undefined,
      timestamp: undefined
    });
  });

  it('throws on invalid JSON payloads', () => {
    expect(() => parseLogNotification('not-json')).toThrow(SyntaxError);
  });
});
