import {Component, computed, effect, inject, Input, OnChanges, OnDestroy, signal, SimpleChanges, WritableSignal} from '@angular/core';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {MultiSelect} from 'primeng/multiselect';

import {FetchMetadataRequest} from '../../../model/request/fetch-metadata-request.model';
import {Book, BookMetadata} from '../../../../book/model/book.model';
import {AppSettings} from '../../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';

import {Subject, Subscription, takeUntil} from 'rxjs';
import {MetadataPickerComponent} from '../metadata-picker/metadata-picker.component';
import {BookMetadataService} from '../../../../book/service/book-metadata.service';
import {Tooltip} from 'primeng/tooltip';
import {TranslocoDirective} from '@jsverse/transloco';

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
    Tooltip,
    TranslocoDirective
  ],
  standalone: true
})
export class MetadataSearcherComponent implements OnDestroy, OnChanges {
  form: FormGroup;
  providers: string[] = [];
  
  bookId!: number;
  loading = signal(false);
  searchTriggered = signal(false);

  private currentBook: Book | null = null;
  private currentSettings: AppSettings | null = null;

  @Input()
  set book(value: Book | null) {
    this.currentBook = value;
    this.syncFormFromState();
  }

  get book(): Book | null {
    return this.currentBook;
  }

  @Input() isActiveTab: boolean = false;

  readonly selectedFetchedMetadata = signal<BookMetadata | null>(null);
  detailLoading = signal(false);

  private formBuilder = inject(FormBuilder);
  private bookMetadataService = inject(BookMetadataService);
  private appSettingsService = inject(AppSettingsService);

  private subscription: Subscription = new Subscription();
  private cancelRequest$ = new Subject<void>();
  
  // Signals for state
  allFetchedMetadata: WritableSignal<BookMetadata[]> = signal([]);
  metadataByProvider = signal<Map<string, BookMetadata[]>>(new Map());
  providerCounts = signal<Map<string, number>>(new Map());
  providerLoading = signal<Map<string, boolean>>(new Map());
  providerCompletionStatus = signal<Map<string, boolean>>(new Map());
  selectedProviderFilters = signal<Set<string>>(new Set(['all']));

  private syncSettingsEffect = effect(() => {
    const settings = this.appSettingsService.appSettings();
    if (!settings) return;

    this.currentSettings = settings;
    const providerSettings = settings.metadataProviderSettings ?? {};
    this.providers = Object.entries(providerSettings)
      .filter(([, value]) => this.isEnabledProviderSetting(value) && value.enabled)
      .map(([key]) => key.charAt(0).toUpperCase() + key.slice(1));

    const currentProviders = this.form.get('provider')?.value || [];
    const validProviders = currentProviders.filter((p: string) => this.providers.includes(p));
    if (validProviders.length !== currentProviders.length) {
      this.form.patchValue({provider: validProviders.length > 0 ? validProviders : null});
    }

    this.syncFormFromState();
  });

  // Computed properties
  interleavedMetadata = computed(() => {
    const interleaved: BookMetadata[] = [];
    const byProvider = this.metadataByProvider();
    const providers = Array.from(byProvider.keys());

    if (providers.length === 0) return [];

    const maxLength = Math.max(
      ...Array.from(byProvider.values()).map(list => list.length)
    );

    for (let i = 0; i < maxLength; i++) {
      for (const provider of providers) {
        const providerList = byProvider.get(provider);
        if (providerList && i < providerList.length) {
          interleaved.push(providerList[i]);
        }
      }
    }

    return interleaved;
  });

  filteredMetadata = computed(() => {
    const all = this.interleavedMetadata();
    const filters = this.selectedProviderFilters();
    
    if (filters.has('all')) {
      return all;
    } else {
      return all.filter(metadata => {
        const provider = this.getProviderFromMetadata(metadata);
        return provider && filters.has(provider);
      });
    }
  });

  providerFilterOptions = computed(() => {
    const allCount = this.interleavedMetadata().length;
    const counts = this.providerCounts();
    
    return [
      {label: `All (${allCount})`, value: 'all'},
      ...Array.from(counts.entries())
        .filter(([, count]) => count > 0)
        .map(([provider, count]) => ({
          label: `${provider.charAt(0).toUpperCase() + provider.slice(1)} (${count})`,
          value: provider
        }))
    ];
  });

  private pendingAutoSearch = false;
  private providerInitialized = false;

