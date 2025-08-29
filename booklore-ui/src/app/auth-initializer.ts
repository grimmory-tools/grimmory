import {inject} from '@angular/core';
import {OAuthService} from 'angular-oauth2-oidc';
import {AuthService, websocketInitializer} from './core/service/auth.service';
import {AppSettingsService} from './core/service/app-settings.service';
import {AuthInitializationService} from './auth-initialization-service';

const OIDC_BYPASS_KEY = 'booklore-oidc-bypass';
const OIDC_ERROR_COUNT_KEY = 'booklore-oidc-error-count';
const MAX_OIDC_RETRIES = 3;
const OIDC_TIMEOUT_MS = 5000;

function withTimeout<T>(promise: Promise<T>, timeoutMs: number): Promise<T> {
  return Promise.race([
    promise,
    new Promise<T>((_, reject) =>
      setTimeout(() => reject(new Error(`Operation timed out after ${timeoutMs}ms`)), timeoutMs)
    )
  ]);
}

export function initializeAuthFactory() {
  return () => {
    const oauthService = inject(OAuthService);
    const appSettingsService = inject(AppSettingsService);
    const authService = inject(AuthService);
    const authInitService = inject(AuthInitializationService);

    return new Promise<void>((resolve) => {
      const sub = appSettingsService.publicAppSettings$.subscribe(publicSettings => {
        if (publicSettings) {
          const forceLocalOnly = new URLSearchParams(window.location.search).get('localOnly') === 'true';
          const oidcBypassed = localStorage.getItem(OIDC_BYPASS_KEY) === 'true';
          const errorCount = parseInt(localStorage.getItem(OIDC_ERROR_COUNT_KEY) || '0', 10);

          if (!forceLocalOnly &&
            publicSettings.oidcEnabled &&
            publicSettings.oidcProviderDetails &&
            !oidcBypassed &&
            errorCount < MAX_OIDC_RETRIES) {
            const details = publicSettings.oidcProviderDetails;

            oauthService.configure({
              issuer: details.issuerUri,
              clientId: details.clientId,
              scope: 'openid profile email offline_access',
              redirectUri: window.location.origin + '/oauth2-callback',
              responseType: 'code',
              showDebugInformation: false,
              requireHttps: false,
              strictDiscoveryDocumentValidation: false,
            });

            console.log(`[OIDC] Attempting discovery and login (attempt ${errorCount + 1}/${MAX_OIDC_RETRIES})`);

            withTimeout(oauthService.loadDiscoveryDocumentAndTryLogin(), OIDC_TIMEOUT_MS)
              .then(() => {
                console.log('[OIDC] Discovery document loaded and login attempted');
                localStorage.removeItem(OIDC_ERROR_COUNT_KEY);

                if (oauthService.hasValidAccessToken()) {
                  authService.tokenSubject.next(oauthService.getAccessToken());
                  console.log('[OIDC] Valid access token found after tryLogin');
                  oauthService.setupAutomaticSilentRefresh();
                  websocketInitializer(authService)();
                  authInitService.markAsInitialized();
                  resolve();
                } else {
                  console.log('[OIDC] No valid access token found, attempting silent login with prompt=none');
                  oauthService.initImplicitFlow();
                  resolve();
                }
              })
              .catch(err => {
                const newErrorCount = errorCount + 1;
                localStorage.setItem(OIDC_ERROR_COUNT_KEY, newErrorCount.toString());

                authInitService.markAsInitialized();

                const isTimeout = err.message.includes('timed out');
                const errorType = isTimeout ? 'timeout' : 'network/configuration';

                console.error(
                  `OIDC initialization failed (${errorType}, attempt ${newErrorCount}/${MAX_OIDC_RETRIES}): ` +
                  'Unable to complete OpenID Connect discovery or login. ' +
                  `${isTimeout ? 'The request timed out after ' + OIDC_TIMEOUT_MS + 'ms. ' : ''}` +
                  'This may be due to an incorrect issuer URL, client ID, network issue, or slow server response. ' +
                  'Falling back to local login. Details:', err
                );

                if (newErrorCount >= MAX_OIDC_RETRIES) {
                  console.warn(`[OIDC] Maximum retry attempts (${MAX_OIDC_RETRIES}) exceeded. OIDC will be automatically bypassed until manually re-enabled.`);
                  localStorage.setItem(OIDC_BYPASS_KEY, 'true');
                }

                resolve();
              });
          } else {
            if (forceLocalOnly) {
              console.warn('[OIDC] Forced local-only login via ?localOnly=true');
            } else if (oidcBypassed) {
              console.log('[OIDC] OIDC is manually bypassed, using local authentication only');
            } else if (errorCount >= MAX_OIDC_RETRIES) {
              console.log(`[OIDC] OIDC automatically bypassed due to ${errorCount} consecutive errors`);
            }
            authInitService.markAsInitialized();
            resolve();
          }
          sub.unsubscribe();
        }
      });
    });
  };
}

export function resetOidcBypass(): void {
  localStorage.removeItem(OIDC_BYPASS_KEY);
  localStorage.removeItem(OIDC_ERROR_COUNT_KEY);
}

export function isOidcBypassed(): boolean {
  return localStorage.getItem(OIDC_BYPASS_KEY) === 'true';
}

export function getOidcErrorCount(): number {
  return parseInt(localStorage.getItem(OIDC_ERROR_COUNT_KEY) || '0', 10);
}
