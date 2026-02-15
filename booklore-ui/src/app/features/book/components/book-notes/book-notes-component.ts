import {Component, DestroyRef, inject, Input, OnChanges, OnInit, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Button} from 'primeng/button';
import {Dialog} from 'primeng/dialog';
import {InputText} from 'primeng/inputtext';
import {Textarea} from 'primeng/textarea';
import {ConfirmDialog} from 'primeng/confirmdialog';
import {ProgressSpinner} from 'primeng/progressspinner';
import {Tooltip} from 'primeng/tooltip';
import {ConfirmationService, MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {BookNote, BookNoteService, CreateBookNoteRequest} from '../../../../shared/service/book-note.service';

@Component({
  selector: 'app-book-notes-component',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    Button,
    Dialog,
    InputText,
    Textarea,
    ConfirmDialog,
    ProgressSpinner,
    Tooltip
  ],
  templateUrl: './book-notes-component.html',
  styleUrl: './book-notes-component.scss'
})
export class BookNotesComponent implements OnInit, OnChanges {
  @Input() bookId!: number;

  private bookNoteService = inject(BookNoteService);
  private confirmationService = inject(ConfirmationService);
  private messageService = inject(MessageService);
  private destroyRef = inject(DestroyRef);
  private readonly t = inject(TranslocoService);

  notes: BookNote[] = [];
  loading = false;
  showCreateDialog = false;
  showEditDialog = false;
  selectedNote: BookNote | null = null;

  newNote: CreateBookNoteRequest = {
    bookId: 0,
    title: '',
    content: ''
  };

  editNote: CreateBookNoteRequest = {
    bookId: 0,
    title: '',
    content: ''
  };

  ngOnInit(): void {
    if (this.bookId) {
      this.loadNotes();
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['bookId'] && changes['bookId'].currentValue) {
      this.loadNotes();
    }
  }

  loadNotes(): void {
    if (!this.bookId) return;

    this.loading = true;
    this.bookNoteService.getNotesForBook(this.bookId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (notes) => {
          this.notes = notes.sort((a, b) =>
            new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
          );
          this.loading = false;
        },
        error: (error) => {
          console.error('Failed to load notes:', error);
          this.loading = false;
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('common.error'),
            detail: this.t.translate('book.notes.toast.loadFailedDetail')
          });
        }
      });
  }

  openCreateDialog(): void {
    this.newNote = {
      bookId: this.bookId,
      title: '',
      content: ''
    };
    this.showCreateDialog = true;
  }

  openEditDialog(note: BookNote): void {
    this.selectedNote = note;
    this.editNote = {
      id: note.id,
      bookId: note.bookId,
      title: note.title,
      content: note.content
    };
    this.showEditDialog = true;
  }

  createNote(): void {
    if (!this.newNote.title.trim() || !this.newNote.content.trim()) {
      this.messageService.add({
        severity: 'warn',
        summary: this.t.translate('book.notes.toast.validationSummary'),
        detail: this.t.translate('book.notes.toast.validationDetail')
      });
      return;
    }

    this.bookNoteService.createOrUpdateNote(this.newNote)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (note) => {
          this.notes.unshift(note);
          this.showCreateDialog = false;
          this.messageService.add({
            severity: 'success',
            summary: this.t.translate('common.success'),
            detail: this.t.translate('book.notes.toast.createSuccessDetail')
          });
        },
        error: (error) => {
          console.error('Failed to create note:', error);
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('common.error'),
            detail: this.t.translate('book.notes.toast.createFailedDetail')
          });
        }
      });
  }

  updateNote(): void {
    if (!this.editNote.title?.trim() || !this.editNote.content?.trim()) {
      this.messageService.add({
        severity: 'warn',
        summary: this.t.translate('book.notes.toast.validationSummary'),
        detail: this.t.translate('book.notes.toast.validationDetail')
      });
      return;
    }

    this.bookNoteService.createOrUpdateNote(this.editNote)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updatedNote) => {
          const index = this.notes.findIndex(n => n.id === this.selectedNote?.id);
          if (index !== -1) {
            this.notes[index] = updatedNote;
            this.notes.sort((a, b) =>
              new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
            );
          }
          this.showEditDialog = false;
          this.selectedNote = null;
          this.messageService.add({
            severity: 'success',
            summary: this.t.translate('common.success'),
            detail: this.t.translate('book.notes.toast.updateSuccessDetail')
          });
        },
        error: (error) => {
          console.error('Failed to update note:', error);
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('common.error'),
            detail: this.t.translate('book.notes.toast.updateFailedDetail')
          });
        }
      });
  }

  deleteNote(note: BookNote): void {
    this.confirmationService.confirm({
      key: 'deleteNote',
      message: this.t.translate('book.notes.confirm.deleteMessage', {title: note.title}),
      header: this.t.translate('book.notes.confirm.deleteHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptIcon: 'pi pi-trash',
      rejectIcon: 'pi pi-times',
      acceptButtonStyleClass: 'p-button-danger, p-button-outlined p-button-danger',
      rejectButtonStyleClass: 'p-button-danger, p-button-outlined p-button-info',
      accept: () => {
        this.performDelete(note.id);
      }
    });
  }

  private performDelete(noteId: number): void {
    this.bookNoteService.deleteNote(noteId).subscribe({
      next: () => {
        this.notes = this.notes.filter(n => n.id !== noteId);
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('common.success'),
          detail: this.t.translate('book.notes.toast.deleteSuccessDetail')
        });
      },
      error: (error) => {
        console.error('Failed to delete note:', error);
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: this.t.translate('book.notes.toast.deleteFailedDetail')
        });
      }
    });
  }

  cancelCreate(): void {
    this.showCreateDialog = false;
    this.newNote = {
      bookId: this.bookId,
      title: '',
      content: ''
    };
  }

  cancelEdit(): void {
    this.showEditDialog = false;
    this.selectedNote = null;
    this.editNote = {
      bookId: this.bookId,
      title: '',
      content: ''
    };
  }

  formatDate(dateString: string): string {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }
}
