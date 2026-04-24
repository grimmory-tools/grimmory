import {computed, Component, effect, inject, OnDestroy, OnInit, signal} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {UserService} from '../../../settings/user-management/user.service';
import {Book, BookRecommendation} from '../../../book/model/book.model';
import {Subject} from 'rxjs';
import {distinctUntilChanged, filter, map, takeUntil,} from 'rxjs/operators';
import {BookService} from '../../../book/service/book.service';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {Tab, TabList, TabPanel, TabPanels, Tabs,} from 'primeng/tabs';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {Button} from 'primeng/button';
import {BookMetadataHostService} from '../../../../shared/service/book-metadata-host.service';
import {TranslocoDirective} from '@jsverse/transloco';
import {MetadataViewerComponent} from './metadata-viewer/metadata-viewer.component';
import {MetadataEditorComponent} from './metadata-editor/metadata-editor.component';
import {MetadataSearcherComponent} from './metadata-searcher/metadata-searcher.component';
import {SidecarViewerComponent} from './sidecar-viewer/sidecar-viewer.component';
import {injectQuery} from '@tanstack/angular-query-experimental';

@Component({
  selector: 'app-book-metadata-center',
  standalone: true,
  templateUrl: './book-metadata-center.component.html',
  imports: [
    Tabs,
    TabList,
    Tab,
    TabPanels,
    TabPanel,
    MetadataViewerComponent,
    MetadataEditorComponent,
    MetadataSearcherComponent,
    SidecarViewerComponent,
    Button,
    TranslocoDirective
  ],
  styleUrls: ['./book-metadata-center.component.scss'],
})
export class BookMetadataCenterComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private bookService = inject(BookService);
  private userService = inject(UserService);
  private appSettingsService = inject(AppSettingsService);
  private metadataHostService = inject(BookMetadataHostService);
  readonly config = inject(DynamicDialogConfig, {optional: true});
  readonly ref = inject(DynamicDialogRef, {optional: true});
  private destroy$ = new Subject<void>();

  private currentBookId = signal<number | null>(this.config?.data?.bookId ?? null);
  private bookQuery = injectQuery(() => {
    const bookId = this.currentBookId();

    if (bookId == null) {
      return {
        queryKey: ['books', 'detail', -1, true] as const,
        queryFn: async (): Promise<Book> => {
          throw new Error('No book selected');
        },
        enabled: false,
      };
    }

    return this.bookService.bookDetailQueryOptions(bookId, true);
  });
  readonly book = computed(() => this.bookQuery.data() ?? null);
  private readonly fetchRecommendations = effect(() => {
    const bookId = this.currentBookId();
    if (bookId == null) {
      this.recommendedBooks = [];
      return;
    }

    this.fetchBookRecommendationsIfNeeded(bookId);
  });

  recommendedBooks: BookRecommendation[] = [];
  private _tab: string = 'view';
  canEditMetadata: boolean = false;
  admin: boolean = false;
  private readonly syncUserPermissionsEffect = effect(() => {
    const user = this.userService.currentUser();
    if (!user) return;
    this.canEditMetadata = user.permissions?.canEditMetadata ?? false;
    this.admin = user.permissions?.admin ?? false;
  });
  get isPhysical(): boolean { return this.book()?.isPhysical ?? false; }
  isLocalStorage: boolean = true;
  get canShowSidecarTab(): boolean {
    const settings = this.appSettingsService.appSettings();
    const sidecarEnabled = settings?.metadataPersistenceSettings?.sidecarSettings?.enabled ?? false;

    return (this.admin || this.canEditMetadata) && !this.isPhysical && this.isLocalStorage && sidecarEnabled;
  }
  private validTabs = ['view', 'edit', 'match', 'sidecar'];

  get tab(): string {
    return this._tab;
  }

  set tab(value: string) {
    this._tab = value;

    if (!this.config) {
      this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { tab: value },
        queryParamsHandling: 'merge'
      });
    }
  }

  ngOnInit(): void {
    const bookIdFromDialog: number | undefined = this.config?.data?.bookId;
    if (bookIdFromDialog != null) {
      this.currentBookId.set(bookIdFromDialog);
    } else {
      this.route.paramMap
        .pipe(
          map(params => Number(params.get('bookId'))),
          filter(bookId => !isNaN(bookId)),
          takeUntil(this.destroy$)
        )
        .subscribe(bookId => this.currentBookId.set(bookId));
    }

    this.metadataHostService.bookSwitches$
      .pipe(
        filter((bookId): bookId is number => !!bookId),
        distinctUntilChanged(),
        takeUntil(this.destroy$)
      )
      .subscribe(bookId => this.currentBookId.set(bookId));

    this.route.queryParamMap
      .pipe(
        map(params => params.get('tab') ?? 'view'),
        distinctUntilChanged(),
        takeUntil(this.destroy$)
      )
      .subscribe(tabParam => {
        this._tab = this.validTabs.includes(tabParam) ? tabParam : 'view';
      });

    const currentSettings = this.appSettingsService.appSettings();
    if (currentSettings) {
      this.isLocalStorage = currentSettings.diskType === 'LOCAL';
    }
  }

  private fetchBookRecommendationsIfNeeded(bookId: number): void {
    const settings = this.appSettingsService.appSettings();
    if (!settings || !(settings.similarBookRecommendation ?? false)) {
      return;
    }
    this.bookService.getBookRecommendations(bookId)
      .pipe(takeUntil(this.destroy$))
      .subscribe(recommendations => {
        this.recommendedBooks = recommendations.sort(
          (a, b) => (b.similarityScore ?? 0) - (a.similarityScore ?? 0)
        );
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
