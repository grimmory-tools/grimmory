import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../core/config/api-config';
import {BookMetadata} from '../../book/model/book.model';
import {MetadataProvider} from '../model/metadata-provider.model';
import {HttpClient} from '@angular/common/http';

@Injectable({providedIn: 'root'})
export class MetadataProviderService {
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/metadata/providers`;
  private http = inject(HttpClient);

  fetchMetadataProviders(): Observable<MetadataProvider[]> {
    return this.http.get<MetadataProvider[]>(`${this.url}`);
  }

  fetchMetadataDetail(provider: string, providerItemId: string): Observable<BookMetadata> {
    return this.http.get<BookMetadata>(`${this.url}/${provider}/fetch/${providerItemId}`);
  }
}
