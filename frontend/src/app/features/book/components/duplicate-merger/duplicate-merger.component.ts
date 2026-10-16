import {
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {ConfirmationService} from '@openng/optimus-ui/api';
import {Button} from '@openng/optimus-ui/button';
import {Checkbox} from '@openng/optimus-ui/checkbox';
import {DynamicDialogConfig, DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {Paginator, PaginatorState} from '@openng/optimus-ui/paginator';
import {ProgressBar} from '@openng/optimus-ui/progressbar';
import {RadioButton} from '@openng/optimus-ui/radiobutton';
import {SelectButton} from '@openng/optimus-ui/selectbutton';
import {Tag} from '@openng/optimus-ui/tag';
import {injectMutation, QueryClient} from '@tanstack/angular-query-experimental';

import {CoverComponent} from '../../../../shared/components/cover/cover.component';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {formatFileSizeKb} from '../../../../shared/util/file-size';
import {DeleteBooksPartialError} from '../../data/book-command.models';
import {BookCommandService} from '../../data/book-command.service';
import {BookDuplicateGroup} from '../../data/book-query.models';
import {BookQueryService} from '../../data/book-query.service';
import {BookDetail} from '../../data/book-response.models';
import {injectBookActionFeedback} from '../../service/book-action-feedback';
import {
  legacyBookCachePatches,
  withLegacyBookCache,
} from '../../service/book-command-legacy-adapter';

type PresetMode = 'strict' | 'balanced' | 'aggressive' | 'custom';

interface DisplayGroup extends BookDuplicateGroup {
  readonly selectedTargetBookId: number;
  readonly selectedForDeletion: ReadonlySet<number>;
}

@Component({
  selector: 'app-duplicate-merger',
  imports: [
    FormsModule,
    Button,
    Checkbox,
    RadioButton,
    SelectButton,
    ProgressBar,
    Tag,
    Paginator,
    TranslocoDirective,
    TranslocoPipe,
    CoverComponent,
  ],
  templateUrl: './duplicate-merger.component.html',
  styleUrls: ['./duplicate-merger.component.scss']
})
export class DuplicateMergerComponent {
  private readonly config = inject(DynamicDialogConfig);
  private readonly queryClient = inject(QueryClient);
  private readonly bookQueryService = inject(BookQueryService);
  private readonly bookCommandService = inject(BookCommandService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly dialogRef = inject(DynamicDialogRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly t = inject(TranslocoService);
  private readonly feedback = injectBookActionFeedback();
  readonly urlHelper = inject(UrlHelperService);
  readonly formatFileSize = formatFileSizeKb;

  private readonly attachMutation = injectMutation(() => withLegacyBookCache(
    this.bookCommandService.attachBookFiles(), legacyBookCachePatches.attachBookFiles));
  private readonly deleteMutation = injectMutation(() => withLegacyBookCache(
    this.bookCommandService.deleteBooks(), legacyBookCachePatches.deleteBooks));

  readonly presetMode = signal<PresetMode>('balanced');
  readonly showAdvanced = signal(false);
  readonly criteria = {
    matchByIsbn: signal(true),
    matchByExternalId: signal(true),
    matchByTitleAuthor: signal(true),
    matchByDirectory: signal(false),
    matchByFilename: signal(false),
  };
  readonly moveFiles = signal(inject(AppSettingsService).appSettings()?.metadataPersistenceSettings?.moveFilesToLibraryPattern ?? false);
  readonly presetOptions = ['Strict', 'Balanced', 'Aggressive', 'Custom'].map(preset => ({
    label: this.t.translate(`book.duplicateMerger.preset${preset}`),
    value: preset.toLowerCase(),
  }));

  readonly matchingOptions = [
    {key: 'matchByIsbn', id: 'isbn', label: 'signalIsbn'},
    {key: 'matchByExternalId', id: 'externalId', label: 'signalExternalId'},
    {key: 'matchByTitleAuthor', id: 'titleAuthor', label: 'signalTitleAuthor'},
    {key: 'matchByDirectory', id: 'directory', label: 'signalDirectory'},
    {key: 'matchByFilename', id: 'filename', label: 'signalFilename'},
  ] as const;

  readonly scanState = signal<'idle' | 'scanning' | 'empty' | 'found'>('idle');
  readonly groups = signal<readonly DisplayGroup[]>([]);
  readonly mergeProgress = signal<number | null>(null);
  readonly pageFirst = signal(0);
  readonly pageSize = signal(20);
  readonly pagedGroups = computed(() => this.groups().slice(this.pageFirst(), this.pageFirst() + this.pageSize()));
  readonly isBusy = computed(() => this.scanState() === 'scanning' || this.mergeProgress() !== null || this.deleteMutation.isPending());
  readonly canScan = computed(() => !this.isBusy() && this.matchingOptions.some(option => this.criteria[option.key]()));
  private closed = false;

  onPresetChange(): void {
    const preset = this.presetMode();
    if (preset === 'custom') return;
    this.criteria.matchByIsbn.set(true);
    this.criteria.matchByExternalId.set(true);
    this.criteria.matchByTitleAuthor.set(preset !== 'strict');
    this.criteria.matchByDirectory.set(preset === 'aggressive');
    this.criteria.matchByFilename.set(preset === 'aggressive');
  }

  async scan(): Promise<void> {
    this.scanState.set('scanning');
    this.groups.set([]);
    this.pageFirst.set(0);
    try {
      const serverGroups = await this.queryClient.fetchQuery(this.bookQueryService.duplicates({
        libraryId: this.config.data.libraryId,
        matchByIsbn: this.criteria.matchByIsbn(),
        matchByExternalId: this.criteria.matchByExternalId(),
        matchByTitleAuthor: this.criteria.matchByTitleAuthor(),
        matchByDirectory: this.criteria.matchByDirectory(),
        matchByFilename: this.criteria.matchByFilename(),
      }));
      if (this.closed || this.destroyRef.destroyed) return;

      this.groups.set(serverGroups.map(group => ({
        ...group,
        selectedTargetBookId: group.suggestedTargetBookId,
        selectedForDeletion: new Set<number>(),
      })));
      this.scanState.set(serverGroups.length ? 'found' : 'empty');
    } catch (error) {
      if (!this.closed && !this.destroyRef.destroyed) this.feedback.error('book.duplicateMerger.toast.scanFailed', error);
      this.scanState.set('idle');
    }
  }

  onPageChange(event: PaginatorState): void {
    this.pageFirst.set(event.first ?? 0);
    this.pageSize.set(event.rows ?? this.pageSize());
  }

  getBookFormats(book: BookDetail): string[] {
    return [book.primaryFile, ...(book.alternativeFormats ?? [])].flatMap(file => file?.bookType ? [file.bookType] : []);
  }

  getFileCount(book: BookDetail): number {
    return (book.primaryFile ? 1 : 0) + (book.alternativeFormats?.length ?? 0);
  }

  hasSameFormatConflict(group: DisplayGroup): boolean {
    const formats = group.books.flatMap(book => book.primaryFile?.bookType ? [book.primaryFile.bookType] : []);
    return new Set(formats).size < formats.length;
  }

  getBookFilePath(book: BookDetail): string {
    const subPath = book.primaryFile?.fileSubPath;
    const fileName = book.primaryFile?.fileName || '';
    if (subPath) return `${subPath}/${fileName}`;
    return fileName;
  }

  readonly matchReasonSeverity: Record<string, 'success' | 'info' | 'warn' | 'secondary'> = {
    ISBN: 'success', EXTERNAL_ID: 'success', TITLE_AUTHOR: 'info', DIRECTORY: 'warn', FILENAME: 'secondary',
  };

  onTargetChange(group: DisplayGroup, targetBookId: number): void {
    const selectedForDeletion = new Set(group.selectedForDeletion);
    selectedForDeletion.delete(targetBookId);
    this.replaceGroup(group, {...group, selectedTargetBookId: targetBookId, selectedForDeletion});
  }

  toggleDeleteSelection(group: DisplayGroup, bookId: number): void {
    const selectedForDeletion = new Set(group.selectedForDeletion);
    if (selectedForDeletion.has(bookId)) {
      selectedForDeletion.delete(bookId);
    } else {
      selectedForDeletion.add(bookId);
    }
    this.replaceGroup(group, {...group, selectedForDeletion});
  }

  async mergeGroups(groups: readonly DisplayGroup[], bulk = false): Promise<void> {
    if (this.isBusy()) return;
    this.mergeProgress.set(0);
    let success = 0;
    let failed = 0;
    let firstError: unknown;
    for (const group of groups) {
      if (this.closed || this.destroyRef.destroyed) break;
      try {
        await this.attachMutation.mutateAsync({
          targetBookId: group.selectedTargetBookId,
          sourceBookIds: group.books.filter(book => book.id !== group.selectedTargetBookId).map(book => book.id),
          moveFiles: this.moveFiles(),
        });
        this.removeGroup(group);
        success++;
      } catch (error) {
        firstError ??= error;
        failed++;
      }
      this.mergeProgress.set(((success + failed) / groups.length) * 100);
    }
    this.mergeProgress.set(null);
    if (this.closed || this.destroyRef.destroyed) return;
    if (success) {
      this.feedback.show('success', bulk ? 'book.duplicateMerger.toast.mergeSuccess' : 'book.bookService.toast.filesAttached', {
        count: bulk ? success : groups[0].books.length - 1,
      });
    }
    if (failed) {
      if (bulk) {
        this.feedback.show('error', 'book.duplicateMerger.toast.mergeFailed', undefined,
          this.t.translate('book.duplicateMerger.toast.mergePartialDetail', {success, failed}));
      } else {
        this.feedback.error('book.duplicateMerger.toast.mergeFailed', firstError);
      }
    }
  }

  deleteGroup(group: DisplayGroup): void {
    const bookIds = [...group.selectedForDeletion];
    this.confirmationService.confirm({
      message: this.t.translate('book.duplicateMerger.confirm.deleteMessage', {count: bookIds.length}),
      header: this.t.translate('book.duplicateMerger.confirm.deleteHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.t.translate('common.yes'),
      rejectLabel: this.t.translate('common.no'),
      acceptButtonProps: {severity: 'danger'},
      accept: () => void this.deleteConfirmedBooks(group, bookIds),
    });
  }

  closeDialog(): void {
    this.closed = true;
    this.dialogRef.close();
  }

  private async deleteConfirmedBooks(group: DisplayGroup, bookIds: readonly number[]): Promise<void> {
    if (this.closed || this.destroyRef.destroyed || !this.groups().includes(group)) return;
    try {
      const result = await this.deleteMutation.mutateAsync({bookIds});
      this.applyConfirmedDeletion(group, result.removedBookIds);
      if (this.closed || this.destroyRef.destroyed) return;
      if (result.fileCleanupFailedBookIds.length) {
        this.feedback.show('warn', 'book.bookService.toast.someFilesNotDeleted', {
          fileNames: result.fileCleanupFailedBookIds.join(', '),
        });
      } else {
        this.feedback.show('success', 'book.bookService.toast.booksDeleted', {count: result.removedBookIds.length});
      }
    } catch (error) {
      if (error instanceof DeleteBooksPartialError) this.applyConfirmedDeletion(group, error.completed.removedBookIds);
      if (!this.closed && !this.destroyRef.destroyed) this.feedback.error('book.bookService.toast.deleteFailed', error);
    }
  }

  private applyConfirmedDeletion(group: DisplayGroup, removedBookIds: readonly number[]): void {
    const books = group.books.filter(book => !removedBookIds.includes(book.id));
    if (books.length <= 1) {
      this.removeGroup(group);
      return;
    }

    const selectedForDeletion = new Set(
      [...group.selectedForDeletion].filter(bookId => !removedBookIds.includes(bookId)),
    );
    this.replaceGroup(group, {...group, books, selectedForDeletion});
  }

  private replaceGroup(group: DisplayGroup, replacement: DisplayGroup): void {
    this.groups.update(groups => groups.map(current => current === group ? replacement : current));
  }

  removeGroup(group: DisplayGroup): void {
    this.groups.update(groups => groups.filter(current => current !== group));
    if (this.pagedGroups().length === 0 && this.pageFirst() > 0) {
      this.pageFirst.set(Math.max(0, this.pageFirst() - this.pageSize()));
    }
  }

}
