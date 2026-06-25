import {computed, inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {injectQuery} from '@tanstack/angular-query-experimental';
import {lastValueFrom, Observable} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {AuthService} from '../../../shared/service/auth.service';
import {BookType} from '../model/book.model';

export const BOOK_CONVERSION_CAPABILITY_QUERY_KEY = ['book-conversion-capability'] as const;

export interface BookConversionCapability {
  available: boolean;
  supportedTargetFormats: BookType[];
}

export interface BookConversionResponse {
  acceptedCount: number;
  targetFormat: BookType;
}

@Injectable({providedIn: 'root'})
export class BookConversionService {
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/books`;
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly token = this.authService.token;

  private readonly capabilityQuery = injectQuery(() => ({
    queryKey: BOOK_CONVERSION_CAPABILITY_QUERY_KEY,
    queryFn: () => lastValueFrom(this.http.get<BookConversionCapability>(`${this.url}/conversion-capability`)),
    enabled: !!this.token(),
  }));

  readonly capability = computed<BookConversionCapability>(() => this.capabilityQuery.data() ?? {
    available: false,
    supportedTargetFormats: [],
  });

  readonly canConvert = computed(() => this.capability().available);

  convertBooks(bookIds: number[], targetFormat: BookType): Observable<BookConversionResponse> {
    return this.http.post<BookConversionResponse>(`${this.url}/convert`, {bookIds, targetFormat});
  }
}
