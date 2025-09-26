import {Component, inject, Input, OnDestroy, OnInit} from '@angular/core';
import {FormBuilder, FormGroup, ReactiveFormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {Divider} from 'primeng/divider';
import {MultiSelect} from 'primeng/multiselect';
import {ProgressSpinner} from 'primeng/progressspinner';

import {FetchMetadataRequest} from '../../model/request/fetch-metadata-request.model';
import {MetadataPickerComponent} from '../metadata-picker/metadata-picker.component';
import {Book, BookMetadata} from '../../../book/model/book.model';
import {BookService} from '../../../book/service/book.service';
import {AppSettings} from '../../../core/model/app-settings.model';
import {AppSettingsService} from '../../../core/service/app-settings.service';

import {BehaviorSubject, combineLatest, Observable, Subject, Subscription, takeUntil} from 'rxjs';
import {distinctUntilChanged, filter, switchMap} from 'rxjs/operators';
import {ActivatedRoute} from '@angular/router';
import {AsyncPipe} from '@angular/common';

@Component({
  selector: 'app-metadata-searcher',
  templateUrl: './metadata-searcher.component.html',
  styleUrls: ['./metadata-searcher.component.scss'],
  imports: [
    ReactiveFormsModule,
    Button,
    InputText,
    Divider,
    ProgressSpinner,
    MetadataPickerComponent,
    MultiSelect,
    AsyncPipe
  ],
  standalone: true
})
export class MetadataSearcherComponent implements OnInit, OnDestroy {
  form: FormGroup;
  providers: string[] = [];
  allFetchedMetadata: BookMetadata[] = [];
  bookId!: number;
  loading: boolean = false;
  searchTriggered = false;

  @Input() book$!: Observable<Book | null>;

  selectedFetchedMetadata$ = new BehaviorSubject<BookMetadata | null>(null);

  private formBuilder = inject(FormBuilder);
  private bookService = inject(BookService);
  private appSettingsService = inject(AppSettingsService);
  private route = inject(ActivatedRoute);

  private subscription: Subscription = new Subscription();
  private cancelRequest$ = new Subject<void>();

  appSettings$: Observable<AppSettings | null> = this.appSettingsService.appSettings$;

  constructor() {
    this.form = this.formBuilder.group({
      provider: null,
      title: [''],
      author: [''],
      isbn: ['']
    });
  }

  ngOnInit() {
    this.subscription.add(
      this.route.paramMap
        .pipe(
          switchMap(params => {
            const bookId = +params.get('id')!;
            if (this.bookId !== bookId) {
              this.bookId = bookId;
              this.cancelRequest$.next();
              this.loading = false;
              this.allFetchedMetadata = [];
              this.selectedFetchedMetadata$.next(null);
            }
            return combineLatest([this.book$, this.appSettings$]);
          }),
          filter(([book, settings]) => !!book && !!settings),
          distinctUntilChanged(([prevBook], [currBook]) => prevBook?.id === currBook?.id)
        )
        .subscribe(([book, settings]) => {
          const providerSettings = settings?.metadataProviderSettings ?? {};
          this.providers = Object.entries(providerSettings)
            .filter(([_, value]) => !!value && typeof value === 'object' && 'enabled' in value && (value as any).enabled)
            .map(([key]) => key.charAt(0).toUpperCase() + key.slice(1));

          this.resetFormFromBook(book!);

          if (settings!.autoBookSearch) {
            this.onSubmit();
          }
        })
    );
  }

  private resetFormFromBook(book: Book): void {
    this.selectedFetchedMetadata$.next(null);
    this.allFetchedMetadata = [];
    this.bookId = book.id;

    this.form.patchValue({
      provider: this.providers,
      title: book.metadata?.title ?? '',
      author: book.metadata?.authors?.[0] ?? '',
      isbn: book.metadata?.isbn13 ?? book.metadata?.isbn10 ?? ''
    });
  }

  ngOnDestroy(): void {
    this.cancelRequest$.next();
    this.subscription.unsubscribe();
    this.selectedFetchedMetadata$.complete();
  }

  get isSearchEnabled(): boolean {
    const providerSelected = !!this.form.get('provider')?.value;
    const title = this.form.get('title')?.value;
    const isbn = this.form.get('isbn')?.value;
    return providerSelected && (title || isbn);
  }

  onSubmit(): void {
    this.searchTriggered = true;
    if (this.form.valid) {
      const providerKeys = this.form.get('provider')?.value;
      if (!providerKeys) return;

      const fetchRequest: FetchMetadataRequest = {
        bookId: this.bookId,
        providers: providerKeys,
        title: this.form.get('title')?.value,
        author: this.form.get('author')?.value,
        isbn: this.form.get('isbn')?.value
      };

      this.loading = true;
      this.cancelRequest$.next();

      this.bookService.fetchBookMetadata(fetchRequest.bookId, fetchRequest)
        .pipe(takeUntil(this.cancelRequest$))
        .subscribe({
          next: (fetchedMetadata) => {
            this.loading = false;
            this.allFetchedMetadata = fetchedMetadata.map(m => ({
              ...m,
              thumbnailUrl: m.thumbnailUrl
            }));
          },
          error: () => {
            this.loading = false;
          }
        });
    } else {
      console.warn('Form is invalid. Please fill in all required fields.');
    }
  }

  onBookClick(fetchedMetadata: BookMetadata) {
    this.selectedFetchedMetadata$.next(fetchedMetadata);
  }

  onGoBack() {
    this.selectedFetchedMetadata$.next(null);
  }

  sanitizeHtml(htmlString: string | null | undefined): string {
    if (!htmlString) return '';
    return htmlString.replace(/<\/?[^>]+(>|$)/g, '').trim();
  }

  truncateText(text: string | null, length: number): string {
    const safeText = text ?? '';
    return safeText.length > length ? safeText.substring(0, length) + '...' : safeText;
  }

  buildProviderLink(metadata: BookMetadata): string {
    if (metadata.asin) {
      return `<a href="https://www.amazon.com/dp/${metadata.asin}" target="_blank">Amazon</a>`;
    } else if (metadata.goodreadsId) {
      return `<a href="https://www.goodreads.com/book/show/${metadata.goodreadsId}" target="_blank">Goodreads</a>`;
    } else if (metadata.googleId) {
      return `<a href="https://books.google.com/books?id=${metadata.googleId}" target="_blank">Google</a>`;
    } else if (metadata.hardcoverId) {
      return `<a href="https://hardcover.app/books/${metadata.hardcoverId}" target="_blank">Hardcover</a>`;
    } else if (metadata['doubanId']) {
      return `<a href="https://book.douban.com/subject/${metadata['doubanId']}" target="_blank">Douban</a>`;
    } else if (metadata.comicvineId) {
      if( metadata.comicvineId.startsWith('4000')) {
        const name = metadata.seriesName ? metadata.seriesName.replace(/ /g, '-').toLowerCase() + "-" + metadata.seriesNumber : '';
        return `<a href="https://comicvine.gamespot.com/${name}/${metadata.comicvineId}" target="_blank">Comicvine</a>`;
      }
      const name = metadata.seriesName;
      return `<a href="https://comicvine.gamespot.com/volume/${metadata.comicvineId}" target="_blank">Comicvine</a>`;
    }
    throw new Error("No provider ID found in metadata.");
  }
}
