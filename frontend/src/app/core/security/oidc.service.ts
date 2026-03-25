import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom, Observable} from 'rxjs';
import {API_CONFIG} from '../config/api-config';
import {AppSettingsService, PublicAppSettings} from '../../shared/service/app-settings.service';
import {filter, take} from 'rxjs/operators';

interface OidcPkceState {
  codeVerifier: string;
  state: string;
  nonce: string;
}

interface OidcTokenResponse {
  accessToken: string;
  refreshToken: string;
  isDefaultPassword: string;
}

@Injectable({providedIn: 'root'})
export class OidcService {

  private http = inject(HttpClient);
  private appSettingsService = inject(AppSettingsService);

  async generatePkce(): Promise<{codeVerifier: string; codeChallenge: string}> {
    const array = new Uint8Array(32);
    crypto.getRandomValues(array);
    const codeVerifier = this.base64UrlEncode(array);

    const encoder = new TextEncoder();
    const hash = await crypto.subtle.digest('SHA-256', encoder.encode(codeVerifier));
    const codeChallenge = this.base64UrlEncode(new Uint8Array(hash));

    return {codeVerifier, codeChallenge};
  }

  generateRandomString(): string {
    const array = new Uint8Array(32);
    crypto.getRandomValues(array);
    return this.base64UrlEncode(array);
  }

  buildAuthUrl(
    issuerUri: string,
    clientId: string,
    codeChallenge: string,
    state: string,
    nonce: string,
    authorizationEndpoint?: string,
    scopes?: string
  ): Promise<string> {
    const redirectUri = `${window.location.origin}/oauth2-callback`;
    const scope = scopes?.trim() || 'openid profile email groups offline_access';

    if (authorizationEndpoint) {
      return Promise.resolve(this.buildUrl(authorizationEndpoint, clientId, redirectUri, scope, codeChallenge, state, nonce));
    }

    // Fetch from discovery if authorization_endpoint not provided
    return fetch(`${issuerUri.replace(/\/+$/, '')}/.well-known/openid-configuration`)
      .then(res => res.json())
      .then(doc => {
        const endpoint = doc.authorization_endpoint;
        if (!endpoint) {
          throw new Error('authorization_endpoint not found in discovery document');
        }
        return this.buildUrl(endpoint, clientId, redirectUri, scope, codeChallenge, state, nonce);
      });
  }

  async fetchState(): Promise<string> {
    const response = await firstValueFrom(
      this.http.get<{state: string}>(`${API_CONFIG.BASE_URL}/api/v1/auth/oidc/state`)
    );
    return response.state;
  }

  exchangeCode(code: string, codeVerifier: string, nonce: string, state: string): Observable<OidcTokenResponse> {
    const redirectUri = `${window.location.origin}/oauth2-callback`;
    return this.http.post<OidcTokenResponse>(`${API_CONFIG.BASE_URL}/api/v1/auth/oidc/callback`, {
      code,
      codeVerifier,
      redirectUri,
      nonce,
      state
    });
  }

  storePkceState(pkceState: OidcPkceState): void {
    sessionStorage.setItem(`oidc_pkce_${pkceState.state}`, JSON.stringify(pkceState));
  }

  retrievePkceState(state: string): OidcPkceState | null {
    const key = `oidc_pkce_${state}`;
    const stored = sessionStorage.getItem(key);
    sessionStorage.removeItem(key);
    if (!stored) return null;
    try {
      return JSON.parse(stored);
    } catch {
      return null;
    }
  }

  private buildUrl(
    endpoint: string,
    clientId: string,
    redirectUri: string,
    scope: string,
    codeChallenge: string,
    state: string,
    nonce: string
  ): string {
    const params = new URLSearchParams({
      response_type: 'code',
      client_id: clientId,
      redirect_uri: redirectUri,
      scope,
      code_challenge: codeChallenge,
      code_challenge_method: 'S256',
      state,
      nonce,
    });
    return `${endpoint}?${params.toString()}`;
  }

  private base64UrlEncode(bytes: Uint8Array): string {
    let binary = '';
    for (const byte of bytes) {
      binary += String.fromCharCode(byte);
    }
    return btoa(binary)
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=/g, '');
  }
}
