import {DestroyRef, inject, Injectable, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {BookNoteV2Service, CreateBookNoteV2Request, UpdateBookNoteV2Request} from '../../../../../shared/service/book-note-v2.service';
import {NoteDialogData, NoteDialogResult} from '../../dialogs/note-dialog.component';
import {ReaderSelectionService} from '../selection/selection.service';
import {ReaderProgressService} from '../../state/progress.service';
import {ReaderLeftSidebarService} from '../../layout/panel/panel.service';
import {ReaderViewManagerService} from '../../core/view-manager.service';

export interface NoteDialogState {
  visible: boolean;
  data: NoteDialogData | null;
}

@Injectable()
export class ReaderNoteService {
  private bookNoteV2Service = inject(BookNoteV2Service);
  private messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);
  private selectionService = inject(ReaderSelectionService);
  private progressService = inject(ReaderProgressService);
  private leftSidebarService = inject(ReaderLeftSidebarService);
  private viewManager = inject(ReaderViewManagerService);

  private readonly destroyRef = inject(DestroyRef);
  private bookId!: number;

  private readonly _dialogState = signal<NoteDialogState>({
    visible: false,
    data: null
  });
  readonly dialogState = this._dialogState.asReadonly();

  initialize(bookId: number): void {
    this.bookId = bookId;

    this.leftSidebarService.editNote$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(note => {
        this.openEditDialog({
          cfi: note.cfi,
          selectedText: note.selectedText,
          chapterTitle: note.chapterTitle,
          noteId: note.id,
          noteContent: note.noteContent,
          color: note.color
        });
      });
  }

  openNewNoteDialog(): void {
    const selectionData = this.selectionService.getCurrentSelection();
    if (selectionData) {
      this._dialogState.set({
        visible: true,
        data: {
          cfi: selectionData.cfi,
          selectedText: selectionData.text,
          chapterTitle: this.progressService.currentChapterName || undefined
        }
      });
      this.selectionService.hidePopup();
    }
  }

  openEditDialog(data: NoteDialogData): void {
    this._dialogState.set({
      visible: true,
      data
    });
  }

  saveNote(result: NoteDialogResult): void {
    const data = this._dialogState().data;
    if (!data) return;

    if (data.noteId) {
      this.updateNote(data.noteId, result);
    } else {
      this.createNote(data, result);
    }
  }

  private createNote(data: NoteDialogData, result: NoteDialogResult): void {
    const createRequest: CreateBookNoteV2Request = {
      bookId: this.bookId,
      cfi: data.cfi,
      selectedText: data.selectedText,
      noteContent: result.noteContent,
      color: result.color,
      chapterTitle: data.chapterTitle
    };

    this.bookNoteV2Service.createNote(createRequest)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.closeDialog();
          this.viewManager.clearSelection();
          this.leftSidebarService.refreshNotes();
          this.messageService.add({
            severity: 'success',
            summary: this.t.translate('readerEbook.toast.noteSavedSummary'),
            detail: this.t.translate('readerEbook.toast.noteSavedDetail')
          });
        },
        error: () => {
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('readerEbook.toast.saveFailedSummary'),
            detail: this.t.translate('readerEbook.toast.saveFailedDetail')
          });
        }
      });
  }

  private updateNote(noteId: number, result: NoteDialogResult): void {
    const updateRequest: UpdateBookNoteV2Request = {
      noteContent: result.noteContent,
      color: result.color
    };

    this.bookNoteV2Service.updateNote(noteId, updateRequest)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.closeDialog();
          this.leftSidebarService.refreshNotes();
          this.messageService.add({
            severity: 'success',
            summary: this.t.translate('readerEbook.toast.noteUpdatedSummary'),
            detail: this.t.translate('readerEbook.toast.noteUpdatedDetail')
          });
        },
        error: () => {
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('readerEbook.toast.updateFailedSummary'),
            detail: this.t.translate('readerEbook.toast.updateFailedDetail')
          });
        }
      });
  }

  closeDialog(): void {
    this._dialogState.set({
      visible: false,
      data: null
    });
  }

  reset(): void {
    this._dialogState.set({
      visible: false,
      data: null
    });
  }
}
