import {Component, EventEmitter, inject, Input, Output} from '@angular/core';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {Book} from '../../../book/model/book.model';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {LanguageResolverService} from '../../../../shared/service/language-resolver.service';
import {CoverComponent} from '../../../../shared/components/cover/cover.component';
import {MetadataCatalogService} from '../../../../shared/metadata/metadata-catalog.service';

@Component({
  selector: 'app-reader-book-metadata-dialog',
  standalone: true,
  imports: [TranslocoDirective, TranslocoPipe, CoverComponent],
  templateUrl: './metadata-dialog.component.html',
  styleUrls: ['./metadata-dialog.component.scss']
})
export class ReaderBookMetadataDialogComponent {
  @Input() book: Book | null = null;
  @Output() closed = new EventEmitter<void>();

  private urlHelperService = inject(UrlHelperService);
  private readonly t = inject(TranslocoService);
  protected readonly catalog = inject(MetadataCatalogService);
  protected readonly languageResolver = inject(LanguageResolverService);

  get metadata() {
    return this.book?.metadata;
  }

  get providerRatings() {
    const metadata = this.metadata;
    return this.catalog.providers().flatMap(provider => {
      const rating = provider.book?.rating ? metadata?.[provider.book.rating] : undefined;
      if (typeof rating !== 'number') return [];
      const reviewCount = provider.book?.reviewCount ? metadata?.[provider.book.reviewCount] : undefined;
      return [{labelKey: provider.labelKey, rating, reviewCount}];
    });
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
