import {Component, inject, Input, OnInit} from '@angular/core';
import {MessageService} from 'primeng/api';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators} from '@angular/forms';
import {BookCoverService, CoverFetchRequest, CoverImage} from '../../../../shared/services/book-cover.service';
import {finalize} from 'rxjs/operators';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {ProgressSpinner} from 'primeng/progressspinner';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {BookService} from '../../../book/service/book.service';
import {BookMetadataManageService} from '../../../book/service/book-metadata-manage.service';
import {Image} from 'primeng/image';
import {Tooltip} from 'primeng/tooltip';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-cover-search',
  templateUrl: './cover-search.component.html',
  imports: [
    Button,
    ReactiveFormsModule,
    FormsModule,
    InputText,
    ProgressSpinner,
    Image,
    Tooltip,
    TranslocoDirective
  ],
  styleUrls: ['./cover-search.component.scss']
})
export class CoverSearchComponent implements OnInit {
  @Input() bookId!: number;
  searchForm: FormGroup;
  coverImages: CoverImage[] = [];
  loading = false;
  hasSearched = false;
  coverType: 'ebook' | 'audiobook' = 'ebook';

  private fb = inject(FormBuilder);
  private bookCoverService = inject(BookCoverService);
  private dynamicDialogConfig = inject(DynamicDialogConfig);
  protected dynamicDialogRef = inject(DynamicDialogRef);
  protected bookService = inject(BookService);
  private bookMetadataManageService = inject(BookMetadataManageService);
  private messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);

  constructor() {
    this.searchForm = this.fb.group({
      title: ['', Validators.required],
      author: ['']
    });
  }

  ngOnInit() {
    this.bookId = this.dynamicDialogConfig.data.bookId;
    const book = this.bookService.getBookByIdFromState(this.bookId);

    // Use explicitly provided coverType, or auto-detect based on primary file
    if (this.dynamicDialogConfig.data.coverType) {
      this.coverType = this.dynamicDialogConfig.data.coverType;
    } else if (book?.primaryFile?.bookType === 'AUDIOBOOK') {
      this.coverType = 'audiobook';
    } else {
      this.coverType = 'ebook';
    }

    if (book) {
      this.searchForm.patchValue({
        title: book.metadata?.title || '',
        author: book.metadata?.authors && book.metadata?.authors.length > 0 ? book.metadata?.authors[0] : ''
      });

      if (this.searchForm.valid) {
        this.onSearch();
      }
    }
  }

  onSearch() {
    if (this.searchForm.valid) {
      this.loading = true;
      const request: CoverFetchRequest = {
        title: this.searchForm.value.title,
        author: this.searchForm.value.author,
        coverType: this.coverType
      };

      this.bookCoverService.fetchBookCovers(request)
        .pipe(finalize(() => this.loading = false))
        .subscribe({
          next: (images) => {
            this.coverImages = images.sort((a, b) => a.index - b.index);
            this.hasSearched = true;
          },
          error: (error) => {
            console.error('Error fetching covers:', error);
            this.coverImages = [];
            this.hasSearched = true;
          }
        });
    } else {
      console.log('Form invalid', {
        formErrors: this.searchForm.errors,
        titleErrors: this.searchForm.get('title')?.errors
      });
    }
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
    this.coverImages = [];
    this.hasSearched = false;
  }
}
