import {Injectable, inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../config/api-config';

export interface Indexer {
  id?: number;
  name: string;
  url: string;
  apiKey: string;
  enabled: boolean;
  priority: number;
}

export interface DownloadClient {
  id?: number;
  name: string;
  type: 'SABNZBD';
  url: string;
  apiKey: string;
  category: string;
  enabled: boolean;
}

export interface ConnectionTestResult {
  success: boolean;
  message: string;
}

export interface WantedBook {
  id: number;
  title: string;
  author?: string;
  isbn13?: string;
  isbn10?: string;
  thumbnailUrl?: string;
  status: 'WANTED' | 'SEARCHING' | 'FOUND' | 'DOWNLOADING' | 'DOWNLOADED' | 'IMPORTED' | 'NOT_FOUND' | 'FAILED' | 'FAILED_PERMANENT';
  lastCheckedAt?: string;
  addedAt: string;
}

export interface JobHistoryItem {
  id: number;
  wantedBookId: number;
  indexerId?: number;
  nzbTitle?: string;
  confidence?: number;
  status: 'SENT' | 'FAILED' | 'SKIPPED';
  attemptedAt: string;
}

export interface BookSearchResult {
  title: string;
  author?: string;
  authors?: string[];
  isbn13?: string;
  isbn10?: string;
  isbn?: string;
  thumbnailUrl?: string;
  publishedDate?: string;
  provider?: string;
  providerBookId?: string;
  googleBookId?: string;
  id?: string;
}

export interface AddToWantedRequest {
  title: string;
  author?: string;
  isbn13?: string;
  isbn10?: string;
  provider?: string;
  providerBookId?: string;
  thumbnailUrl?: string;
}

@Injectable({providedIn: 'root'})
export class AcquisitionService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/acquisition`;

  getIndexers(): Observable<Indexer[]> {
    return this.http.get<Indexer[]>(`${this.baseUrl}/indexers`);
  }

  createIndexer(indexer: Indexer): Observable<Indexer> {
    return this.http.post<Indexer>(`${this.baseUrl}/indexers`, indexer);
  }

  updateIndexer(id: number, indexer: Indexer): Observable<Indexer> {
    return this.http.put<Indexer>(`${this.baseUrl}/indexers/${id}`, indexer);
  }

  deleteIndexer(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/indexers/${id}`);
  }

  testIndexer(id: number): Observable<ConnectionTestResult> {
    return this.http.post<ConnectionTestResult>(`${this.baseUrl}/indexers/${id}/test`, {});
  }

  getClients(): Observable<DownloadClient[]> {
    return this.http.get<DownloadClient[]>(`${this.baseUrl}/clients`);
  }

  createClient(client: DownloadClient): Observable<DownloadClient> {
    return this.http.post<DownloadClient>(`${this.baseUrl}/clients`, client);
  }

  updateClient(id: number, client: DownloadClient): Observable<DownloadClient> {
    return this.http.put<DownloadClient>(`${this.baseUrl}/clients/${id}`, client);
  }

  deleteClient(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/clients/${id}`);
  }

  testClient(id: number): Observable<ConnectionTestResult> {
    return this.http.post<ConnectionTestResult>(`${this.baseUrl}/clients/${id}/test`, {});
  }

  searchBooks(query: string, page: number = 0): Observable<BookSearchResult[]> {
    return this.http.get<BookSearchResult[]>(`${this.baseUrl}/search`, {params: {q: query, page}});
  }

  searchByIsbn(isbn: string): Observable<BookSearchResult[]> {
    return this.http.get<BookSearchResult[]>(`${this.baseUrl}/search`, {params: {isbn}});
  }

  getLibraryIsbns(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/library-isbns`);
  }

  addToWanted(req: AddToWantedRequest): Observable<WantedBook> {
    return this.http.post<WantedBook>(`${this.baseUrl}/wanted`, req);
  }

  getWantedBooks(): Observable<WantedBook[]> {
    return this.http.get<WantedBook[]>(`${this.baseUrl}/wanted`);
  }

  removeWanted(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/wanted/${id}`);
  }

  triggerSearch(id: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/wanted/${id}/search`, {});
  }

  getJobHistory(wantedBookId: number): Observable<JobHistoryItem[]> {
    return this.http.get<JobHistoryItem[]>(`${this.baseUrl}/wanted/${wantedBookId}/history`);
  }

  runAllNow(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/jobs/run-now`, {});
  }
}
