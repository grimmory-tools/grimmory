import {Injectable, inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../core/config/api-config';

export type AnnotationStyle = 'highlight' | 'underline' | 'strikethrough' | 'squiggly';

export interface Annotation {
  id: number;
  userId?: number;
  bookId: number;
  cfi: string;
  text: string;
  color: string;
  style: AnnotationStyle;
  note?: string;
  chapterTitle?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface CreateAnnotationRequest {
  bookId: number;
  cfi: string;
  text: string;
  color?: string;
  style?: AnnotationStyle;
  note?: string;
  chapterTitle?: string;
}

export interface UpdateAnnotationRequest {
  color?: string;
  style?: AnnotationStyle;
  note?: string;
}

@Injectable({
  providedIn: 'root'
})
export class AnnotationService {

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/annotations`;
  private readonly http = inject(HttpClient);

  getAnnotationsForBook(bookId: number): Observable<Annotation[]> {
    return this.http.get<Annotation[]>(`${this.url}/book/${bookId}`);
  }

  createAnnotation(request: CreateAnnotationRequest): Observable<Annotation> {
    return this.http.post<Annotation>(this.url, request);
  }

  deleteAnnotation(annotationId: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${annotationId}`);
  }

  updateAnnotation(annotationId: number, request: UpdateAnnotationRequest): Observable<Annotation> {
    return this.http.put<Annotation>(`${this.url}/${annotationId}`, request);
  }
}