  constructor() {
    this.form = this.formBuilder.group({
      provider: null,
      title: [''],
      author: [''],
      isbn: ['']
    });
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['isActiveTab']?.currentValue && this.pendingAutoSearch) {
      this.pendingAutoSearch = false;
      this.onSubmit();
    }
  }

  private isEnabledProviderSetting(value: unknown): value is { enabled: boolean } {
    return !!value && typeof value === 'object' && 'enabled' in value;
  }

  private syncFormFromState(): void {
    if (!this.currentBook || !this.currentSettings) {
      return;
    }

    const bookChanged = this.currentBook.id !== this.bookId;
    if (bookChanged) {
      this.resetFormFromBook(this.currentBook);
      if (this.currentSettings.autoBookSearch) {
        if (this.isActiveTab) {
          this.onSubmit();
        } else {
          this.pendingAutoSearch = true;
        }
      }
      return;
    }

    this.updateFormFromBook(this.currentBook);
  }

  private resetFormFromBook(book: Book): void {
    this.cancelRequest$.next();
    this.loading.set(false);
    this.detailLoading.set(false);
    this.selectedFetchedMetadata.set(null);
    this.allFetchedMetadata.set([]);
    
    this.providerCounts.set(new Map());
    this.providerLoading.set(new Map());
    this.providerCompletionStatus.set(new Map());
    this.metadataByProvider.set(new Map());
    
    this.selectedProviderFilters.set(new Set(['all']));
    this.bookId = book.id;

    const formUpdate: Record<'title' | 'author' | 'isbn', string> & {provider?: string[] | null} = {
      title: book.metadata?.title ?? '',
      author: book.metadata?.authors?.[0] ?? '',
      isbn: book.metadata?.isbn13 ?? book.metadata?.isbn10 ?? ''
    };

    if (!this.providerInitialized) {
      formUpdate['provider'] = this.providers;
      this.providerInitialized = true;
    }

    this.form.patchValue(formUpdate);
  }

  private updateFormFromBook(book: Book): void {
    this.form.patchValue({
      title: book.metadata?.title ?? '',
      author: book.metadata?.authors?.[0] ?? '',
      isbn: book.metadata?.isbn13 ?? book.metadata?.isbn10 ?? ''
    });
  }

  ngOnDestroy(): void {
    this.cancelRequest$.next();
    this.cancelRequest$.complete();
    this.subscription.unsubscribe();
  }

  get isSearchEnabled(): boolean {
    const providerSelected = !!this.form.get('provider')?.value;
    const title = this.form.get('title')?.value;
    const isbn = this.form.get('isbn')?.value;
    return providerSelected && (title || isbn);
  }

  onSubmit(): void {
    this.searchTriggered.set(true);
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

      this.loading.set(true);
      this.allFetchedMetadata.set([]);
      
      const initialCounts = new Map<string, number>();
      const initialLoading = new Map<string, boolean>();
      const initialCompletion = new Map<string, boolean>();
      const initialByProvider = new Map<string, BookMetadata[]>();
      
      providerKeys.forEach((provider: string) => {
        const providerLower = provider.toLowerCase();
        initialCounts.set(providerLower, 0);
        initialLoading.set(providerLower, true);
        initialCompletion.set(providerLower, false);
        initialByProvider.set(providerLower, []);
      });
      
      this.providerCounts.set(initialCounts);
      this.providerLoading.set(initialLoading);
      this.providerCompletionStatus.set(initialCompletion);
      this.metadataByProvider.set(initialByProvider);
      
      this.selectedProviderFilters.set(new Set(['all']));
      this.cancelRequest$.next();

      const activeProviders = new Set<string>(providerKeys.map((p: string) => p.toLowerCase()));

      this.bookMetadataService.fetchBookMetadata(fetchRequest.bookId, fetchRequest)
        .pipe(takeUntil(this.cancelRequest$))
        .subscribe({
          next: (metadata) => {
            const provider = this.getProviderFromMetadata(metadata);
            if (provider) {
              this.metadataByProvider.update(map => {
                const list = map.get(provider) || [];
                return new Map(map).set(provider, [...list, metadata]);
              });

              this.providerCounts.update(map => {
                const count = (this.metadataByProvider().get(provider)?.length) || 0;
                return new Map(map).set(provider, count);
              });

              if (!this.providerCompletionStatus().get(provider)) {
                this.providerLoading.update(map => new Map(map).set(provider, false));
                this.providerCompletionStatus.update(map => new Map(map).set(provider, true));
              }
            }
            
            this.allFetchedMetadata.update(all => [...all, metadata]);
          },
          error: (error) => {
            console.error('Error fetching metadata:', error);
            this.loading.set(false);
            this.providerLoading.set(new Map());
          },
          complete: () => {
            this.loading.set(false);
            activeProviders.forEach((provider: string) => {
              if (!this.providerCompletionStatus().get(provider)) {
                this.providerLoading.update(map => new Map(map).set(provider, false));
                this.providerCompletionStatus.update(map => new Map(map).set(provider, true));
              }
            });
          }
        });
    } else {
      console.warn('Form is invalid. Please fill in all required fields.');
    }
  }

  private getProviderFromMetadata(metadata: BookMetadata): string | null {
    if (metadata.audibleId) return 'audible'; 
    if (metadata.asin) return 'amazon';
    if (metadata.goodreadsId) return 'goodreads';
    if (metadata.googleId) return 'google';
    if (metadata.hardcoverId) return 'hardcover';
    if (metadata['doubanId']) return 'douban';
    if (metadata['lubimyczytacId']) return 'lubimyczytac';
    if (metadata.comicvineId) return 'comicvine';
    if (metadata.ranobedbId) return 'ranobedb';
    return metadata.provider?.toLowerCase() || null;
  }

  getProviderClass(metadata: BookMetadata): string {
    return this.getProviderFromMetadata(metadata) || 'unknown';
  }

  onProviderPillClick(provider: string, event: Event): void {
    const providerLower = provider.toLowerCase();

    const isModifierClick = (event instanceof MouseEvent || event instanceof KeyboardEvent) && (event.ctrlKey || event.metaKey);
    
    this.selectedProviderFilters.update(filters => {
      const newFilters = new Set(filters);
      if (isModifierClick) {
        if (newFilters.has(providerLower)) {
          newFilters.delete(providerLower);
        } else {
          newFilters.add(providerLower);
          newFilters.delete('all');
        }

        if (newFilters.size === 0) {
          newFilters.add('all');
        }
      } else {
        if (newFilters.has(providerLower) && newFilters.size === 1) {
          newFilters.clear();
          newFilters.add('all');
        } else {
          newFilters.clear();
          newFilters.add(providerLower);
        }
      }
      return newFilters;
    });
  }

  isProviderPillActive(provider: string): boolean {
    return this.selectedProviderFilters().has(provider.toLowerCase());
  }

  isProviderLoading(provider: string): boolean {
    return this.providerLoading().get(provider.toLowerCase()) ?? false;
  }

  getProviderTabs(): { provider: string; count: number }[] {
    return Array.from(this.providerCounts().entries()).map(([provider, count]) => ({
      provider: provider.charAt(0).toUpperCase() + provider.slice(1),
      count
    }));
  }

  onBookClick(fetchedMetadata: BookMetadata) {
    this.selectedFetchedMetadata.set(fetchedMetadata);

    const enrichment = this.getDetailEnrichmentInfo(fetchedMetadata);

    if (enrichment) {
      this.detailLoading.set(true);
      this.bookMetadataService.fetchMetadataDetail(enrichment.provider, enrichment.id)
        .pipe(takeUntil(this.cancelRequest$))
        .subscribe({
          next: (enriched) => {
            const current = this.selectedFetchedMetadata();
            const currentId = current && this.getProviderItemId(current, enrichment.provider);
            if (currentId === enrichment.id) {
              this.selectedFetchedMetadata.set(enriched);
            }
            this.detailLoading.set(false);
          },
          error: (err) => {
            console.error('Error fetching detailed metadata:', err);
            this.detailLoading.set(false);
          }
        });
    }
  }

  private getDetailEnrichmentInfo(metadata: BookMetadata): { provider: string; id: string } | null {
    if (metadata.comicvineId && (!metadata.comicMetadata
      || (!metadata.comicMetadata.pencillers?.length
        && !metadata.comicMetadata.inkers?.length
        && !metadata.comicMetadata.colorists?.length
        && !metadata.comicMetadata.letterers?.length
        && !metadata.comicMetadata.editors?.length
        && !metadata.comicMetadata.characters?.length))) {
      return {provider: 'Comicvine', id: metadata.comicvineId};
    }
    if (metadata.goodreadsId && !metadata.description) {
      return {provider: 'GoodReads', id: metadata.goodreadsId};
    }
    // Audible has to come before Amazon in this check as they both have ASIN
    if (metadata.audibleId && !metadata.description) {
      return {provider: 'Audible', id: metadata.audibleId};
    }
    if (metadata.asin && !metadata.description) {
      return {provider: 'Amazon', id: metadata.asin};
    }
    return null;
  }

  private getProviderItemId(metadata: BookMetadata, provider: string): string | undefined {
    switch (provider) {
      case 'Comicvine': return metadata.comicvineId;
      case 'GoodReads': return metadata.goodreadsId;
      case 'Amazon': return metadata.asin;
      case 'Audible': return metadata.audibleId;
      default: return undefined;
    }
  }

  onGoBack() {
    this.detailLoading.set(false);
    this.selectedFetchedMetadata.set(null);
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
    if (metadata.audibleId) {
      // Audible has to come before Amazon because they both have an ASIN.
      return `<a href="https://www.audible.com/pd/${metadata.audibleId}" target="_blank">Audible</a>`;
    } else if (metadata.asin) {
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
      if (metadata.externalUrl) {
        return `<a href="${metadata.externalUrl}" target="_blank">Comicvine</a>`;
      }
      return `<a href="https://comicvine.gamespot.com/4050-${metadata.comicvineId}/" target="_blank">Comicvine</a>`;
    } else if (metadata.ranobedbId) {
      return `<a href="https://ranobedb.org/book/${metadata.ranobedbId}" target="_blank">RanobeDB</a>`;
    } else if (metadata.externalUrl) {
      const providerName = metadata.provider || 'Link';
      return `<a href="${metadata.externalUrl}" target="_blank">${providerName}</a>`;
    }
    throw new Error("No provider ID found in metadata.");
  }

  trackByMetadata(index: number, metadata: BookMetadata): string {
    return metadata.googleId || metadata.goodreadsId || metadata.asin ||
      metadata.hardcoverId || metadata.comicvineId || metadata.audibleId || index.toString();
  }

  onProviderClick(event: Event) {
    const target = event.target as HTMLElement;
    if (target.tagName === 'A' || target.closest('a')) {
      event.stopPropagation();
    }
  }
}
