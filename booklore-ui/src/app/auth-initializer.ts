import {inject} from '@angular/core';
import {OAuthEvent, OAuthService} from 'angular-oauth2-oidc';
import {AppSettingsService} from './core/service/app-settings.service';
import {AuthService, websocketInitializer} from './core/service/auth.service';
import {filter} from 'rxjs/operators';

export function initializeAuthFactory() {
  return () => {
    const oauthService = inject(OAuthService);
    const appSettingsService = inject(AppSettingsService);
    const authService = inject(AuthService);

    return new Promise<void>((resolve) => {
      const sub = appSettingsService.appSettings$.subscribe(settings => {
        if (settings) {
          if (settings.oidcEnabled && settings.oidcProviderDetails) {
            const details = settings.oidcProviderDetails;
            oauthService.configure({
              issuer: details.issuerUri,
              clientId: details.clientId,
              scope: 'openid profile email offline_access',
              redirectUri: window.location.origin + '/oauth2-callback',
              responseType: 'code',
              showDebugInformation: true,
              requireHttps: false,
              strictDiscoveryDocumentValidation: false,
            });

            oauthService.events
              .pipe(filter((e: OAuthEvent) =>
                e.type === 'token_received' || e.type === 'token_refreshed'
              ))
              .subscribe((e: OAuthEvent) => {
                const accessToken = oauthService.getAccessToken();
                const refreshToken = oauthService.getRefreshToken();
                authService.saveOidcTokens(accessToken, refreshToken ?? '');
              });

            oauthService.loadDiscoveryDocumentAndTryLogin().then(() => {
              oauthService.setupAutomaticSilentRefresh();
              websocketInitializer(authService);
              resolve();
            });
          } else {
            resolve();
          }
          sub.unsubscribe();
        }
      });
    });
  };
}
