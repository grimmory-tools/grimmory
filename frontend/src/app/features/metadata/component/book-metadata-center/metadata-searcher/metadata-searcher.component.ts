import {Component, computed, effect, inject, input, linkedSignal, signal} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {FormBuilder, ReactiveFormsModule} from '@angular/forms';
import {Button} from '@openng/optimus-ui/button';
import {InputText} from '@openng/optimus-ui/inputtext';
import {MultiSelect} from '@openng/optimus-ui/multiselect';
import {Tooltip} from '@openng/optimus-ui/tooltip';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {injectQuery, QueryClient} from '@tanstack/angular-query-experimental';

import {Book} from '../../../../book/model/book.model';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {MetadataPickerComponent} from '../metadata-picker/metadata-picker.component';
import {CoverComponent} from '../../../../../shared/components/cover/cover.component';
import {MetadataCatalogService} from '../../../../../shared/metadata/metadata-catalog.service';
import type {MetadataProviderId} from '../../../../../shared/metadata/metadata-providers';
import {
  MetadataSourceQueryService,
  type MetadataSearchParams,
  type MetadataSearchResult,
} from '../../../sources/metadata-source-query.service';

const NO_SEARCH: MetadataSearchParams = {bookId: 0, providers: []};

@Component({
  selector: 'app-metadata-searcher',
  templateUrl: './metadata-searcher.component.html',
  styleUrls: ['./metadata-searcher.component.scss'],
  imports: [
    ReactiveFormsModule,
    Button,
    InputText,
    MetadataPickerComponent,
    MultiSelect,
    Tooltip,
    TranslocoDirective,
    TranslocoPipe,
    CoverComponent
  ],
  standalone: true
})
export class MetadataSearcherComponent {
  readonly book = input<Book | null>(null);
  readonly isActiveTab = input(false);

  private readonly formBuilder = inject(FormBuilder);
  private readonly appSettingsService = inject(AppSettingsService);
  private readonly sources = inject(MetadataSourceQueryService);
  private readonly catalog = inject(MetadataCatalogService);
  private readonly queryClient = inject(QueryClient);
  private readonly t = inject(TranslocoService);
  private readonly activeLang = toSignal(this.t.langChanges$, {initialValue: this.t.getActiveLang()});
  private readonly search = signal<MetadataSearchParams | null>(null);
  private resetForBookId: number | null = null;
  private readonly autoSearchPending = linkedSignal<number | undefined, boolean>({
    source: () => this.book()?.id,
    computation: bookId => bookId !== undefined
      && !!this.appSettingsService.appSettings()?.autoBookSearch,
  });
  private providersInitialised = false;
  private readonly query = injectQuery(() => ({
    ...this.sources.prospective(this.search() ?? NO_SEARCH),
    enabled: this.search() !== null,
  }));

  readonly form = this.formBuilder.group({
    providers: this.formBuilder.control<MetadataProviderId[] | null>(null),
    title: this.formBuilder.nonNullable.control(''),
    author: this.formBuilder.nonNullable.control(''),
    isbn: this.formBuilder.nonNullable.control('')
  });

  readonly results = computed(() => this.query.data() ?? []);
  readonly loading = this.query.isFetching;
  readonly failed = computed(() => this.query.isError());
  readonly hasSearched = computed(() => this.search() !== null);
  readonly selectedFilters = signal<Set<MetadataProviderId | 'all'>>(new Set(['all']));
  readonly selected = signal<MetadataSearchResult | null>(null);

  readonly providers = computed(() => this.sources.enabledProviders().map(provider => provider.id));
  readonly noProviders = computed(() => !this.sources.providersLoading() && this.providers().length === 0);

  readonly providerOptions = computed(() => this.sources.enabledProviders().map(provider => ({
    id: provider.id,
    label: this.t.translate(provider.labelKey, {}, this.activeLang()),
  })));

  readonly resultsByProvider = computed(() => {
    const groups = new Map<MetadataProviderId, MetadataSearchResult[]>();
    this.search()?.providers.forEach(provider => groups.set(provider, []));
    for (const result of this.results()) {
      groups.get(result.provider)?.push(result);
    }
    return groups;
  });

