import {inject} from '@angular/core';
import {AuthService, websocketInitializer} from '../../shared/service/auth.service';
import {AppSettingsService} from '../../shared/service/app-settings.service';
import {AuthInitializationService} from './auth-initialization-service';
import {QueryClient} from '@tanstack/angular-query-experimental';

const SETTINGS_TIMEOUT_MS = 10000;

export function initializeAuthFactory() {
  return () => {
    const appSettingsService = inject(AppSettingsService);
    const authService = inject(AuthService);
    const authInitService = inject(AuthInitializationService);
    const queryClient = inject(QueryClient);

    const settingsPromise = queryClient.fetchQuery(appSettingsService.getPublicSettingsQueryOptions());
    const timeoutPromise = new Promise<null>(resolve =>
      setTimeout(() => resolve(null), SETTINGS_TIMEOUT_MS)
    );

    return Promise.race([settingsPromise, timeoutPromise]).then(publicSettings => {
      if (!publicSettings) {
        console.warn('[Auth] Public settings fetch timed out, falling back to local auth');
        authInitService.markAsInitialized();
        return;
      }

      if (publicSettings.remoteAuthEnabled) {
        return new Promise<void>(resolve => {
          authService.remoteLogin().subscribe({
            next: () => {
              authInitService.markAsInitialized();
              resolve();
            },
            error: err => {
              console.error('[Remote Login] failed:', err);
              authInitService.markAsInitialized();
              resolve();
            }
          });
        });
      } else {
        if (authService.getInternalAccessToken()) {
          websocketInitializer(authService)();
        }
        authInitService.markAsInitialized();
        return;
      }
    });
  };
}
