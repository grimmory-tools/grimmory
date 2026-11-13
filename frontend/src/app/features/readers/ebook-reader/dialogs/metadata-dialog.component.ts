import {Component, inject, Input, output} from '@angular/core';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {Book} from '../../../book/model/book.model';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {LanguageResolverService} from '../../../../shared/service/language-resolver.service';
import {CoverComponent} from '../../../../shared/components/cover/cover.component';
import {MetadataCatalogService} from '../../../../shared/metadata/metadata-catalog.service';
import {METADATA_PROVIDER_SCORE_FIELDS} from '../../../../shared/metadata/metadata-provider-fields';

@Component({
  selector: 'app-reader-book-metadata-dialog',
  standalone: true,
  imports: [TranslocoDirective, TranslocoPipe, CoverComponent],
  templateUrl: './metadata-dialog.component.html',
  styleUrls: ['./metadata-dialog.component.scss']
})
export class ReaderBookMetadataDialogComponent {
  private readonly urlHelperService = inject(UrlHelperService);
  private readonly t = inject(TranslocoService);
  protected readonly catalog = inject(MetadataCatalogService);
  protected readonly languageResolver = inject(LanguageResolverService);
  @Input() book: Book | null = null;
  readonly closed = output<void>();

  get metadata() {
    return this.book?.metadata;
  }

  get providerRatings() {
    const metadata = this.metadata;
    return this.catalog.providers().flatMap(provider => {
      const score = (role: 'rating' | 'reviewCount') => {
        const field = METADATA_PROVIDER_SCORE_FIELDS.find(candidate => candidate.provider === provider.id && candidate.role === role);
        return field ? metadata?.[field.name] : undefined;
      };
      const rating = score('rating');
      if (typeof rating !== 'number') return [];
      return [{labelKey: provider.labelKey, rating, reviewCount: score('reviewCount')}];
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