  readonly providerTabs = computed(() => Array.from(
    this.resultsByProvider(),
    ([provider, results]) => ({provider, count: results.length})
  ));

  private readonly interleavedResults = computed(() => {
    const lists = Array.from(this.resultsByProvider().values());
    const maxLength = Math.max(0, ...lists.map(list => list.length));
    const interleaved: MetadataSearchResult[] = [];
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
    return filters.has('all') ? all : all.filter(result => filters.has(result.provider));
  });

  constructor() {
    effect(() => {
      if (!this.appSettingsService.appSettings() || this.sources.providersLoading()) return;
      const providers = this.providers();
      const control = this.form.controls.providers;

      if (!this.providersInitialised) {
        this.providersInitialised = true;
        control.setValue(providers);
        return;
      }

      const current = control.value ?? [];
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
      if (!settings || book.id === this.resetForBookId) return;

      this.resetForBookId = book.id;
      this.resetForBook(book);
    });

    effect(() => {
      if (this.autoSearchPending() && this.isActiveTab() && !this.sources.providersLoading()) {
        this.autoSearchPending.set(false);
        this.onSubmit();
      }
    });
  }

  get isSearchEnabled(): boolean {
    const providerSelected = !!this.form.controls.providers.value?.length;
    const title = this.form.controls.title.value;
    const isbn = this.form.controls.isbn.value;
    return providerSelected && (title.length > 0 || isbn.length > 0);
  }

  onSubmit(): void {
    const selectedProviders = this.form.controls.providers.value;
    const bookId = this.book()?.id;
    if (!selectedProviders?.length || bookId === undefined) return;

    const params: MetadataSearchParams = {
      bookId,
      providers: selectedProviders,
      title: this.form.controls.title.value,
      author: this.form.controls.author.value,
      isbn: this.form.controls.isbn.value
    };

    this.selectedFilters.set(new Set(['all']));

    void this.queryClient.resetQueries({queryKey: this.sources.prospective(params).queryKey, exact: true});
    this.search.set(params);
  }

  onBookClick(result: MetadataSearchResult): void {
    this.selected.set(result);
  }

  onGoBack(): void {
    this.selected.set(null);
  }

  onProviderPillClick(provider: MetadataProviderId, event: Event): void {
    const isModifierClick = (event instanceof MouseEvent || event instanceof KeyboardEvent) && (event.ctrlKey || event.metaKey);

    this.selectedFilters.update(filters => {
      const next = new Set(filters);
      if (isModifierClick) {
        if (next.has(provider)) {
          next.delete(provider);
        } else {
          next.add(provider);
          next.delete('all');
        }
        if (next.size === 0) next.add('all');
      } else if (next.has(provider) && next.size === 1) {
        next.clear();
        next.add('all');
      } else {
        next.clear();
        next.add(provider);
      }
      return next;
    });
  }

  isProviderPillActive(provider: MetadataProviderId): boolean {
    return this.selectedFilters().has(provider);
  }

  providerClass(id: MetadataProviderId): string {
    return id.toLowerCase();
  }

  providerLabelKey(id: MetadataProviderId): string {
    return this.catalog.provider(id)?.labelKey ?? id;
  }

  sanitizeHtml(htmlString: string): string {
    return htmlString.replace(/<\/?[^>]+(>|$)/g, '').trim();
  }

  truncateText(text: string | undefined, length: number): string {
    const safeText = text ?? '';
    return safeText.length > length ? safeText.substring(0, length) + '...' : safeText;
  }

  private resetSearchState(): void {
    this.search.set(null);
    this.selected.set(null);
    this.selectedFilters.set(new Set(['all']));
  }

  private clearForNoBook(): void {
    this.resetForBookId = null;
    this.autoSearchPending.set(false);
    this.resetSearchState();
    this.form.patchValue({title: '', author: '', isbn: ''});
  }

  private resetForBook(book: Book): void {
    this.resetSearchState();
    this.form.patchValue({
      title: book.metadata?.title ?? '',
      author: book.metadata?.authors?.[0] ?? '',
      isbn: book.metadata?.isbn13 ?? book.metadata?.isbn10 ?? ''
    });
  }
}
