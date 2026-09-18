import {Component, computed, effect, inject, input, OnDestroy, signal} from '@angular/core';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule} from '@angular/forms';
import {Button} from '@openng/optimus-ui/button';
import {InputText} from '@openng/optimus-ui/inputtext';
import {MultiSelect} from '@openng/optimus-ui/multiselect';
import {Tooltip} from '@openng/optimus-ui/tooltip';
import {TranslocoDirective} from '@jsverse/transloco';
import {Subject, takeUntil} from 'rxjs';

import {FetchMetadataRequest} from '../../../model/request/fetch-metadata-request.model';
import {Book, BookMetadata} from '../../../../book/model/book.model';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {BookMetadataService} from '../../../../book/service/book-metadata.service';
import {MetadataPickerComponent} from '../metadata-picker/metadata-picker.component';
import {CoverComponent} from '../../../../../shared/components/cover/cover.component';

const DETAIL_ID_FIELD: Record<string, keyof BookMetadata> = {
  GoodReads: 'goodreadsId',
  Amazon: 'asin',
  Audible: 'audibleId',
  Comicvine: 'comicvineId',
};

function providerKey(result: BookMetadata): string {
  return result.provider?.toLowerCase() ?? 'unknown';
}

function capitalize(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1);
}

function needsDetail(result: BookMetadata): boolean {
  if (result.provider === 'Comicvine') {
    const comic = result.comicMetadata;
    return !comic || !(
      comic.pencillers?.length || comic.inkers?.length || comic.colorists?.length ||
      comic.letterers?.length || comic.editors?.length || comic.characters?.length
    );
  }
  return !result.description;
}

export function detailRequestFor(result: BookMetadata): { provider: string; id: string } | null {
  const provider = result.provider;
  const idField = provider ? DETAIL_ID_FIELD[provider] : undefined;
  const id = idField ? result[idField] : undefined;
  if (!provider || typeof id !== 'string' || !id || !needsDetail(result)) {
    return null;
  }
  return {provider, id};
}

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
    TranslocoDirective,
    CoverComponent
  ],
  standalone: true
})
export class MetadataSearcherComponent implements OnDestroy {
  readonly book = input<Book | null>(null);
  readonly isActiveTab = input(false);

  private readonly formBuilder = inject(FormBuilder);
  private readonly bookMetadataService = inject(BookMetadataService);
  private readonly appSettingsService = inject(AppSettingsService);

  readonly form: FormGroup = this.formBuilder.group({
    provider: null,
    title: [''],
    author: [''],
    isbn: ['']
  });

  readonly results = signal<BookMetadata[]>([]);
  readonly searchedProviders = signal<string[]>([]);
  readonly loading = signal(false);
  readonly searchTriggered = signal(false);
  readonly selectedFilters = signal<Set<string>>(new Set(['all']));
  readonly selected = signal<BookMetadata | null>(null);
  readonly detailLoading = signal(false);

  readonly providers = computed(() => {
    const providerSettings = this.appSettingsService.appSettings()?.metadataProviderSettings ?? {};
    return Object.entries(providerSettings)
      .filter(([, value]) => this.isEnabledProviderSetting(value) && value.enabled)
      .map(([key]) => capitalize(key));
  });

  readonly resultsByProvider = computed(() => {
    const groups = new Map<string, BookMetadata[]>();
    this.searchedProviders().forEach(provider => groups.set(provider.toLowerCase(), []));
    for (const result of this.results()) {
      const key = providerKey(result);
      groups.set(key, [...(groups.get(key) ?? []), result]);
    }
    return groups;
  });

  readonly providerTabs = computed(() =>
    this.searchedProviders().map(provider => ({
      provider,
      count: this.resultsByProvider().get(provider.toLowerCase())?.length ?? 0
    }))
  );

  readonly interleavedResults = computed(() => {
    const lists = Array.from(this.resultsByProvider().values());
    const maxLength = Math.max(0, ...lists.map(list => list.length));
    const interleaved: BookMetadata[] = [];
    for (let i = 0; i < maxLength; i++) {
      for (const list of lists) {
        if (i < list.length) interleaved.push(list[i]);
      }
    }
    return interleaved;
  });

  readonly filteredResults = computed(() => {
    const filters = this.selectedFilters();
    const all = this.interleavedResults();
    return filters.has('all') ? all : all.filter(result => filters.has(providerKey(result)));
  });

  private bookId: number | null = null;
  private readonly autoSearchPending = signal(false);
  private providersInitialised = false;
  private readonly cancel$ = new Subject<void>();

  constructor() {
    effect(() => {
      if (!this.appSettingsService.appSettings()) return;
      const providers = this.providers();
      const control = this.form.get('provider')!;

      if (!this.providersInitialised) {
        this.providersInitialised = true;
        control.setValue(providers);
        return;
      }

      const current: string[] = control.value ?? [];
      const valid = current.filter(provider => providers.includes(provider));
      if (valid.length !== current.length) {
        control.setValue(valid.length > 0 ? valid : null);
      }
    });

    effect(() => {
      const book = this.book();
      if (!book) {
        this.clearForNoBook();
        return;
      }

      const settings = this.appSettingsService.appSettings();
      if (!settings || book.id === this.bookId) return;

      this.bookId = book.id;
      this.resetForBook(book);
      this.autoSearchPending.set(!!settings.autoBookSearch);
    });

    effect(() => {
      if (this.autoSearchPending() && this.isActiveTab()) {
        this.autoSearchPending.set(false);
        this.onSubmit();
      }
    });
  }

