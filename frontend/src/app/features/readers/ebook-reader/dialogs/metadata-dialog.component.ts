import {Component, EventEmitter, inject, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslocoDirective} from '@jsverse/transloco';
import {TranslocoService} from '@jsverse/transloco';
import {Book} from '../../../book/model/book.model';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';

@Component({
  selector: 'app-reader-book-metadata-dialog',
  standalone: true,
  imports: [CommonModule, TranslocoDirective],
  templateUrl: './metadata-dialog.component.html',
  styleUrls: ['./metadata-dialog.component.scss']
})
export class ReaderBookMetadataDialogComponent {
  @Input() book: Book | null = null;
  @Output() close = new EventEmitter<void>();

  private urlHelperService = inject(UrlHelperService);
  private readonly t = inject(TranslocoService);

  get metadata() {
    return this.book?.metadata;
  }

  get bookCoverUrl(): string | null {
    if (!this.book?.id) return null;
    const coverUpdatedOn = this.book.metadata?.coverUpdatedOn;
    return this.urlHelperService.getCoverUrl(this.book.id, coverUpdatedOn);
  }

  formatDate(date: string | undefined): string {
    if (!date) return this.t.translate('readerEbook.metadataDialog.na');
    try {
      return new Date(date).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'long',
        day: 'numeric'
      });
    } catch {
      return date;
    }
  }

  formatAuthors(authors: string[] | undefined): string {
    if (!authors || authors.length === 0) return this.t.translate('readerEbook.metadataDialog.unknown');
    return authors.join(', ');
  }

  formatFileSize(sizeKb: number | undefined): string {
    if (!sizeKb) return this.t.translate('readerEbook.metadataDialog.na');
    if (sizeKb < 1024) return `${sizeKb.toFixed(1)} KB`;
    return `${(sizeKb / 1024).toFixed(2)} MB`;
  }
}
