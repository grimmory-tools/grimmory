import {
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {TranslocoDirective, TranslocoPipe} from '@jsverse/transloco';
import {AutoComplete} from '@openng/optimus-ui/autocomplete';
import {Button} from '@openng/optimus-ui/button';
import {Checkbox} from '@openng/optimus-ui/checkbox';
import {DynamicDialogConfig, DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {injectMutation, injectQuery} from '@tanstack/angular-query-experimental';

import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {debouncedSignal} from '../../../../shared/util/debounced-signal';
import {SEARCH_DEBOUNCE_MS} from '../../../../shared/util/search-terms';
import {BookCommandService} from '../../data/book-command.service';
import {injectBookActionFeedback} from '../../service/book-action-feedback';
import {DEFAULT_BOOK_SORT_TERMS} from '../../data/book-query-params';
import {BookSummary} from '../../data/book-response.models';
import {BookQueryService} from '../../data/book-query.service';
import {legacyBookCachePatches, withLegacyBookCache} from '../../service/book-command-legacy-adapter';

const SEARCH_PAGE_SIZE = 100;

export interface BookFileAttacherSourceBook {
  id: number;
  libraryId: number;
  metadata?: {title?: string; authors?: string[]};
  primaryFile?: {extension?: string; bookType?: string; fileName?: string};
}

@Component({
  selector: 'app-book-file-attacher',
  imports: [
    FormsModule,
    AutoComplete,
    Button,
    Checkbox,
    TranslocoDirective,
    TranslocoPipe,
  ],
  templateUrl: './book-file-attacher.component.html',
  styleUrl: './book-file-attacher.component.scss'
})
export class BookFileAttacherComponent {
  private readonly dialogRef = inject(DynamicDialogRef);
  private readonly config = inject(DynamicDialogConfig);
  private readonly bookQueryService = inject(BookQueryService);
  private readonly bookCommands = inject(BookCommandService);
  private readonly feedback = injectBookActionFeedback();

  readonly sourceBooks: readonly BookFileAttacherSourceBook[] = this.config.data.sourceBook
    ? [this.config.data.sourceBook]
    : this.config.data.sourceBooks;
  readonly isBulkMode = this.sourceBooks.length > 1;
  private readonly sourceBookIds = this.sourceBooks.map(book => book.id);

  readonly targetInputValue = signal<BookSummary | string | null>(null);
  readonly moveFiles = signal(
    inject(AppSettingsService).appSettings()?.metadataPersistenceSettings?.moveFilesToLibraryPattern ?? false
  );
  private readonly searchTerm = signal('');
  // Debounce here: Optimus's delay leaves a pending search alive when its Clear icon is clicked.
  private readonly queryTerm = debouncedSignal(this.searchTerm, SEARCH_DEBOUNCE_MS);
  private readonly autocompleteSearchSequence = signal(0);

  readonly targetsQuery = injectQuery(() => this.bookQueryService.page({
    query: this.queryTerm() || undefined,
    facets: {library: [`${this.sourceBooks[0].libraryId}`]},
    facetLogic: 'and',
    sort: DEFAULT_BOOK_SORT_TERMS,
    size: SEARCH_PAGE_SIZE,
  }));

  readonly attachMutation = injectMutation(() => withLegacyBookCache(
    this.bookCommands.attachBookFiles(), legacyBookCachePatches.attachBookFiles));

  readonly isSearching = computed(() =>
    this.searchTerm() !== this.queryTerm()
      || this.targetsQuery.isPending()
      || this.targetsQuery.isFetching()
  );
  readonly filteredBooks = computed(() => {
    // Optimus completes each search when its suggestions input receives a new array.
    this.autocompleteSearchSequence();
    if (this.isSearching() || this.targetsQuery.isError()) return [];
    return (this.targetsQuery.data()?.content ?? []).filter(book => !this.sourceBookIds.includes(book.id));
  });

  readonly canAttach = computed(() => {
    const target = this.targetInputValue();
    return !!target && typeof target !== 'string' && !this.attachMutation.isPending();
  });

  onSearchInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.targetInputValue.set(value || null);
    this.searchTerm.set(value.trim());
  }

  filterBooks(event: {query: string}): void {
    const query = event.query.trim();
    const retriesFailedSearch = query === this.queryTerm()
      && this.targetsQuery.isError()
      && !this.targetsQuery.isFetching();

    this.searchTerm.set(query);
    this.autocompleteSearchSequence.update(sequence => sequence + 1);

    if (retriesFailedSearch) {
      void this.targetsQuery.refetch();
    }
  }

  onBookClear(): void {
    this.targetInputValue.set(null);
    this.searchTerm.set('');
    this.autocompleteSearchSequence.update(sequence => sequence + 1);
  }

  attach(): void {
    const target = this.targetInputValue();
    if (!target || typeof target === 'string') return;

    this.attachMutation.mutate({
      targetBookId: target.id,
      sourceBookIds: this.sourceBookIds,
      moveFiles: this.moveFiles(),
    }, {
      onSuccess: (_data, variables) => {
        this.feedback.show('success', 'book.bookService.toast.filesAttached', {count: variables.sourceBookIds.length});
        this.dialogRef.close({success: true});
      },
      onError: error => this.feedback.error('book.bookService.toast.attachmentFailed', error),
    });
  }

  closeDialog(): void {
    this.dialogRef.close();
  }
}
