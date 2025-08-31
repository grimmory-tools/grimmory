import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable, throwError} from 'rxjs';
import {API_CONFIG} from '../../config/api-config';
import {Library} from '../../book/model/library.model';
import {catchError, distinctUntilChanged, finalize, shareReplay, tap} from 'rxjs/operators';
import {CbxPageSpread, CbxPageViewMode, PdfPageSpread, PdfPageViewMode} from '../../book/model/book.model';
import {AuthService} from '../../core/service/auth.service';

export interface EntityViewPreferences {
  global: EntityViewPreference;
  overrides: EntityViewPreferenceOverride[];
}

export interface EntityViewPreference {
  sortKey: string;
  sortDir: 'ASC' | 'DESC';
  view: 'GRID' | 'TABLE';
  coverSize: number;
  seriesCollapsed: boolean;
}

export interface EntityViewPreferenceOverride {
  entityType: 'LIBRARY' | 'SHELF';
  entityId: number;
  preferences: EntityViewPreference;
}

export interface SidebarLibrarySorting {
  field: string;
  order: string;
}

export interface SidebarShelfSorting {
  field: string;
  order: string;
}

export interface PerBookSetting {
  pdf: string;
  epub: string;
  cbx: string;
}

export type PageSpread = 'off' | 'even' | 'odd';

export interface PdfReaderSetting {
  pageSpread: PageSpread;
  pageZoom: string;
  showSidebar: boolean;
}

export interface EpubReaderSetting {
  theme: string;
  font: string;
  fontSize: number;
  flow: string;
  lineHeight: number;
  margin: number;
  letterSpacing: number;
}

export interface CbxReaderSetting {
  pageSpread: CbxPageSpread;
  pageViewMode: CbxPageViewMode;
}

export interface NewPdfReaderSetting {
  pageSpread: PdfPageSpread;
  pageViewMode: PdfPageViewMode;
}

export interface TableColumnPreference {
  field: string;
  visible: boolean;
  order: number;
}

export interface UserSettings {
  perBookSetting: PerBookSetting;
  pdfReaderSetting: PdfReaderSetting;
  epubReaderSetting: EpubReaderSetting;
  cbxReaderSetting: CbxReaderSetting;
  newPdfReaderSetting: NewPdfReaderSetting;
  sidebarLibrarySorting: SidebarLibrarySorting;
  sidebarShelfSorting: SidebarShelfSorting;
  filterSortingMode: 'alphabetical' | 'count';
  metadataCenterViewMode: 'route' | 'dialog';
  entityViewPreferences: EntityViewPreferences;
  tableColumnPreference?: TableColumnPreference[];
  koReaderEnabled: boolean;
}

export interface User {
  id: number;
  username: string;
  name: string;
  email: string;
  assignedLibraries: Library[];
  permissions: {
    admin: boolean;
    canUpload: boolean;
    canDownload: boolean;
    canEmailBook: boolean;
    canDeleteBook: boolean;
    canEditMetadata: boolean;
    canManipulateLibrary: boolean;
    canSyncKoReader: boolean;
    canSyncKobo: boolean;
    canAccessOpds: boolean;
  };
  userSettings: UserSettings;
  provisioningMethod?: 'LOCAL' | 'OIDC' | 'REMOTE';
}

export interface UserState {
  user: User | null;
  loaded: boolean;
  error: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private readonly apiUrl = `${API_CONFIG.BASE_URL}/api/v1/auth/register`;
  private readonly userUrl = `${API_CONFIG.BASE_URL}/api/v1/users`;

  private http = inject(HttpClient);
  private authService = inject(AuthService);

  private userStateSubject = new BehaviorSubject<UserState>({
    user: null,
    loaded: false,
    error: null,
  });

  private loading$: Observable<User> | null = null;

  constructor() {
    this.authService.token$.pipe(
      distinctUntilChanged()
    ).subscribe(token => {
      if (token === null) {
        this.userStateSubject.next({
          user: null,
          loaded: true,
          error: null,
        });
        this.loading$ = null;
      } else {
        const current = this.userStateSubject.value;
        if (current.loaded && !current.user) {
          this.userStateSubject.next({
            user: null,
            loaded: false,
            error: null,
          });
          this.loading$ = null;
        }
      }
    });
  }

  userState$ = this.userStateSubject.asObservable().pipe(
    tap(state => {
      if (!state.loaded && !state.error && !this.loading$) {
        this.loading$ = this.fetchMyself().pipe(
          shareReplay(1),
          finalize(() => (this.loading$ = null))
        );
        this.loading$.subscribe();
      }
    })
  );

  private fetchMyself(): Observable<User> {
    return this.http.get<User>(`${this.userUrl}/me`).pipe(
      tap(user => this.userStateSubject.next({ user, loaded: true, error: null })),
      catchError(err => {
        const curr = this.userStateSubject.value;
        this.userStateSubject.next({ user: curr.user, loaded: true, error: err.message });
        throw err;
      })
    );
  }

  public setInitialUser(user: User): void {
    this.userStateSubject.next({ user, loaded: true, error: null });
  }

  getCurrentUser(): User | null {
    return this.userStateSubject.value.user;
  }

  getMyself(): Observable<User> {
    return this.http.get<User>(`${this.userUrl}/me`);
  }

  createUser(userData: Omit<User, 'id'>): Observable<void> {
    return this.http.post<void>(this.apiUrl, userData);
  }

  getUsers(): Observable<User[]> {
    return this.http.get<User[]>(this.userUrl);
  }

  updateUser(userId: number, updateData: Partial<User>): Observable<User> {
    return this.http.put<User>(`${this.userUrl}/${userId}`, updateData);
  }

  deleteUser(userId: number): Observable<void> {
    return this.http.delete<void>(`${this.userUrl}/${userId}`);
  }

  changeUserPassword(userId: number, newPassword: string): Observable<void> {
    const payload = {
      userId: userId,
      newPassword: newPassword
    };
    return this.http.put<void>(`${this.userUrl}/change-user-password`, payload).pipe(
      catchError((error) => {
        const errorMessage = error?.error?.message || 'An unexpected error occurred. Please try again.';
        return throwError(() => new Error(errorMessage));
      })
    );
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    const payload = {
      currentPassword: currentPassword,
      newPassword: newPassword
    };
    return this.http.put<void>(`${this.userUrl}/change-password`, payload).pipe(
      catchError((error) => {
        const errorMessage = error?.error?.message || 'An unexpected error occurred. Please try again.';
        return throwError(() => new Error(errorMessage));
      })
    );
  }

  updateUserSetting(userId: number, key: string, value: any): void {
    const payload = {
      key,
      value
    };
    this.http.put<void>(`${this.userUrl}/${userId}/settings`, payload, {
      headers: {'Content-Type': 'application/json'},
      responseType: 'text' as 'json'
    }).subscribe(() => {
      const currentState = this.userStateSubject.value;
      if (currentState.user) {
        const updatedSettings = {...currentState.user.userSettings, [key]: value};
        const updatedUser = {...currentState.user, userSettings: updatedSettings};
        this.userStateSubject.next({...currentState, user: updatedUser});
      }
    });
  }
}
