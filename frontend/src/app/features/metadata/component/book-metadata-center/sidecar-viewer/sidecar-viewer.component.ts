import {Component, computed, inject, input, OnDestroy, signal} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import {injectQuery, QueryClient} from '@tanstack/angular-query-experimental';
import {Book} from '../../../../book/model/book.model';
import {SidecarService} from '../../../service/sidecar.service';
import {SidecarMetadata, SidecarSyncStatus} from '../../../sources/sidecar.models';
import {metadataSourceKeys, MetadataSourceQueryService} from '../../../sources/metadata-source-query.service';
import {MessageService} from '@openng/optimus-ui/api';
import {Button} from '@openng/optimus-ui/button';
import {Tag} from '@openng/optimus-ui/tag';
import {Tooltip} from '@openng/optimus-ui/tooltip';
import {DatePipe, JsonPipe} from '@angular/common';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-sidecar-viewer',
  standalone: true,
  templateUrl: './sidecar-viewer.component.html',
  styleUrls: ['./sidecar-viewer.component.scss'],
  imports: [Button, Tag, Tooltip, JsonPipe, DatePipe, TranslocoDirective, TranslocoPipe]
})
export class SidecarViewerComponent implements OnDestroy {
  readonly book = input<Book | null>(null);
  readonly currentBookId = computed(() => this.book()?.id ?? null);

  private readonly sidecarService = inject(SidecarService);
  private readonly sources = inject(MetadataSourceQueryService);
  private readonly queryClient = inject(QueryClient);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);
  private readonly activeLang = toSignal(this.t.langChanges$, {initialValue: this.t.getActiveLang()});
  private destroy$ = new Subject<void>();

  private readonly statusQuery = injectQuery(() => ({
    ...this.sources.sidecarStatus(this.currentBookId() ?? 0),
    enabled: this.currentBookId() !== null,
  }));

  readonly syncStatus = computed<SidecarSyncStatus | null>(() =>
    this.statusQuery.isError() ? null : this.statusQuery.data()?.status ?? null
  );
  readonly syncStatusSeverity = computed(() => {
    switch (this.syncStatus()) {
      case 'IN_SYNC':
        return 'success';
      case 'OUTDATED':
        return 'warn';
      case 'CONFLICT':
        return 'danger';
      case 'MISSING':
        return 'secondary';
      default:
        return 'info';
    }
  });
  readonly syncStatusLabel = computed(() => {
    const lang = this.activeLang();
    switch (this.syncStatus()) {
      case 'IN_SYNC':
        return this.t.translate('metadata.sidecar.syncStatusInSync', {}, lang);
      case 'OUTDATED':
        return this.t.translate('metadata.sidecar.syncStatusOutdated', {}, lang);
      case 'CONFLICT':
        return this.t.translate('metadata.sidecar.syncStatusConflict', {}, lang);
      case 'MISSING':
        return this.t.translate('metadata.sidecar.syncStatusMissing', {}, lang);
      case 'NOT_APPLICABLE':
        return this.t.translate('metadata.sidecar.syncStatusNA', {}, lang);
      default:
        return this.t.translate('metadata.sidecar.syncStatusUnknown', {}, lang);
    }
  });
  private readonly hasSidecar = computed(() => {
    const status = this.syncStatus();
    return status !== null && status !== 'MISSING' && status !== 'NOT_APPLICABLE';
  });

  private readonly contentQuery = injectQuery(() => ({
    ...this.sources.sidecar(this.currentBookId() ?? 0),
    enabled: this.currentBookId() !== null && this.hasSidecar(),
  }));

  readonly sidecarContent = computed<SidecarMetadata | null>(() =>
    this.hasSidecar() && !this.contentQuery.isError() ? this.contentQuery.data() ?? null : null
  );
  readonly loading = computed(() => this.statusQuery.isLoading() || this.contentQuery.isLoading());
  readonly failed = computed(() => this.statusQuery.isError() || (this.hasSidecar() && this.contentQuery.isError()));
  readonly exporting = signal(false);
  readonly importing = signal(false);

  retryRead(): void {
    const bookId = this.currentBookId();
    if (bookId !== null) {
      void this.queryClient.invalidateQueries({queryKey: metadataSourceKeys.sidecar(bookId)});
    }
  }

  exportToSidecar(): void {
    const bookId = this.currentBookId();
    if (!bookId) return;

    this.exporting.set(true);
    this.sidecarService.exportToSidecar(bookId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('metadata.sidecar.toast.exportSuccessSummary'),
          detail: this.t.translate('metadata.sidecar.toast.exportSuccessDetail')
        });
        void this.queryClient.invalidateQueries({queryKey: metadataSourceKeys.sidecar(bookId)});
        this.exporting.set(false);
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('metadata.sidecar.toast.exportFailedSummary'),
          detail: this.t.translate('metadata.sidecar.toast.exportFailedDetail')
        });
        this.exporting.set(false);
        console.error('Export failed:', err);
      }
    });
  }

  importFromSidecar(): void {
    const bookId = this.currentBookId();
    if (!bookId) return;

    this.importing.set(true);
    this.sidecarService.importFromSidecar(bookId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('metadata.sidecar.toast.importSuccessSummary'),
          detail: this.t.translate('metadata.sidecar.toast.importSuccessDetail')
        });
        void this.queryClient.invalidateQueries({queryKey: metadataSourceKeys.sidecar(bookId)});
        this.importing.set(false);
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('metadata.sidecar.toast.importFailedSummary'),
          detail: this.t.translate('metadata.sidecar.toast.importFailedDetail')
        });
        this.importing.set(false);
        console.error('Import failed:', err);
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