  ngOnDestroy(): void {
    this.cancel$.next();
    this.cancel$.complete();
  }

  get isSearchEnabled(): boolean {
    const providerSelected = !!this.form.get('provider')?.value;
    const title = this.form.get('title')?.value;
    const isbn = this.form.get('isbn')?.value;
    return providerSelected && (title || isbn);
  }

  onSubmit(): void {
    this.searchTriggered.set(true);
    const selectedProviders: string[] | null = this.form.get('provider')?.value;
    if (!selectedProviders?.length || this.bookId === null) return;

    const request: FetchMetadataRequest = {
      bookId: this.bookId,
      providers: selectedProviders,
      title: this.form.get('title')?.value,
      author: this.form.get('author')?.value,
      isbn: this.form.get('isbn')?.value
    };

    this.cancel$.next();
    this.results.set([]);
    this.searchedProviders.set(selectedProviders);
    this.selectedFilters.set(new Set(['all']));
    this.loading.set(true);

    this.bookMetadataService.fetchBookMetadata(request.bookId, request)
      .pipe(takeUntil(this.cancel$))
      .subscribe({
        next: result => this.results.update(all => [...all, result]),
        error: error => {
          console.error('Error fetching metadata:', error);
          this.loading.set(false);
        },
        complete: () => this.loading.set(false)
      });
  }

  onBookClick(result: BookMetadata): void {
    this.selected.set(result);

    const detail = detailRequestFor(result);
    this.detailLoading.set(!!detail);
    if (!detail) return;

    this.bookMetadataService.fetchMetadataDetail(detail.provider, detail.id)
      .pipe(takeUntil(this.cancel$))
      .subscribe({
        next: enriched => {
          if (this.selected() !== result) return;
          this.selected.set(enriched);
          this.detailLoading.set(false);
        },
        error: error => {
          console.error('Error fetching detailed metadata:', error);
          if (this.selected() === result) this.detailLoading.set(false);
        }
      });
  }

  onGoBack(): void {
    this.detailLoading.set(false);
    this.selected.set(null);
  }

  onProviderPillClick(provider: string, event: Event): void {
    const key = provider.toLowerCase();
    const isModifierClick = (event instanceof MouseEvent || event instanceof KeyboardEvent) && (event.ctrlKey || event.metaKey);

    this.selectedFilters.update(filters => {
      const next = new Set(filters);
      if (isModifierClick) {
        if (next.has(key)) {
          next.delete(key);
        } else {
          next.add(key);
          next.delete('all');
        }
        if (next.size === 0) next.add('all');
      } else if (next.has(key) && next.size === 1) {
        next.clear();
        next.add('all');
      } else {
        next.clear();
        next.add(key);
      }
      return next;
    });
  }

  isProviderPillActive(provider: string): boolean {
    return this.selectedFilters().has(provider.toLowerCase());
  }

  providerClass(result: BookMetadata): string {
    return providerKey(result);
  }

  providerName(result: BookMetadata): string | null {
    return result.provider ?? null;
  }

  providerHref(result: BookMetadata): string | null {
    if (!result.externalUrl) {
      return null;
    }

    return result.externalUrl;
  }

  onProviderClick(event: Event): void {
    const target = event.target as HTMLElement;
    if (target.tagName === 'A' || target.closest('a')) {
      event.stopPropagation();
    }
  }

  sanitizeHtml(htmlString: string | null | undefined): string {
    if (!htmlString) return '';
    return htmlString.replace(/<\/?[^>]+(>|$)/g, '').trim();
  }

  truncateText(text: string | null, length: number): string {
    const safeText = text ?? '';
    return safeText.length > length ? safeText.substring(0, length) + '...' : safeText;
  }

  private isEnabledProviderSetting(value: unknown): value is { enabled: boolean } {
    return !!value && typeof value === 'object' && 'enabled' in value && typeof value.enabled == 'boolean';
  }

  private resetSearchState(): void {
    this.cancel$.next();
    this.loading.set(false);
    this.detailLoading.set(false);
    this.searchTriggered.set(false);
    this.selected.set(null);
    this.results.set([]);
    this.searchedProviders.set([]);
    this.selectedFilters.set(new Set(['all']));
  }

  private clearForNoBook(): void {
    this.bookId = null;
    this.autoSearchPending.set(false);
    this.resetSearchState();
    this.form.patchValue({title: '', author: '', isbn: ''});
  }

  private resetForBook(book: Book): void {
    this.resetSearchState();
    this.patchFormFromBook(book);
  }

  private patchFormFromBook(book: Book): void {
    this.form.patchValue({
      title: book.metadata?.title ?? '',
      author: book.metadata?.authors?.[0] ?? '',
      isbn: book.metadata?.isbn13 ?? book.metadata?.isbn10 ?? ''
    });
  }
}
