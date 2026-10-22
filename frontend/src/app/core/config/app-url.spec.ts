import {afterEach, describe, expect, it, vi} from 'vitest';
import {appUrl, authRedirectUrl} from './app-url';

describe('application URLs', () => {
  afterEach(() => vi.restoreAllMocks());

  it.each(['/', '/grimmory/', '/library/books/'])('resolves browser routes under %s', path => {
    vi.spyOn(document, 'baseURI', 'get').mockReturnValue(`https://example.com${path}`);
    expect(appUrl('/oauth2-callback')).toBe(`https://example.com${path}oauth2-callback`);
    expect(authRedirectUrl('/login?reason=expired')).toBe(`https://example.com${path}login?reason=expired`);
  });

  it('keeps the frontend port for development callbacks', () => {
    vi.spyOn(document, 'baseURI', 'get').mockReturnValue('http://localhost:4200/');
    expect(appUrl('oauth2-callback')).toBe('http://localhost:4200/oauth2-callback');
  });

  it('accepts HTTP providers and rejects unsupported protocols', () => {
    vi.spyOn(document, 'baseURI', 'get').mockReturnValue('https://example.com/grimmory/');
    expect(authRedirectUrl('HTTPS://provider.example/logout')).toBe('https://provider.example/logout');
    for (const value of ['javascript:alert(1)', 'data:text/html,test', 'ftp://example.com', 'https://[']) {
      expect(authRedirectUrl(value)).toBe('https://example.com/grimmory/login');
    }
  });
});
