import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {AppSettings} from '../model/app-settings.model';
import {API_CONFIG} from '../../config/api-config';
import {catchError, map} from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class AppSettingsService {
  private readonly apiUrl = `${API_CONFIG.BASE_URL}/api/v1/settings`;

  private appSettingsSubject = new BehaviorSubject<AppSettings | null>(null);
  appSettings$ = this.appSettingsSubject.asObservable();

  constructor(private http: HttpClient) {
    this.loadAppSettings();
  }

  loadAppSettings(): void {
    this.http.get<AppSettings>(this.apiUrl).subscribe({
      next: (settings: AppSettings) => {
        this.appSettingsSubject.next(settings);
      },
      error: (error) => {
        console.error('Error loading app settings:', error);
        this.appSettingsSubject.next(null);
      }
    });
  }

  saveSettings(settings: { key: string, newValue: any }[]): Observable<void> {
    const payload = settings.map(setting => ({
      name: setting.key,
      value: setting.newValue
    }));

    return this.http.put<void>(this.apiUrl, payload).pipe(
      map(() => {
        this.loadAppSettings();
      }),
      catchError(err => {
        console.error('Error saving settings:', err);
        return of();
      })
    );
  }
}
