import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../../core/config/api-config';
import {EmailProvider} from '../../email/email-provider/email-provider.model';

@Injectable({
  providedIn: 'root'
})
export class EmailV2ProviderService {
  private readonly url = `${API_CONFIG.BASE_URL}/api/v2/email/providers`;

  private http = inject(HttpClient);

  getEmailProviders(): Observable<EmailProvider[]> {
    return this.http.get<EmailProvider[]>(this.url);
  }

  createEmailProvider(provider: EmailProvider): Observable<EmailProvider> {
    return this.http.post<EmailProvider>(this.url, provider);
  }

  updateProvider(provider: EmailProvider): Observable<EmailProvider> {
    return this.http.put<EmailProvider>(`${this.url}/${provider.id}`, provider);
  }

  deleteProvider(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  setDefaultProvider(id: number): Observable<void> {
    return this.http.patch<void>(`${this.url}/${id}/set-default`, {});
  }
}
