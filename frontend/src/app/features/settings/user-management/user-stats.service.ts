import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../core/config/api-config';
import {BookType} from '../../book/model/book.model';

export interface ReadingSessionHeatmapResponse {
  date: string;
  count: number;
}

export interface ReadingSessionTimelineResponse {
  bookId: number;
  bookTitle: string;
  startDate: string;
  bookType: BookType
  endDate: string;
  totalSessions: number;
  totalDurationSeconds: number;
}

export interface GenreStatsResponse {
  genre: string;
  bookCount: number;
  totalSessions: number;
  totalDurationSeconds: number;
  averageSessionsPerBook: number;
}

export interface CompletionTimelineResponse {
  year: number;
  month: number;
  totalBooks: number;
  statusBreakdown: Record<string, number>;
  finishedBooks: number;
  completionRate: number;
}

export interface FavoriteDaysResponse {
  dayOfWeek: number;
  dayName: string;
  sessionCount: number;
  totalDurationSeconds: number;
}

export interface PeakHoursResponse {
  hourOfDay: number;
  sessionCount: number;
  totalDurationSeconds: number;
}

export interface PageTurnerScoreResponse {
  bookId: number;
  bookTitle: string;
  categories: string[];
  pageCount: number;
  personalRating: number;
  gripScore: number;
  totalSessions: number;
  avgSessionDurationSeconds: number;
  sessionAcceleration: number;
  gapReduction: number;
  finishBurst: boolean;
}

export interface CompletionRaceResponse {
  bookId: number;
  bookTitle: string;
  sessionDate: string;
  endProgress: number;
}

export interface SessionScatterResponse {
  hourOfDay: number;
  durationMinutes: number;
  dayOfWeek: number;
}

@Injectable({
  providedIn: 'root'
})
export class UserStatsService {
  private readonly readingSessionsUrl = `${API_CONFIG.BASE_URL}/api/v1/user-stats`;
  private http = inject(HttpClient);

  getHeatmapForYear(year: number): Observable<ReadingSessionHeatmapResponse[]> {
    return this.http.get<ReadingSessionHeatmapResponse[]>(
      `${this.readingSessionsUrl}/reading/heatmap`,
      {params: {year: year.toString()}}
    );
  }

  getTimelineForWeek(year: number, week: number): Observable<ReadingSessionTimelineResponse[]> {
    return this.http.get<ReadingSessionTimelineResponse[]>(
      `${this.readingSessionsUrl}/reading/timeline`,
      {params: {year: year.toString(), week: week.toString()}}
    );
  }

  getGenreStats(): Observable<GenreStatsResponse[]> {
    return this.http.get<GenreStatsResponse[]>(
      `${this.readingSessionsUrl}/reading/genres`
    );
  }

  getCompletionTimelineForYear(year: number): Observable<CompletionTimelineResponse[]> {
    return this.http.get<CompletionTimelineResponse[]>(
      `${this.readingSessionsUrl}/reading/completion-timeline`,
      {params: {year: year.toString()}}
    );
  }

  getFavoriteDays(year?: number, month?: number): Observable<FavoriteDaysResponse[]> {
    const params: Record<string, string> = {};
    if (year !== undefined && year !== null) {
      params['year'] = year.toString();
    }
    if (month !== undefined && month !== null) {
      params['month'] = month.toString();
    }

    return this.http.get<FavoriteDaysResponse[]>(
      `${this.readingSessionsUrl}/reading/favorite-days`,
      {params}
    );
  }

  getPeakHours(year?: number, month?: number): Observable<PeakHoursResponse[]> {
    const params: Record<string, string> = {};
    if (year !== undefined && year !== null) {
      params['year'] = year.toString();
    }
    if (month !== undefined && month !== null) {
      params['month'] = month.toString();
    }

    return this.http.get<PeakHoursResponse[]>(
      `${this.readingSessionsUrl}/reading/peak-hours`,
      {params}
    );
  }

  getPageTurnerScores(): Observable<PageTurnerScoreResponse[]> {
    return this.http.get<PageTurnerScoreResponse[]>(
      `${this.readingSessionsUrl}/reading/page-turner-scores`
    );
  }

  getCompletionRace(year: number): Observable<CompletionRaceResponse[]> {
    return this.http.get<CompletionRaceResponse[]>(
      `${this.readingSessionsUrl}/reading/completion-race`,
      {params: {year: year.toString()}}
    );
  }

  getReadingDates(): Observable<ReadingSessionHeatmapResponse[]> {
    return this.http.get<ReadingSessionHeatmapResponse[]>(
      `${this.readingSessionsUrl}/reading/dates`
    );
  }

  getSessionScatter(year: number): Observable<SessionScatterResponse[]> {
    return this.http.get<SessionScatterResponse[]>(
      `${this.readingSessionsUrl}/reading/session-scatter`,
      {params: {year: year.toString()}}
    );
  }
}
