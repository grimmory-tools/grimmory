import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../../core/config/api-config';

export interface BookReview {
  id?: number;
  metadataProvider?: string;
  reviewerName?: string;
  title?: string;
  rating?: number;
  date?: string;
  body?: string;
  country?: string;
  spoiler?: boolean;
  followersCount?: number;
  textReviewsCount?: number;
}

@Injectable({
  providedIn: 'root',
})
export class BookReviewService {

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/reviews`;
  private http = inject(HttpClient);

  getByBookId(bookId: number): Observable<BookReview[]> {
    return this.http.get<BookReview[]>(`${this.url}/book/${bookId}`);
  }

  refreshReviews(bookId: number): Observable<BookReview[]> {
    return this.http.post<BookReview[]>(`${this.url}/book/${bookId}/refresh`, {});
  }

  delete(reviewId: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${reviewId}`);
  }

  deleteAllByBookId(bookId: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/book/${bookId}`);
  }
}
