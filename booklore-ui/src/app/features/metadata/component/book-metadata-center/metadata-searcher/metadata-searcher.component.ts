import {Component, inject, Input, OnDestroy, OnInit} from '@angular/core';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {MultiSelect} from 'primeng/multiselect';

import {FetchMetadataRequest} from '../../../model/request/fetch-metadata-request.model';
import {Book, BookMetadata} from '../../../../book/model/book.model';
import {AppSettings} from '../../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';

import {BehaviorSubject, combineLatest, Observable, Subject, Subscription, takeUntil} from 'rxjs';
import {distinctUntilChanged, filter, switchMap} from 'rxjs/operators';
import {ActivatedRoute} from '@angular/router';
import {AsyncPipe} from '@angular/common';
import {MetadataPickerComponent} from '../metadata-picker/metadata-picker.component';
import {BookMetadataService} from '../../../../book/service/book-metadata.service';
import {Tooltip} from 'primeng/tooltip';

@Component({
  selector: 'app-metadata-searcher',
  templateUrl: './metadata-searcher.component.html',
  styleUrls: ['./metadata-searcher.component.scss'],
  imports: [
    ReactiveFormsModule,
    FormsModule,
    Button,
    InputText,
    MetadataPickerComponent,
    MultiSelect,
    AsyncPipe,
    Tooltip
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
  private bookMetadataService = inject(BookMetadataService);
  private appSettingsService = inject(AppSettingsService);
  private route = inject(ActivatedRoute);

  private subscription: Subscription = new Subscription();
  private cancelRequest$ = new Subject<void>();

  appSettings$: Observable<AppSettings | null> = this.appSettingsService.appSettings$;

  providerCounts: Map<string, number> = new Map();
  providerLoading: Map<string, boolean> = new Map();
  selectedProviderFilters: Set<string> = new Set(['all']);
  filteredMetadata: BookMetadata[] = [];
  providerFilterOptions: Array<{ label: string; value: string }> = [];

  private metadataByProvider: Map<string, BookMetadata[]> = new Map();
  private providerCompletionStatus: Map<string, boolean> = new Map();

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
      this.appSettings$
        .pipe(filter(settings => !!settings))
        .subscribe(settings => {
          const providerSettings = settings!.metadataProviderSettings ?? {};
          this.providers = Object.entries(providerSettings)
            .filter(([_, value]) => !!value && typeof value === 'object' && 'enabled' in value && (value as any).enabled)
            .map(([key]) => key.charAt(0).toUpperCase() + key.slice(1));

          const currentProviders = this.form.get('provider')?.value || [];
          const validProviders = currentProviders.filter((p: string) => this.providers.includes(p));
          if (validProviders.length !== currentProviders.length) {
            this.form.patchValue({provider: validProviders.length > 0 ? validProviders : null});
          }
        })
    );

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
              this.filteredMetadata = [];
              this.providerCounts.clear();
              this.selectedFetchedMetadata$.next(null);
            }
            return combineLatest([this.book$, this.appSettings$]);
          }),
          filter(([book, settings]) => !!book && !!settings),
          distinctUntilChanged(([prevBook], [currBook]) => prevBook?.id === currBook?.id)
        )
        .subscribe(([book, settings]) => {
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
    this.filteredMetadata = [];
    this.providerCounts.clear();
    this.metadataByProvider.clear();
    this.selectedProviderFilters = new Set(['all']);
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
    this.cancelRequest$.complete();
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
      this.allFetchedMetadata = [];
      this.filteredMetadata = [];
      this.providerCounts.clear();
      this.providerLoading.clear();
      this.providerCompletionStatus.clear();
      this.metadataByProvider.clear();
      this.selectedProviderFilters = new Set(['all']);
      this.cancelRequest$.next();

      providerKeys.forEach((provider: string) => {
        const providerLower = provider.toLowerCase();
        this.providerCounts.set(providerLower, 0);
        this.providerLoading.set(providerLower, true);
        this.providerCompletionStatus.set(providerLower, false);
        this.metadataByProvider.set(providerLower, []);
      });

      this.updateProviderFilterOptions();

      const activeProviders = new Set<string>(providerKeys.map((p: string) => p.toLowerCase()));

      this.bookMetadataService.fetchBookMetadata(fetchRequest.bookId, fetchRequest)
        .pipe(takeUntil(this.cancelRequest$))
        .subscribe({
          next: (metadata) => {
            const metadataWithThumbnail = {
              ...metadata,
              thumbnailUrl: metadata.thumbnailUrl
            };

            const provider = this.getProviderFromMetadata(metadata);
            if (provider) {
              const providerList = this.metadataByProvider.get(provider) || [];
              providerList.push(metadataWithThumbnail);
              this.metadataByProvider.set(provider, providerList);

              this.providerCounts.set(provider, providerList.length);

              if (!this.providerCompletionStatus.get(provider)) {
                this.providerLoading.set(provider, false);
                this.providerCompletionStatus.set(provider, true);
              }
            }

            this.allFetchedMetadata = this.interleaveResults();

            this.applyFilter();
            this.updateProviderFilterOptions();
          },
          error: (error) => {
            console.error('Error fetching metadata:', error);
            this.loading = false;
            this.providerLoading.clear();
          },
          complete: () => {
            this.loading = false;
            activeProviders.forEach((provider: string) => {
              if (!this.providerCompletionStatus.get(provider)) {
                this.providerLoading.set(provider, false);
                this.providerCompletionStatus.set(provider, true);
              }
            });
          }
        });
    } else {
      console.warn('Form is invalid. Please fill in all required fields.');
    }
  }

  private interleaveResults(): BookMetadata[] {
    const interleaved: BookMetadata[] = [];
    const providers = Array.from(this.metadataByProvider.keys());

    if (providers.length === 0) return [];

    const maxLength = Math.max(
      ...Array.from(this.metadataByProvider.values()).map(list => list.length)
    );

    for (let i = 0; i < maxLength; i++) {
      for (const provider of providers) {
        const providerList = this.metadataByProvider.get(provider);
        if (providerList && i < providerList.length) {
          interleaved.push(providerList[i]);
        }
      }
    }

    return interleaved;
  }

  private getProviderFromMetadata(metadata: BookMetadata): string | null {
    if (metadata.asin) return 'amazon';
    if (metadata.goodreadsId) return 'goodreads';
    if (metadata.googleId) return 'google';
    if (metadata.hardcoverId) return 'hardcover';
    if (metadata['doubanId']) return 'douban';
    if (metadata['lubimyczytacId']) return 'lubimyczytac';
    if (metadata.comicvineId) return 'comicvine';
    if (metadata.ranobedbId) return 'ranobedb';
    return null;
  }

  getProviderClass(metadata: BookMetadata): string {
    return this.getProviderFromMetadata(metadata) || 'unknown';
  }

  private updateProviderFilterOptions(): void {
    this.providerFilterOptions = [
      {label: `All (${this.allFetchedMetadata.length})`, value: 'all'},
      ...Array.from(this.providerCounts.entries())
        .filter(([_, count]) => count > 0)
        .map(([provider, count]) => ({
          label: `${provider.charAt(0).toUpperCase() + provider.slice(1)} (${count})`,
          value: provider
        }))
    ];
  }

  onProviderPillClick(provider: string, event: MouseEvent): void {
    const providerLower = provider.toLowerCase();

    if (event.ctrlKey || event.metaKey) {
      if (this.selectedProviderFilters.has(providerLower)) {
        this.selectedProviderFilters.delete(providerLower);
      } else {
        this.selectedProviderFilters.add(providerLower);
        this.selectedProviderFilters.delete('all');
      }

      if (this.selectedProviderFilters.size === 0) {
        this.selectedProviderFilters.add('all');
      }
    } else {
      if (this.selectedProviderFilters.has(providerLower) && this.selectedProviderFilters.size === 1) {
        this.selectedProviderFilters.clear();
        this.selectedProviderFilters.add('all');
      } else {
        this.selectedProviderFilters.clear();
        this.selectedProviderFilters.add(providerLower);
      }
    }

    this.applyFilter();
  }

  isProviderPillActive(provider: string): boolean {
    return this.selectedProviderFilters.has(provider.toLowerCase());
  }

  isProviderLoading(provider: string): boolean {
    return this.providerLoading.get(provider.toLowerCase()) ?? false;
  }

  private applyFilter(): void {
    if (this.selectedProviderFilters.has('all')) {
      this.filteredMetadata = [...this.allFetchedMetadata];
    } else {
      this.filteredMetadata = this.allFetchedMetadata.filter(metadata => {
        const provider = this.getProviderFromMetadata(metadata);
        return provider && this.selectedProviderFilters.has(provider);
      });
    }
  }

  getProviderTabs(): Array<{ provider: string; count: number }> {
    return Array.from(this.providerCounts.entries()).map(([provider, count]) => ({
      provider: provider.charAt(0).toUpperCase() + provider.slice(1),
      count
    }));
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
    } else if (metadata['lubimyczytacId']) {
      return `<a href="https://lubimyczytac.pl/ksiazka/${metadata['lubimyczytacId']}/ksiazka" target="_blank">Lubimyczytac</a>`;
    } else if (metadata.comicvineId) {
      if (metadata.comicvineId.startsWith('4000')) {
        const name = metadata.seriesName ? metadata.seriesName.replace(/ /g, '-').toLowerCase() + "-" + metadata.seriesNumber : '';
        return `<a href="https://comicvine.gamespot.com/${name}/${metadata.comicvineId}" target="_blank">Comicvine</a>`;
      }
      const name = metadata.seriesName;
      return `<a href="https://comicvine.gamespot.com/volume/${metadata.comicvineId}" target="_blank">Comicvine</a>`;
    } else if (metadata.ranobedbId) {
      return `<a href="https://ranobedb.org/book/${metadata.ranobedbId}" target="_blank">RanobeDB</a>`;
    }
    throw new Error("No provider ID found in metadata.");
  }

  trackByMetadata(index: number, metadata: BookMetadata): string {
    return metadata.googleId || metadata.goodreadsId || metadata.asin ||
      metadata.hardcoverId || metadata.comicvineId || index.toString();
  }

  onProviderClick(event: MouseEvent) {
    const target = event.target as HTMLElement;
    if (target.tagName === 'A' || target.closest('a')) {
      event.stopPropagation();
    }
  }
}
