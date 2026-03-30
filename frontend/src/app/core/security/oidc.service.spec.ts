import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {of} from 'rxjs';

import {AppSettingsService} from '../../shared/service/app-settings.service';
import {API_CONFIG} from '../config/api-config';
import {OidcService} from './oidc.service';

describe('OidcService', () => {
  interface OidcServiceInternals {
    http: {
      get: ReturnType<typeof vi.fn>;
      post: ReturnType<typeof vi.fn>;
    };
    appSettingsService: AppSettingsService;
  }

  const createService = () => {
    const service = Object.create(OidcService.prototype) as OidcService;
    const internals = service as unknown as OidcServiceInternals;
    internals.http = {
      get: vi.fn(),
      post: vi.fn(),
    };
    internals.appSettingsService = {} as AppSettingsService;
    return {service, http: internals.http};
  };

  beforeEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('generates PKCE verifier and challenge pairs', async () => {
    const {service} = createService();
    const digestSpy = vi.fn(async (_algorithm: string, value: Uint8Array) => value);
    const getRandomValuesSpy = vi.fn((array: Uint8Array) => {
      array.set(Uint8Array.from({length: array.length}, (_, index) => index + 1));
      return array;
    });
    vi.stubGlobal('crypto', {
      getRandomValues: getRandomValuesSpy,
      subtle: {digest: digestSpy},
    });

    const result = await service.generatePkce();

    expect(getRandomValuesSpy).toHaveBeenCalledOnce();
    expect(digestSpy).toHaveBeenCalledOnce();
    expect(result.codeVerifier).toBeTruthy();
    expect(result.codeChallenge).toBeTruthy();
  });

  it('generates random URL-safe strings', () => {
    const {service} = createService();
    vi.stubGlobal('crypto', {
      getRandomValues: (array: Uint8Array) => {
        array.set(Uint8Array.from({length: array.length}, (_, index) => index + 10));
        return array;
      },
      subtle: {digest: vi.fn()},
    });

    const value = service.generateRandomString();

    expect(value).toMatch(/^[A-Za-z0-9_-]+$/);
  });

  it('builds an auth url directly when the authorization endpoint is provided', async () => {
    const {service} = createService();
    const authUrl = await service.buildAuthUrl(
      'https://issuer.example',
      'client-id',
      'challenge',
      'state-token',
      'nonce-token',
      'https://issuer.example/authorize',
      'openid profile'
    );

    expect(authUrl).toContain('https://issuer.example/authorize?');
    expect(authUrl).toContain('client_id=client-id');
    expect(authUrl).toContain('scope=openid+profile');
    expect(authUrl).toContain('state=state-token');
  });

  it('builds an auth url from the OIDC discovery document when the endpoint is omitted', async () => {
    const {service} = createService();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      json: async () => ({authorization_endpoint: 'https://issuer.example/discovered-authorize'})
    }));

    const authUrl = await service.buildAuthUrl(
      'https://issuer.example/',
      'client-id',
      'challenge',
      'state-token',
      'nonce-token'
    );

    expect(fetch).toHaveBeenCalledWith('https://issuer.example/.well-known/openid-configuration');
    expect(authUrl).toContain('https://issuer.example/discovered-authorize?');
  });

  it('throws when the discovery document does not expose an authorization endpoint', async () => {
    const {service} = createService();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      json: async () => ({issuer: 'https://issuer.example'})
    }));

    await expect(service.buildAuthUrl(
      'https://issuer.example/',
      'client-id',
      'challenge',
      'state-token',
      'nonce-token'
    )).rejects.toThrow('authorization_endpoint not found in discovery document');
  });

  it('fetches the backend-generated OIDC state token', async () => {
    const {service, http} = createService();
    http.get.mockReturnValue(of({state: 'server-state'}));

    const statePromise = service.fetchState();

    await expect(statePromise).resolves.toBe('server-state');
    expect(http.get).toHaveBeenCalledWith(`${API_CONFIG.BASE_URL}/api/v1/auth/oidc/state`);
  });

  it('posts the callback payload to exchange an OIDC code for tokens', () => {
    const {service, http} = createService();
    http.post.mockReturnValue(of({accessToken: 'access', refreshToken: 'refresh', isDefaultPassword: 'false'}));

    service.exchangeCode('code-123', 'verifier', 'nonce', 'state').subscribe();

    expect(http.post).toHaveBeenCalledWith(`${API_CONFIG.BASE_URL}/api/v1/auth/oidc/callback`, {
      code: 'code-123',
      codeVerifier: 'verifier',
      redirectUri: `${window.location.origin}/oauth2-callback`,
      nonce: 'nonce',
      state: 'state',
    });
  });

  it('stores, retrieves, and removes pkce state entries', () => {
    const {service} = createService();
    service.storePkceState({codeVerifier: 'verifier', state: 'state', nonce: 'nonce'});

    expect(service.retrievePkceState('state')).toEqual({
      codeVerifier: 'verifier',
      state: 'state',
      nonce: 'nonce',
    });
    expect(service.retrievePkceState('state')).toBeNull();
  });

  it('returns null when stored PKCE state is missing or malformed', () => {
    const {service} = createService();
    expect(service.retrievePkceState('missing')).toBeNull();

    sessionStorage.setItem('oidc_pkce_bad', '{not-json');

    expect(service.retrievePkceState('bad')).toBeNull();
  });
});
