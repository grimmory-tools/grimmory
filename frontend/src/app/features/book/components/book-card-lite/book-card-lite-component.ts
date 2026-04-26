import {ChangeDetectionStrategy, Component, computed, inject, Input, input} from '@angular/core';
import {Book} from '../../model/book.model';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {CoverPlaceholderComponent} from '../../../../shared/components/cover-generator/cover-generator.component';
import {Button} from 'primeng/button';
import {UserService} from '../../../settings/user-management/user.service';
import {Router} from '@angular/router';
import {NgClass} from '@angular/common';
import {BookMetadataHostService} from '../../../../shared/service/book-metadata-host.service';
import {Tooltip} from 'primeng/tooltip';

@Component({
  selector: 'app-book-card-lite-component',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    Button,
    NgClass,
    Tooltip,
    CoverPlaceholderComponent
  ],
  templateUrl: './book-card-lite-component.html',
  styleUrl: './book-card-lite-component.scss'
})
export class BookCardLiteComponent {
  @Input() book!: Book;
  @Input() isActive: boolean = false;
  @Input() showSeriesNumber: boolean = false;
  readonly priorityImage = input(false);

  readonly imageLoading = computed(() => this.priorityImage() ? 'eager' : 'lazy');
  readonly imageFetchPriority = computed(() => this.priorityImage() ? 'high' : 'low');

  private router = inject(Router);
  protected urlHelper = inject(UrlHelperService);
  private userService = inject(UserService);
  private bookMetadataHostService = inject(BookMetadataHostService);

  private metadataCenterViewMode = computed(() =>
    this.userService.currentUser()?.userSettings.metadataCenterViewMode ?? 'route'
  );
  isHovered: boolean = false;

  isAudiobookOnly(): boolean {
    const primaryIsAudiobook = this.book.primaryFile?.bookType === 'AUDIOBOOK';
    const hasEbookAlternative = this.book.alternativeFormats?.some(f => f.bookType !== 'AUDIOBOOK') ?? false;
    return primaryIsAudiobook && !hasEbookAlternative;
  }

  getThumbnailUrl(): string | null {
    if (this.isAudiobookOnly()) {
      return this.urlHelper.getAudiobookThumbnailUrl(this.book.id, this.book.metadata?.audiobookCoverUpdatedOn);
    }
    return this.urlHelper.getThumbnailUrl(this.book.id, this.book.metadata?.coverUpdatedOn);
  }

  openBookInfo(book: Book): void {
    if (this.metadataCenterViewMode() === 'route') {
      this.router.navigate(['/book', book.id], {
        queryParams: {tab: 'view'}
      });
    } else {
      this.bookMetadataHostService.requestBookSwitch(book.id);
    }
  }
}
