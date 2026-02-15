import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable, throwError} from 'rxjs';
import {API_CONFIG} from '../../../core/config/api-config';
import {Library} from '../../book/model/library.model';
import {catchError, distinctUntilChanged, finalize, shareReplay, tap} from 'rxjs/operators';
import {AuthService} from '../../../shared/service/auth.service';
import {DashboardConfig} from '../../dashboard/models/dashboard-config.model';

export interface EntityViewPreferences {
  global: EntityViewPreference;
  overrides: EntityViewPreferenceOverride[];
}

export interface SortCriterion {
  field: string;
  direction: 'ASC' | 'DESC';
}

export interface EntityViewPreference {
  sortKey: string;
  sortDir: 'ASC' | 'DESC';
  sortCriteria?: SortCriterion[];
  view: 'GRID' | 'TABLE';
  coverSize: number;
  seriesCollapsed: boolean;
  overlayBookType: boolean;
}

export interface EntityViewPreferenceOverride {
  entityType: 'LIBRARY' | 'SHELF' | 'MAGIC_SHELF';
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

export interface SidebarMagicShelfSorting {
  field: string;
  order: string;
}

export interface PerBookSetting {
  pdf: string;
  epub: string;
  cbx: string;
  newPdf?: string;
}

export type PageSpread = 'off' | 'even' | 'odd';
export type BookFilterMode = 'and' | 'or' | 'single';


export enum CbxPageViewMode {
  SINGLE_PAGE = 'SINGLE_PAGE',
  TWO_PAGE = 'TWO_PAGE',
}

export enum CbxPageSpread {
  EVEN = 'EVEN',
  ODD = 'ODD',
}

export enum CbxBackgroundColor {
  GRAY = 'GRAY',
  BLACK = 'BLACK',
  WHITE = 'WHITE'
}

export enum CbxFitMode {
  ACTUAL_SIZE = 'ACTUAL_SIZE',
  FIT_PAGE = 'FIT_PAGE',
  FIT_WIDTH = 'FIT_WIDTH',
  FIT_HEIGHT = 'FIT_HEIGHT',
  AUTO = 'AUTO'
}

export enum CbxScrollMode {
  PAGINATED = 'PAGINATED',
  INFINITE = 'INFINITE',
  LONG_STRIP = 'LONG_STRIP'
}

export enum CbxReadingDirection {
  LTR = 'LTR',
  RTL = 'RTL'
}

export enum CbxSlideshowInterval {
  THREE_SECONDS = 3000,
  FIVE_SECONDS = 5000,
  TEN_SECONDS = 10000,
  FIFTEEN_SECONDS = 15000,
  THIRTY_SECONDS = 30000
}

export interface PdfReaderSetting {
  pageSpread: PageSpread;
  pageZoom: string;
  showSidebar: boolean;
}

export enum PdfPageViewMode {
  SINGLE_PAGE = 'SINGLE_PAGE',
  TWO_PAGE = 'TWO_PAGE',
}

export enum PdfPageSpread {
  EVEN = 'EVEN',
  ODD = 'ODD',
}

export enum PdfBackgroundColor {
  GRAY = 'GRAY',
  BLACK = 'BLACK',
  WHITE = 'WHITE'
}

export enum PdfFitMode {
  ACTUAL_SIZE = 'ACTUAL_SIZE',
  FIT_PAGE = 'FIT_PAGE',
  FIT_WIDTH = 'FIT_WIDTH',
  FIT_HEIGHT = 'FIT_HEIGHT',
  AUTO = 'AUTO'
}

export enum PdfScrollMode {
  PAGINATED = 'PAGINATED',
  INFINITE = 'INFINITE'
}

export interface NewPdfReaderSetting {
  pageSpread: PdfPageSpread;
  pageViewMode: PdfPageViewMode;
  fitMode: PdfFitMode;
  scrollMode?: PdfScrollMode;
  backgroundColor?: PdfBackgroundColor;
}

export interface EbookReaderSetting {
  lineHeight: number;
  justify: boolean;
  hyphenate: boolean;
  maxColumnCount: number;
  gap: number;
  fontSize: number;
  theme: string
  maxInlineSize: number;
  maxBlockSize: number;
  fontFamily: string;
  isDark: boolean;
  flow: 'paginated' | 'scrolled';
}

export interface EpubReaderSetting {
  theme: string;
  font: string;
  fontSize: number;
  flow: string;
  spread: string;
  lineHeight: number;
  margin: number;
  letterSpacing: number;
  customFontId?: number | null;
}

export interface CbxReaderSetting {
  pageSpread: CbxPageSpread;
  pageViewMode: CbxPageViewMode;
  fitMode: CbxFitMode;
  scrollMode?: CbxScrollMode;
  backgroundColor?: CbxBackgroundColor;
  readingDirection?: CbxReadingDirection;
  slideshowInterval?: CbxSlideshowInterval;
}

export interface TableColumnPreference {
  field: string;
  visible: boolean;
  order: number;
}

export type VisibleFilterType =
  | 'author' | 'category' | 'series' | 'bookType' | 'readStatus'
  | 'personalRating' | 'publisher' | 'matchScore' | 'library' | 'shelf'
  | 'shelfStatus' | 'tag' | 'publishedDate' | 'fileSize' | 'amazonRating'
  | 'goodreadsRating' | 'hardcoverRating' | 'language' | 'pageCount' | 'mood'
  | 'ageRating' | 'contentRating'
  | 'narrator'
  | 'comicCharacter' | 'comicTeam' | 'comicLocation' | 'comicCreator';

export const DEFAULT_VISIBLE_FILTERS: VisibleFilterType[] = [
  'author', 'category', 'series', 'bookType', 'readStatus',
  'personalRating', 'library', 'tag', 'ageRating', 'contentRating',
  'matchScore', 'publisher', 'publishedDate', 'fileSize'
];

// Translation key for each filter option — use book.filter.labels.<value>
export const ALL_FILTER_OPTION_VALUES: VisibleFilterType[] = [
  'author', 'category', 'series', 'bookType', 'readStatus',
  'personalRating', 'library', 'tag', 'ageRating', 'contentRating',
  'matchScore', 'publisher', 'publishedDate', 'fileSize', 'shelf',
  'shelfStatus', 'language', 'pageCount', 'mood', 'amazonRating',
  'goodreadsRating', 'hardcoverRating', 'narrator',
  'comicCharacter', 'comicTeam', 'comicLocation', 'comicCreator'
];

export const ALL_FILTER_OPTIONS: { label: string; value: VisibleFilterType }[] = [
  {label: 'Author', value: 'author'},
  {label: 'Genre', value: 'category'},
  {label: 'Series', value: 'series'},
  {label: 'Book Type', value: 'bookType'},
  {label: 'Read Status', value: 'readStatus'},
  {label: 'Personal Rating', value: 'personalRating'},
  {label: 'Library', value: 'library'},
  {label: 'Tag', value: 'tag'},
  {label: 'Age Rating', value: 'ageRating'},
  {label: 'Content Rating', value: 'contentRating'},
  {label: 'Metadata Match Score', value: 'matchScore'},
  {label: 'Publisher', value: 'publisher'},
  {label: 'Published Year', value: 'publishedDate'},
  {label: 'File Size', value: 'fileSize'},
  {label: 'Shelf', value: 'shelf'},
  {label: 'Shelf Status', value: 'shelfStatus'},
  {label: 'Language', value: 'language'},
  {label: 'Page Count', value: 'pageCount'},
  {label: 'Mood', value: 'mood'},
  {label: 'Amazon Rating', value: 'amazonRating'},
  {label: 'Goodreads Rating', value: 'goodreadsRating'},
  {label: 'Hardcover Rating', value: 'hardcoverRating'},
  {label: 'Narrator', value: 'narrator'},
  {label: 'Comic Character', value: 'comicCharacter'},
  {label: 'Comic Team', value: 'comicTeam'},
  {label: 'Comic Location', value: 'comicLocation'},
  {label: 'Comic Creator', value: 'comicCreator'}
];

export const DEFAULT_VISIBLE_SORT_FIELDS: string[] = [
  'title', 'seriesName', 'fileName', 'filePath',
  'author', 'authorSurnameVorname', 'seriesNumber',
  'lastReadTime', 'personalRating', 'addedOn',
  'fileSizeKb', 'locked', 'publisher', 'publishedDate', 'pageCount', 'random'
];

export interface UserSettings {
  perBookSetting: PerBookSetting;
  pdfReaderSetting: PdfReaderSetting;
  epubReaderSetting: EpubReaderSetting;
  ebookReaderSetting: EbookReaderSetting;
  cbxReaderSetting: CbxReaderSetting;
  newPdfReaderSetting: NewPdfReaderSetting;
  sidebarLibrarySorting: SidebarLibrarySorting;
  sidebarShelfSorting: SidebarShelfSorting;
  sidebarMagicShelfSorting: SidebarMagicShelfSorting;
  filterMode: BookFilterMode;
  visibleFilters?: VisibleFilterType[];
  visibleSortFields?: string[];
  metadataCenterViewMode: 'route' | 'dialog';
  enableSeriesView: boolean;
  entityViewPreferences: EntityViewPreferences;
  tableColumnPreference?: TableColumnPreference[];
  dashboardConfig?: DashboardConfig;
  koReaderEnabled: boolean;
  autoSaveMetadata: boolean;
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
    canManageLibrary: boolean;
    canManageMetadataConfig: boolean;
    canSyncKoReader: boolean;
    canSyncKobo: boolean;
    canAccessOpds: boolean;
    canAccessBookdrop: boolean;
    canAccessLibraryStats: boolean;
    canAccessUserStats: boolean;
    canAccessTaskManager: boolean;
    canManageEmailConfig: boolean;
    canManageGlobalPreferences: boolean;
    canManageIcons: boolean;
    canManageFonts: boolean;
    demoUser: boolean;
    canBulkAutoFetchMetadata: boolean;
    canBulkCustomFetchMetadata: boolean;
    canBulkEditMetadata: boolean;
    canBulkRegenerateCover: boolean;
    canMoveOrganizeFiles: boolean;
    canBulkLockUnlockMetadata: boolean;
    canBulkResetBookloreReadProgress?: boolean;
    canBulkResetKoReaderReadProgress?: boolean;
    canBulkResetBookReadStatus?: boolean;
  };
  userSettings: UserSettings;
  provisioningMethod?: 'LOCAL' | 'OIDC' | 'REMOTE';
}

export interface UserState {
  user: User | null;
  loaded: boolean;
  error: string | null;
}

export interface UserUpdateRequest {
  name?: string;
  email?: string;
  permissions?: User['permissions'];
  assignedLibraries?: number[];
}

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private readonly apiUrl = `${API_CONFIG.BASE_URL}/api/v1/auth/register`;
  private readonly userUrl = `${API_CONFIG.BASE_URL}/api/v1/users`;

  private http = inject(HttpClient);
  private authService = inject(AuthService);

  userStateSubject = new BehaviorSubject<UserState>({
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
      tap(user => this.userStateSubject.next({user, loaded: true, error: null})),
      catchError(err => {
        const curr = this.userStateSubject.value;
        this.userStateSubject.next({user: curr.user, loaded: true, error: err.message});
        throw err;
      })
    );
  }

  public setInitialUser(user: User): void {
    this.userStateSubject.next({user, loaded: true, error: null});
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

  updateUser(userId: number, updateData: UserUpdateRequest): Observable<User> {
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

  updateUserSetting(userId: number, key: string, value: unknown): void {
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
