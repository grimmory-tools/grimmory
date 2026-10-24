import {inject, Service} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {API_CONFIG} from '../../core/config/api-config';

export interface LcpSettingsStatus {
  hasDefaultPassphrase: boolean;
}

@Service()
export class LcpSettingsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/users/me/lcp-settings`;

  getStatus() {
    return this.http.get<LcpSettingsStatus>(this.url);
  }

  save(passphrase: string) {
    return this.http.put<void>(this.url, {passphrase});
  }

  clear() {
    return this.http.delete<void>(this.url);
  }
}
