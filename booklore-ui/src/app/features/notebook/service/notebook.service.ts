import {Injectable, inject} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../core/config/api-config';
import {NotebookBookOption, NotebookEntry, NotebookPage} from '../model/notebook.model';

@Injectable({
  providedIn: 'root'
})
export class NotebookService {

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/notebook`;
  private readonly http = inject(HttpClient);

  getNotebookEntries(page: number, size: number, types: string[], bookId: number | null,
                     search: string, sort: string): Observable<NotebookPage> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    for (const type of types) {
      params = params.append('types', type);
    }
    if (bookId !== null) {
      params = params.set('bookId', bookId);
    }
    if (search.trim()) {
      params = params.set('search', search.trim());
    }

    return this.http.get<NotebookPage>(this.url, {params});
  }

  getExportEntries(types: string[], bookId: number | null, search: string,
                   sort: string): Observable<NotebookEntry[]> {
    let params = new HttpParams().set('sort', sort);

    for (const type of types) {
      params = params.append('types', type);
    }
    if (bookId !== null) {
      params = params.set('bookId', bookId);
    }
    if (search.trim()) {
      params = params.set('search', search.trim());
    }

    return this.http.get<NotebookEntry[]>(`${this.url}/export`, {params});
  }

  getBooksWithAnnotations(search?: string): Observable<NotebookBookOption[]> {
    let params = new HttpParams();
    if (search?.trim()) {
      params = params.set('search', search.trim());
    }
    return this.http.get<NotebookBookOption[]>(`${this.url}/books`, {params});
  }
}
