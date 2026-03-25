import {Component, inject, OnInit} from '@angular/core';
import {Button} from 'primeng/button';
import {FormsModule} from '@angular/forms';

import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {MessageService} from 'primeng/api';
import {BookMetadataManageService} from '../../../service/book-metadata-manage.service';
import {Divider} from 'primeng/divider';
import {LoadingService} from '../../../../../core/services/loading.service';
import {finalize} from 'rxjs';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-lock-unlock-metadata-dialog',
  standalone: true,
  imports: [
    Button,
    FormsModule,
    Divider,
    TranslocoDirective
],
  templateUrl: './lock-unlock-metadata-dialog.component.html',
  styleUrl: './lock-unlock-metadata-dialog.component.scss'
})
export class LockUnlockMetadataDialogComponent implements OnInit {
  private bookMetadataManageService = inject(BookMetadataManageService);
  private dynamicDialogConfig = inject(DynamicDialogConfig);
  dialogRef = inject(DynamicDialogRef);
  private messageService = inject(MessageService);
  private loadingService = inject(LoadingService);
  private readonly t = inject(TranslocoService);
  fieldLocks: Record<string, boolean | undefined> = {};

  bookIds: Set<number> = this.dynamicDialogConfig.data.bookIds;

  lockableFields: string[] = [
    'titleLocked', 'subtitleLocked', 'publisherLocked', 'publishedDateLocked', 'descriptionLocked',
    'isbn13Locked', 'isbn10Locked', 'asinLocked', 'pageCountLocked', 'thumbnailLocked', 'languageLocked', 'coverLocked',
    'seriesNameLocked', 'seriesNumberLocked', 'seriesTotalLocked', 'authorsLocked', 'categoriesLocked', 'moodsLocked', 'tagsLocked',
    'amazonRatingLocked', 'amazonReviewCountLocked', 'goodreadsRatingLocked', 'goodreadsReviewCountLocked',
    'hardcoverRatingLocked', 'hardcoverReviewCountLocked', 'goodreadsIdLocked', 'hardcoverIdLocked', 'hardcoverBookIdLocked', 'googleIdLocked', 'comicvineIdLocked',
    'ranobedbIdLocked', 'ranobedbRatingLocked'
  ];

  fieldLabels: Record<string, string> = {
    titleLocked: 'Title',
    subtitleLocked: 'Subtitle',
    publisherLocked: 'Publisher',
    publishedDateLocked: 'Date',
    descriptionLocked: 'Description',
    isbn13Locked: 'ISBN-13',
    isbn10Locked: 'ISBN-10',
    asinLocked: 'ASIN',
    pageCountLocked: 'Page Count',
    thumbnailLocked: 'Thumbnail',
    languageLocked: 'Language',
    coverLocked: 'Cover',
    seriesNameLocked: 'Series',
    seriesNumberLocked: 'Series #',
    seriesTotalLocked: 'Series Total #',
    authorsLocked: 'Authors',
    categoriesLocked: 'Genres',
    moodsLocked: 'Moods',
    tagsLocked: 'Tags',
    amazonRatingLocked: 'Amazon ★',
    amazonReviewCountLocked: 'Amazon Reviews',
    goodreadsRatingLocked: 'Goodreads ★',
    goodreadsReviewCountLocked: 'Goodreads Reviews',
    hardcoverRatingLocked: 'Hardcover ★',
    hardcoverReviewCountLocked: 'Hardcover Reviews',
    goodreadsIdLocked: 'Goodreads ID',
    hardcoverIdLocked: 'Hardcover ID',
    hardcoverBookIdLocked: 'Hardcover Book ID',
    googleIdLocked: 'Google ID',
    comicvineIdLocked: 'Comicvine ID',
    ranobedbIdLocked: 'Ranobedb ID',
    ranobedbRatingLocked: 'Ranobedb ★',
  };

  isSaving = false;

  ngOnInit(): void {
    this.lockableFields.forEach(field => this.fieldLocks[field] = undefined);
  }

  toggleLockAll(action: 'LOCK' | 'UNLOCK'): void {
    const lockState = action === 'LOCK' ? true : false;
    this.lockableFields.forEach(field => {
      this.fieldLocks[field] = lockState;
    });
  }

  getLockLabel(field: string): string {
    const state = this.fieldLocks[field];
    if (state === undefined) return this.t.translate('book.lockUnlockDialog.unselected');
    return state ? this.t.translate('book.lockUnlockDialog.locked') : this.t.translate('book.lockUnlockDialog.unlocked');
  }

  getLockIcon(field: string): string {
    const state = this.fieldLocks[field];
    return state === undefined ? '' : state ? 'pi pi-lock' : 'pi pi-lock-open';
  }

  resetFieldLocks(): void {
    this.lockableFields.forEach(field => {
      this.fieldLocks[field] = undefined;
    });
  }

  cycleLockState(field: string): void {
    const current = this.fieldLocks[field];
    if (current === undefined) {
      this.fieldLocks[field] = true;
    } else if (current) {
      this.fieldLocks[field] = false;
    } else {
      this.fieldLocks[field] = undefined;
    }
  }

  applyFieldLocks(): void {
    const fieldActions: Record<string, 'LOCK' | 'UNLOCK'> = {};
    for (const [field, locked] of Object.entries(this.fieldLocks)) {
      if (locked !== undefined) {
        fieldActions[field] = locked ? 'LOCK' : 'UNLOCK';
      }
    }

    this.isSaving = true;
    const loader = this.loadingService.show(this.t.translate('book.lockUnlockDialog.toast.updatingFieldLocks'));

    this.bookMetadataManageService.toggleFieldLocks(this.bookIds, fieldActions)
      .pipe(finalize(() => {
        this.isSaving = false;
        this.loadingService.hide(loader);
      }))
      .subscribe({
        next: () => {
          this.messageService.add({
            severity: 'success',
            summary: this.t.translate('book.lockUnlockDialog.toast.updatedSummary'),
            detail: this.t.translate('book.lockUnlockDialog.toast.updatedDetail')
          });
          this.dialogRef.close('fields-updated');
        },
        error: () => {
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('book.lockUnlockDialog.toast.failedSummary'),
            detail: this.t.translate('book.lockUnlockDialog.toast.failedDetail')
          });
        }
      });
  }
}
