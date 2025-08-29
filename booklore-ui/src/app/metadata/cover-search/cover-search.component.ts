import {Component, inject, Input, OnInit} from '@angular/core';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators} from '@angular/forms';
import {BookCoverService, CoverFetchRequest, CoverImage} from '../../shared/services/book-cover.service';
import {finalize} from 'rxjs/operators';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {ProgressSpinner} from 'primeng/progressspinner';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {BookService} from '../../book/service/book.service';
import {Image} from 'primeng/image';

@Component({
  selector: 'app-cover-search',
  templateUrl: './cover-search.component.html',
  imports: [
    Button,
    ReactiveFormsModule,
    FormsModule,
    InputText,
    ProgressSpinner,
    Image
  ],
  styleUrls: ['./cover-search.component.scss']
})
export class CoverSearchComponent implements OnInit {
  @Input() bookId!: number;
  searchForm: FormGroup;
  coverImages: CoverImage[] = [];
  loading = false;
  hasSearched = false;
  showPreview = false;
  previewImageUrl = '';

  private fb = inject(FormBuilder);
  private bookCoverService = inject(BookCoverService);
  private dynamicDialogConfig = inject(DynamicDialogConfig);
  protected dynamicDialogRef = inject(DynamicDialogRef);
  protected bookService = inject(BookService);

  constructor() {
    this.searchForm = this.fb.group({
      title: ['', Validators.required],
      author: ['']
    });
  }

  ngOnInit() {
    this.bookId = this.dynamicDialogConfig.data.bookId;
    let book = this.bookService.getBookByIdFromState(this.bookId);

    if (book) {
      this.searchForm.patchValue({
        title: book.metadata?.title || '',
        author: book.metadata?.authors && book.metadata?.authors.length > 0 ? book.metadata?.authors[0] : ''
      });
    }
  }

  onSearch() {
    if (this.searchForm.valid) {
      this.loading = true;
      const request: CoverFetchRequest = {
        title: this.searchForm.value.title,
        author: this.searchForm.value.author
      };

      this.bookCoverService.fetchBookCovers(request)
        .pipe(finalize(() => this.loading = false))
        .subscribe({
          next: (images) => {
            console.log('API response received:', images);
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
    this.dynamicDialogRef.close(image.url);
  }

  previewImage(imageUrl: string) {
    this.previewImageUrl = imageUrl;
    this.showPreview = true;
    document.body.style.overflow = 'hidden';
  }

  closePreview() {
    this.showPreview = false;
    this.previewImageUrl = '';
    document.body.style.overflow = 'auto';
  }

  onImageError(event: Event) {
    const target = event.target as HTMLImageElement;
    if (target) {
      target.style.display = 'none';
    }
  }

  onClear() {
    this.searchForm.reset();
    this.coverImages = [];
    this.hasSearched = false;
  }
}
