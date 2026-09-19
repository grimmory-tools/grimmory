import {Component, computed, inject, signal} from '@angular/core';
import {MessageService} from '@openng/optimus-ui/api';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {injectQuery, QueryClient} from '@tanstack/angular-query-experimental';
import {Button} from '@openng/optimus-ui/button';
import {InputText} from '@openng/optimus-ui/inputtext';
import {ProgressSpinner} from '@openng/optimus-ui/progressspinner';
import {DynamicDialogConfig, DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {BookService} from '../../../book/service/book.service';
import {BookMetadataManageService} from '../../../book/service/book-metadata-manage.service';
import {CoverImage, CoverSearchParams, MetadataSourceQueryService} from '../../sources/metadata-source-query.service';
import {Image} from '@openng/optimus-ui/image';
import {Tooltip} from '@openng/optimus-ui/tooltip';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

const NO_SEARCH: CoverSearchParams = {bookId: 0, coverType: 'ebook'};

@Component({
  selector: 'app-cover-search',
  templateUrl: './cover-search.component.html',
  imports: [
    Button,
    ReactiveFormsModule,
    InputText,
    ProgressSpinner,
    Image,
    Tooltip,
    TranslocoDirective
  ],
  styleUrls: ['./cover-search.component.scss']
})
export class CoverSearchComponent {
  private readonly fb = inject(FormBuilder);
  private readonly dynamicDialogConfig = inject(DynamicDialogConfig);
  protected readonly dynamicDialogRef = inject(DynamicDialogRef);
  private readonly bookService = inject(BookService);
  private readonly bookMetadataManageService = inject(BookMetadataManageService);
  private readonly sources = inject(MetadataSourceQueryService);
  private readonly queryClient = inject(QueryClient);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);

  private readonly bookId: number = this.dynamicDialogConfig.data.bookId;
  private readonly book = this.bookService.findBookById(this.bookId);
  readonly searchForm = this.fb.nonNullable.group({
    title: ['', Validators.required],
    author: ['']
  });
  readonly coverType: 'ebook' | 'audiobook' = this.dynamicDialogConfig.data.coverType ??
    (this.book?.primaryFile?.bookType === 'AUDIOBOOK' ? 'audiobook' : 'ebook');
  private readonly search = signal<CoverSearchParams | null>(null);
  private readonly query = injectQuery(() => ({
    ...this.sources.coverSearch(this.search() ?? NO_SEARCH),
    enabled: this.search() !== null,
  }));

  readonly coverImages = computed(() => [...(this.query.data() ?? [])].sort((a, b) => a.index - b.index));
  readonly loading = this.query.isFetching;
  readonly failed = computed(() => this.query.isError());
  readonly hasSearched = computed(() => this.search() !== null);

  constructor() {
    if (this.book) {
      this.searchForm.patchValue({
        title: this.book.metadata?.title || '',
        author: this.book.metadata?.authors?.[0] ?? ''
      });

      if (this.searchForm.valid) {
        this.onSearch();
      }
    }
  }

  onSearch() {
    if (!this.searchForm.valid) return;

    const params: CoverSearchParams = {
      bookId: this.bookId,
      title: this.searchForm.value.title,
      author: this.searchForm.value.author,
      coverType: this.coverType,
    };
    void this.queryClient.resetQueries({queryKey: this.sources.coverSearch(params).queryKey, exact: true});
    this.search.set(params);
  }

  selectAndSave(image: CoverImage) {
    const uploadObservable = this.coverType === 'audiobook'
      ? this.bookMetadataManageService.uploadAudiobookCoverFromUrl(this.bookId, image.url)
      : this.bookMetadataManageService.uploadCoverFromUrl(this.bookId, image.url);

    uploadObservable.subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('metadata.coverSearch.toast.coverUpdatedSummary'),
          detail: this.coverType === 'audiobook'
            ? this.t.translate('metadata.coverSearch.toast.audiobookCoverUpdatedDetail')
            : this.t.translate('metadata.coverSearch.toast.ebookCoverUpdatedDetail')
        });
        this.dynamicDialogRef.close(true);
      },
      error: err => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('metadata.coverSearch.toast.coverUpdateFailedSummary'),
          detail: err?.message || this.t.translate('metadata.coverSearch.toast.coverUpdateFailedDetail')
        });
      }
    });
  }

  onClear() {
    this.searchForm.reset();
    this.search.set(null);
  }
}
