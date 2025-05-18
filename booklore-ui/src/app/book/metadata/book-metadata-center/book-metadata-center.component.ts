import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {BookMetadataCenterService} from './book-metadata-center.service';
import {UserService} from '../../../settings/user-management/user.service';
import {Book, BookRecommendation} from '../../model/book.model';
import {Subscription} from 'rxjs';
import {distinctUntilChanged, filter, map, switchMap, take} from 'rxjs/operators';
import {BookService} from '../../service/book.service';
import {AppSettingsService} from '../../../core/service/app-settings.service';
import {Tab, TabList, TabPanel, TabPanels, Tabs} from 'primeng/tabs';
import {NgIf} from '@angular/common';
import {MetadataViewerComponent} from './metadata-viewer/metadata-viewer.component';
import {MetadataEditorComponent} from './metadata-editor/metadata-editor.component';
import {MetadataSearcherComponent} from './metadata-searcher/metadata-searcher.component';

@Component({
  selector: 'app-book-metadata-center',
  standalone: true,
  templateUrl: './book-metadata-center.component.html',
  styleUrl: './book-metadata-center.component.scss',
  imports: [
    TabList,
    Tabs,
    Tab,
    NgIf,
    TabPanels,
    MetadataViewerComponent,
    TabPanel,
    MetadataEditorComponent,
    MetadataSearcherComponent
  ]
})
export class BookMetadataCenterComponent implements OnInit, OnDestroy {

  private route = inject(ActivatedRoute);
  private bookService = inject(BookService);
  private metadataCenterService = inject(BookMetadataCenterService);
  private userService = inject(UserService);
  private appSettingsService = inject(AppSettingsService);

  book: Book | undefined;
  recommendedBooks: BookRecommendation[] = [];
  tab: string = 'view';
  canEditMetadata: boolean = false;
  admin: boolean = false;

  private userSubscription: Subscription = Subscription.EMPTY;
  private routeSubscription: Subscription = Subscription.EMPTY;

  private appSettings$ = this.appSettingsService.appSettings$;

  ngOnInit(): void {
    this.bookService.loadBooks();

    this.routeSubscription = this.route.paramMap.pipe(
      switchMap(params => {
        const bookId = Number(params.get('bookId'));
        if (isNaN(bookId)) throw new Error('Invalid book ID');
        return this.route.queryParamMap.pipe(
          map(queryParams => ({
            bookId,
            tab: queryParams.get('tab') ?? 'view'
          }))
        );
      }),
      switchMap(({bookId, tab}) => {
        const validTabs = ['view', 'edit', 'match'];
        this.tab = validTabs.includes(tab) ? tab : 'view';
        return this.bookService.getBookByIdFromAPI(bookId, true);
      }),
      filter(book => !!book && !!book.metadata),
      distinctUntilChanged((prev, curr) => prev.id === curr.id)
    ).subscribe(book => {
      this.book = book;
      this.metadataCenterService.emitBookChanged(book);
      this.metadataCenterService.emitMetadata(book.metadata!);
      this.fetchBookRecommendationsIfNeeded(book.metadata!.bookId);
    });

    this.userSubscription = this.userService.userData$.subscribe(userData => {
      const userPermissions = userData?.permissions;
      this.canEditMetadata = userPermissions?.canEditMetadata ?? false;
      this.admin = userPermissions?.admin ?? false;
    });
  }

  ngOnDestroy(): void {
    this.userSubscription.unsubscribe();
    this.routeSubscription.unsubscribe();
    this.metadataCenterService.emitBookChanged(null);
  }

  private fetchBookRecommendationsIfNeeded(bookId: number): void {
    this.appSettings$
      .pipe(
        filter(settings => settings != null),
        take(1)
      )
      .subscribe(settings => {
        const similarBookRecommendation = settings!.similarBookRecommendation ?? false;
        if (similarBookRecommendation) {
          this.bookService.getBookRecommendations(bookId).subscribe(recommendations => {
            this.recommendedBooks = recommendations;
          });
        }
      });
  }
}
