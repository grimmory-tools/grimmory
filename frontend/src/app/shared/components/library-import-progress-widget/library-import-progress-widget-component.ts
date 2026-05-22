import {AsyncPipe} from '@angular/common';
import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {Router} from '@angular/router';
import {ButtonDirective} from 'primeng/button';
import {ProgressBar} from 'primeng/progressbar';
import {Tag} from 'primeng/tag';
import {TranslocoDirective} from '@jsverse/transloco';

import {LibraryImportProgressService, LibraryImportProgressState, LibraryImportProgressStatus} from '../../service/library-import-progress.service';

@Component({
  selector: 'app-library-import-progress-widget',
  templateUrl: './library-import-progress-widget-component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [AsyncPipe, ButtonDirective, ProgressBar, Tag, TranslocoDirective]
})
export class LibraryImportProgressWidgetComponent {
  protected readonly progressService = inject(LibraryImportProgressService);
  private readonly router = inject(Router);

  getProgressPercent(state: LibraryImportProgressState): number {
    if (state.expectedCount <= 0) return 0;
    if (state.status === 'COMPLETED') return 100;
    const percent = Math.round((state.processedCount / state.expectedCount) * 100);
    return Math.min(100, Math.max(0, percent));
  }

  getCurrentCount(state: LibraryImportProgressState): number {
    return Math.min(state.processedCount, state.expectedCount);
  }

  getStatusLabelKey(status: LibraryImportProgressStatus): string {
    return {
      IN_PROGRESS: 'shared.metadataProgress.statusInProgress',
      COMPLETED: 'shared.metadataProgress.statusCompleted',
      ERROR: 'shared.metadataProgress.statusError',
    }[status];
  }

  getTagSeverity(status: LibraryImportProgressStatus): 'info' | 'success' | 'danger' {
    switch (status) {
      case 'COMPLETED':
        return 'success';
      case 'ERROR':
        return 'danger';
      case 'IN_PROGRESS':
      default:
        return 'info';
    }
  }

  dismiss(): void {
    this.progressService.clear();
  }

  openLibrary(state: LibraryImportProgressState): void {
    if (state.libraryId === undefined) return;
    this.router.navigate(['/library', state.libraryId, 'books']).then(navigated => {
      if (navigated) {
        this.dismiss();
      }
    });
  }
}
