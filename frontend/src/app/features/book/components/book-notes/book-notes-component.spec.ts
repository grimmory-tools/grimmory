import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {ConfirmationService, MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';

import {BookNote, BookNoteService} from '../../../../shared/service/book-note.service';
import {BookNotesComponent} from './book-notes-component';

interface ConfirmationLike {
  key?: string;
  message?: string;
  header?: string;
  accept?: () => void;
}

function createNote(id: number, overrides: Partial<BookNote> = {}): BookNote {
  return {
    id,
    userId: 7,
    bookId: 42,
    title: `Note ${id}`,
    content: `Content ${id}`,
    createdAt: '2026-03-28T09:00:00.000Z',
    updatedAt: `2026-03-28T09:0${id}:00.000Z`,
    ...overrides,
  };
}

describe('BookNotesComponent', () => {
  let component: BookNotesComponent;
  let bookNoteService: {
    getNotesForBook: ReturnType<typeof vi.fn>;
    createOrUpdateNote: ReturnType<typeof vi.fn>;
    deleteNote: ReturnType<typeof vi.fn>;
  };
  let messageService: {
    add: ReturnType<typeof vi.fn>;
  };
  let confirmationService: {
    confirm: ReturnType<typeof vi.fn>;
  };
  let translate: ReturnType<typeof vi.fn>;
  let lastConfirmation: ConfirmationLike | null;

  function createComponent(): BookNotesComponent {
    return TestBed.runInInjectionContext(() => new BookNotesComponent());
  }

  beforeEach(() => {
    lastConfirmation = null;
    bookNoteService = {
      getNotesForBook: vi.fn(),
      createOrUpdateNote: vi.fn(),
      deleteNote: vi.fn(),
    };
    messageService = {
      add: vi.fn(),
    };
    confirmationService = {
      confirm: vi.fn((confirmation: ConfirmationLike) => {
        lastConfirmation = confirmation;
      }),
    };
    translate = vi.fn((key: string, params?: Record<string, unknown>) => (
      params ? `${key}:${JSON.stringify(params)}` : key
    ));

    TestBed.configureTestingModule({
      providers: [
        {provide: BookNoteService, useValue: bookNoteService},
        {provide: ConfirmationService, useValue: confirmationService},
        {provide: MessageService, useValue: messageService},
        {provide: TranslocoService, useValue: {translate}},
      ],
    });

    component = createComponent();
    component.bookId = 42;
  });

  it('skips loading notes when no book is selected', () => {
    component.bookId = 0;

    component.loadNotes();

    expect(bookNoteService.getNotesForBook).not.toHaveBeenCalled();
    expect(component.loading()).toBe(false);
  });

  it('loads notes and orders them by most recent update first', () => {
    const oldest = createNote(1, {updatedAt: '2026-03-28T08:00:00.000Z'});
    const newest = createNote(2, {updatedAt: '2026-03-28T12:00:00.000Z'});
    const middle = createNote(3, {updatedAt: '2026-03-28T10:00:00.000Z'});
    bookNoteService.getNotesForBook.mockReturnValue(of([oldest, newest, middle]));

    component.loadNotes();

    expect(bookNoteService.getNotesForBook).toHaveBeenCalledWith(42);
    expect(component.notes.map(note => note.id)).toEqual([2, 3, 1]);
    expect(component.loading()).toBe(false);
  });

  it('shows an error toast when loading notes fails', () => {
    bookNoteService.getNotesForBook.mockReturnValue(throwError(() => new Error('load failed')));

    component.loadNotes();

    expect(component.loading()).toBe(false);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'common.error',
      detail: 'book.notes.toast.loadFailedDetail',
    });
  });

  it('blocks note creation when title or content is blank after trimming', () => {
    component.newNote = {
      bookId: 42,
      title: '  ',
      content: '  ',
    };

    component.createNote();

    expect(bookNoteService.createOrUpdateNote).not.toHaveBeenCalled();
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'warn',
      summary: 'book.notes.toast.validationSummary',
      detail: 'book.notes.toast.validationDetail',
    });
  });

  it('creates a note and closes the create dialog on success', () => {
    const createdNote = createNote(9, {title: 'Created', content: 'Fresh content'});
    component.notes = [createNote(1)];
    component.showCreateDialog = true;
    component.newNote = {
      bookId: 42,
      title: 'Created',
      content: 'Fresh content',
    };
    bookNoteService.createOrUpdateNote.mockReturnValue(of(createdNote));

    component.createNote();

    expect(bookNoteService.createOrUpdateNote).toHaveBeenCalledWith(component.newNote);
    expect(component.notes[0]).toEqual(createdNote);
    expect(component.showCreateDialog).toBe(false);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'common.success',
      detail: 'book.notes.toast.createSuccessDetail',
    });
  });

  it('shows an error toast when note creation fails', () => {
    component.showCreateDialog = true;
    component.newNote = {
      bookId: 42,
      title: 'Created',
      content: 'Fresh content',
    };
    bookNoteService.createOrUpdateNote.mockReturnValue(throwError(() => new Error('create failed')));

    component.createNote();

    expect(component.showCreateDialog).toBe(true);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'common.error',
      detail: 'book.notes.toast.createFailedDetail',
    });
  });

  it('blocks note updates when title or content is blank after trimming', () => {
    component.editNote = {
      id: 4,
      bookId: 42,
      title: '  ',
      content: '  ',
    };

    component.updateNote();

    expect(bookNoteService.createOrUpdateNote).not.toHaveBeenCalled();
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'warn',
      summary: 'book.notes.toast.validationSummary',
      detail: 'book.notes.toast.validationDetail',
    });
  });

  it('updates an existing note, resorts the list, and clears edit state on success', () => {
    const staleNote = createNote(1, {updatedAt: '2026-03-28T08:00:00.000Z', title: 'Old title'});
    const selectedNote = createNote(2, {updatedAt: '2026-03-28T10:00:00.000Z', title: 'Before edit'});
    const updatedNote = createNote(2, {
      updatedAt: '2026-03-28T14:00:00.000Z',
      title: 'After edit',
      content: 'Updated body',
    });
    component.notes = [staleNote, selectedNote];
    component.selectedNote = selectedNote;
    component.showEditDialog = true;
    component.editNote = {
      id: 2,
      bookId: 42,
      title: 'After edit',
      content: 'Updated body',
    };
    bookNoteService.createOrUpdateNote.mockReturnValue(of(updatedNote));

    component.updateNote();

    expect(bookNoteService.createOrUpdateNote).toHaveBeenCalledWith(component.editNote);
    expect(component.notes.map(note => note.id)).toEqual([2, 1]);
    expect(component.notes[0]).toEqual(updatedNote);
    expect(component.showEditDialog).toBe(false);
    expect(component.selectedNote).toBeNull();
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'common.success',
      detail: 'book.notes.toast.updateSuccessDetail',
    });
  });

  it('shows an error toast when updating a note fails', () => {
    const selectedNote = createNote(2, {title: 'Before edit'});
    component.selectedNote = selectedNote;
    component.showEditDialog = true;
    component.editNote = {
      id: 2,
      bookId: 42,
      title: 'After edit',
      content: 'Updated body',
    };
    bookNoteService.createOrUpdateNote.mockReturnValue(throwError(() => new Error('update failed')));

    component.updateNote();

    expect(component.showEditDialog).toBe(true);
    expect(component.selectedNote).toEqual(selectedNote);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'common.error',
      detail: 'book.notes.toast.updateFailedDetail',
    });
  });

  it('confirms note deletion and removes the note when the accept callback succeeds', () => {
    const keepNote = createNote(1);
    const deleteCandidate = createNote(2, {title: 'Delete me'});
    component.notes = [keepNote, deleteCandidate];
    bookNoteService.deleteNote.mockReturnValue(of(void 0));

    component.deleteNote(deleteCandidate);
    lastConfirmation?.accept?.();

    expect(confirmationService.confirm).toHaveBeenCalledOnce();
    expect(lastConfirmation).toMatchObject({
      key: 'deleteNote',
      header: 'book.notes.confirm.deleteHeader',
      message: 'book.notes.confirm.deleteMessage:{"title":"Delete me"}',
    });
    expect(bookNoteService.deleteNote).toHaveBeenCalledWith(2);
    expect(component.notes).toEqual([keepNote]);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'common.success',
      detail: 'book.notes.toast.deleteSuccessDetail',
    });
  });

  it('shows an error toast when the confirmed delete fails', () => {
    const note = createNote(3, {title: 'Delete me'});
    component.notes = [note];
    bookNoteService.deleteNote.mockReturnValue(throwError(() => new Error('delete failed')));

    component.deleteNote(note);
    lastConfirmation?.accept?.();

    expect(bookNoteService.deleteNote).toHaveBeenCalledWith(3);
    expect(component.notes).toEqual([note]);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'common.error',
      detail: 'book.notes.toast.deleteFailedDetail',
    });
  });

  it('resets create and edit state when dialogs are cancelled', () => {
    const selectedNote = createNote(4);
    component.showCreateDialog = true;
    component.newNote = {
      bookId: 42,
      title: 'Draft title',
      content: 'Draft content',
    };
    component.showEditDialog = true;
    component.selectedNote = selectedNote;
    component.editNote = {
      id: selectedNote.id,
      bookId: 42,
      title: 'Edited title',
      content: 'Edited content',
    };

    component.cancelCreate();
    component.cancelEdit();

    expect(component.showCreateDialog).toBe(false);
    expect(component.newNote).toEqual({
      bookId: 42,
      title: '',
      content: '',
    });
    expect(component.showEditDialog).toBe(false);
    expect(component.selectedNote).toBeNull();
    expect(component.editNote).toEqual({
      bookId: 42,
      title: '',
      content: '',
    });
  });
});
