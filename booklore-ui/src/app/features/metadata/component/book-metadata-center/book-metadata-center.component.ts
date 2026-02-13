import {Component, inject, OnDestroy, OnInit, Optional} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {UserService} from '../../../settings/user-management/user.service';
import {Book, BookRecommendation} from '../../../book/model/book.model';
import {BehaviorSubject, Observable, Subject} from 'rxjs';
import {distinctUntilChanged, filter, map, shareReplay, switchMap, take, takeUntil,} from 'rxjs/operators';
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
  private destroy$ = new Subject<void>();

  book$!: Observable<Book>;
  recommendedBooks: BookRecommendation[] = [];
  private _tab: string = 'view';
  canEditMetadata: boolean = false;
  admin: boolean = false;

  private appSettings$ = this.appSettingsService.appSettings$;
  private currentBookId$ = new BehaviorSubject<number | null>(null);
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

  constructor(
    @Optional() public config?: DynamicDialogConfig,
    @Optional() public ref?: DynamicDialogRef
  ) {
  }

  ngOnInit(): void {
    const bookIdFromDialog: number | undefined = this.config?.data?.bookId;
    if (bookIdFromDialog != null) {
      this.currentBookId$.next(bookIdFromDialog);
    } else {
      this.route.paramMap
        .pipe(
          map(params => Number(params.get('bookId'))),
          filter(bookId => !isNaN(bookId)),
          takeUntil(this.destroy$)
        )
        .subscribe(bookId => this.currentBookId$.next(bookId));
    }

    this.metadataHostService.bookSwitches$
      .pipe(
        filter((bookId): bookId is number => !!bookId),
        distinctUntilChanged(),
        takeUntil(this.destroy$)
      )
      .subscribe(bookId => {
        this.currentBookId$.next(bookId);
      });

    this.book$ = this.currentBookId$.pipe(
      filter((bookId): bookId is number => bookId != null),
      distinctUntilChanged(),
      switchMap(bookId =>
        this.bookService.bookState$.pipe(
          map(state => state.books?.find(b => b.id === bookId)),
          filter((book): book is Book => !!book && !!book.metadata),
          distinctUntilChanged((a, b) => a.id === b.id && a.metadata === b.metadata),
          switchMap(book =>
            this.bookService.getBookByIdFromAPI(book.id, true)
          )
        )
      ),
      takeUntil(this.destroy$),
      shareReplay({bufferSize: 1, refCount: true})
    );

    this.currentBookId$
      .pipe(
        filter((id): id is number => id != null),
        takeUntil(this.destroy$)
      )
      .subscribe(bookId => this.fetchBookRecommendationsIfNeeded(bookId));

    this.route.queryParamMap
      .pipe(
        map(params => params.get('tab') ?? 'view'),
        distinctUntilChanged(),
        takeUntil(this.destroy$)
      )
      .subscribe(tabParam => {
        this._tab = this.validTabs.includes(tabParam) ? tabParam : 'view';
      });

    this.userService.userState$
      .pipe(
        filter(userState => !!userState?.user && userState.loaded),
        takeUntil(this.destroy$)
      )
      .subscribe(userState => {
        this.canEditMetadata = userState.user?.permissions?.canEditMetadata ?? false;
        this.admin = userState.user?.permissions?.admin ?? false;
      });
  }

  private fetchBookRecommendationsIfNeeded(bookId: number): void {
    this.appSettings$.pipe(
      filter(settings => settings != null),
      take(1),
      filter(settings => settings!.similarBookRecommendation ?? false),
      switchMap(() => this.bookService.getBookRecommendations(bookId)),
      takeUntil(this.destroy$)
    ).subscribe(recommendations => {
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
