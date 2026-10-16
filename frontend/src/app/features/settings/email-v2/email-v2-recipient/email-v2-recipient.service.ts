import { inject, Service } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_CONFIG } from '../../../../core/config/api-config';
import {EmailRecipient} from '../email-recipient.model';

@Service()
export class EmailV2RecipientService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/email/recipients`;

  getRecipients(): Observable<EmailRecipient[]> {
    return this.http.get<EmailRecipient[]>(this.url);
  }

  createRecipient(recipient: EmailRecipient): Observable<EmailRecipient> {
    return this.http.post<EmailRecipient>(this.url, recipient);
  }

  updateRecipient(recipient: EmailRecipient): Observable<EmailRecipient> {
    return this.http.put<EmailRecipient>(`${this.url}/${recipient.id}`, recipient);
  }

  deleteRecipient(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  setDefaultRecipient(id: number): Observable<void> {
    return this.http.patch<void>(`${this.url}/${id}/set-default`, {});
  }
}
