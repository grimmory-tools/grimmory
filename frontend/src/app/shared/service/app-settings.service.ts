import {computed, effect, inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {lastValueFrom, Observable, of} from 'rxjs';
import {catchError, map, switchMap} from 'rxjs/operators';
import {API_CONFIG} from '../../core/config/api-config';
import {AppSettings, OidcProviderDetails, OidcTestResult} from '../model/app-settings.model';
import {AuthService} from './auth.service';
import {injectQuery, queryOptions, QueryClient} from '@tanstack/angular-query-experimental';
import {APP_SETTINGS_QUERY_KEY, PUBLIC_SETTINGS_QUERY_KEY} from './app-settings-query-keys';

export interface PublicAppSettings {
  oidcEnabled: boolean;
  remoteAuthEnabled: boolean;
  oidcProviderDetails: OidcProviderDetails;
  oidcForceOnlyMode: boolean;
}

@Injectable({providedIn: 'root'})
export class AppSettingsService {
  private http = inject(HttpClient);
  private queryClient = inject(QueryClient);
  private authService = inject(AuthService);
  private readonly token = this.authService.token;

  private readonly apiUrl = `${API_CONFIG.BASE_URL}/api/v1/settings`;
  private readonly publicApiUrl = `${API_CONFIG.BASE_URL}/api/v1/public-settings`;

  private publicSettingsQuery = injectQuery(() => ({
    ...this.getPublicSettingsQueryOptions(),
  }));

  private appSettingsQuery = injectQuery(() => ({
    ...this.getAppSettingsQueryOptions(),
    enabled: !!this.token(),
  }));

  appSettings = computed(() => this.appSettingsQuery.data() ?? null);
  publicAppSettings = computed(() => this.publicSettingsQuery.data() ?? null);

  constructor() {
    effect(() => {
      const token = this.token();
      if (token === null) {
        this.queryClient.removeQueries({queryKey: APP_SETTINGS_QUERY_KEY});
      }
    });

    // When authenticated settings load, keep public settings cache in sync
    effect(() => {
      const settings = this.appSettings();
      if (settings) {
        this.syncPublicSettings(settings);
      }
    });
  }

  getPublicSettingsQueryOptions() {
    return queryOptions({
      queryKey: PUBLIC_SETTINGS_QUERY_KEY,
      queryFn: () => lastValueFrom(this.http.get<PublicAppSettings>(this.publicApiUrl))
    });
  }

  private getAppSettingsQueryOptions() {
    return queryOptions({
      queryKey: APP_SETTINGS_QUERY_KEY,
      queryFn: () => lastValueFrom(this.http.get<AppSettings>(this.apiUrl))
    });
  }

  testOidcConnection(providerDetails: OidcProviderDetails): Observable<OidcTestResult> {
    return this.http.post<OidcTestResult>(`${this.apiUrl}/oidc/test`, providerDetails);
  }

  private syncPublicSettings(appSettings: AppSettings): void {
    const updatedPublicSettings: PublicAppSettings = {
      oidcEnabled: appSettings.oidcEnabled,
      remoteAuthEnabled: appSettings.remoteAuthEnabled,
      oidcProviderDetails: appSettings.oidcProviderDetails,
      oidcForceOnlyMode: appSettings.oidcForceOnlyMode
    };
    const current = this.publicAppSettings();

    if (
      !current ||
      current.oidcEnabled !== updatedPublicSettings.oidcEnabled ||
      current.remoteAuthEnabled !== updatedPublicSettings.remoteAuthEnabled ||
      current.oidcForceOnlyMode !== updatedPublicSettings.oidcForceOnlyMode ||
      JSON.stringify(current.oidcProviderDetails) !== JSON.stringify(updatedPublicSettings.oidcProviderDetails)
    ) {
      this.queryClient.setQueryData(PUBLIC_SETTINGS_QUERY_KEY, updatedPublicSettings);
    }
  }

  saveSettings(settings: { key: string; newValue: unknown }[]): Observable<void> {
    const payload = settings.map(setting => ({
      name: setting.key,
      value: setting.newValue
    }));

    return this.http.put<void>(this.apiUrl, payload).pipe(
      switchMap(() => {
        void this.queryClient.invalidateQueries({queryKey: APP_SETTINGS_QUERY_KEY});
        return of(void 0);
      }),
      map(() => void 0),
      catchError(err => {
        console.error('Error saving settings:', err);
        return of();
      })
    );
  }

  toggleOidcEnabled(enabled: boolean): Observable<void> {
    const payload = [{name: 'OIDC_ENABLED', value: enabled}];
    return this.http.put<void>(this.apiUrl, payload).pipe(
      map(() => {
        const current = this.appSettings();
        if (current) {
          const updated = {...current, oidcEnabled: enabled};
          this.queryClient.setQueryData(APP_SETTINGS_QUERY_KEY, updated);
          this.syncPublicSettings(updated);
        }
      }),
      catchError(err => {
        console.error('Error toggling OIDC:', err);
        return of();
      })
    );
  }
}
