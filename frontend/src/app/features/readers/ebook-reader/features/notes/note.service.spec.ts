import {TestBed} from '@angular/core/testing';
import {MessageService} from 'primeng/api';
import {of, Subject, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {BookNoteV2Service} from '../../../../../shared/service/book-note-v2.service';
import {ReaderSelectionService} from '../selection/selection.service';
import {ReaderProgressService} from '../../state/progress.service';
import {ReaderLeftSidebarService} from '../../layout/panel/panel.service';
import {ReaderViewManagerService} from '../../core/view-manager.service';
import {TranslocoService} from '@jsverse/transloco';
import {ReaderNoteService} from './note.service';

describe('ReaderNoteService', () => {
  const bookNoteV2Service = {
    createNote: vi.fn(),
    updateNote: vi.fn(),
  };

  const messageService = {
    add: vi.fn(),
  };

  const selectionService = {
    getCurrentSelection: vi.fn(),
    hidePopup: vi.fn(),
  };

  const progressService = {
    currentChapterName: 'Chapter 4',
  };

  const editNote$ = new Subject<{
    id: number;
    cfi: string;
    selectedText?: string;
    chapterTitle?: string;
    noteContent?: string;
    color?: string;
  }>();

  const leftSidebarService = {
    editNote$,
    refreshNotes: vi.fn(),
  };

  const viewManager = {
    clearSelection: vi.fn(),
  };

  const translocoService = {
    translate: vi.fn((key: string) => key),
  };

  let service: ReaderNoteService;

  beforeEach(() => {
    bookNoteV2Service.createNote.mockReset();
    bookNoteV2Service.updateNote.mockReset();
    messageService.add.mockReset();
    selectionService.getCurrentSelection.mockReset();
    selectionService.hidePopup.mockReset();
    leftSidebarService.refreshNotes.mockReset();
    viewManager.clearSelection.mockReset();
    translocoService.translate.mockClear();

    TestBed.configureTestingModule({
      providers: [
        ReaderNoteService,
        {provide: BookNoteV2Service, useValue: bookNoteV2Service},
        {provide: MessageService, useValue: messageService},
        {provide: ReaderSelectionService, useValue: selectionService},
        {provide: ReaderProgressService, useValue: progressService},
        {provide: ReaderLeftSidebarService, useValue: leftSidebarService},
        {provide: ReaderViewManagerService, useValue: viewManager},
        {provide: TranslocoService, useValue: translocoService},
      ]
    });

    service = TestBed.inject(ReaderNoteService);
    service.initialize(14);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('opens a new note dialog from the current selection and hides the popup', () => {
    selectionService.getCurrentSelection.mockReturnValue({
      cfi: 'epubcfi(/6/2)',
      text: 'Quoted text',
    });

    service.openNewNoteDialog();

    expect(selectionService.hidePopup).toHaveBeenCalledOnce();
    expect(service.dialogState()).toEqual({
      visible: true,
      data: {
        cfi: 'epubcfi(/6/2)',
        selectedText: 'Quoted text',
        chapterTitle: 'Chapter 4',
      }
    });
  });

  it('does nothing when opening a new note dialog without a selection', () => {
    selectionService.getCurrentSelection.mockReturnValue(null);

    service.openNewNoteDialog();

    expect(selectionService.hidePopup).not.toHaveBeenCalled();
    expect(service.dialogState()).toEqual({visible: false, data: null});
  });

  it('creates a note, clears selection, refreshes notes, and shows a success toast', () => {
    bookNoteV2Service.createNote.mockReturnValue(of({id: 9}));
    service.openEditDialog({
      cfi: 'epubcfi(/6/2)',
      selectedText: 'Quoted text',
      chapterTitle: 'Chapter 4',
    });

    service.saveNote({noteContent: 'Important thought', color: '#FFC107'});

    expect(bookNoteV2Service.createNote).toHaveBeenCalledWith({
      bookId: 14,
      cfi: 'epubcfi(/6/2)',
      selectedText: 'Quoted text',
      noteContent: 'Important thought',
      color: '#FFC107',
      chapterTitle: 'Chapter 4',
    });
    expect(viewManager.clearSelection).toHaveBeenCalledOnce();
    expect(leftSidebarService.refreshNotes).toHaveBeenCalledOnce();
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'readerEbook.toast.noteSavedSummary',
      detail: 'readerEbook.toast.noteSavedDetail'
    });
    expect(service.dialogState()).toEqual({visible: false, data: null});
  });

  it('updates an existing note and shows the update toast', () => {
    bookNoteV2Service.updateNote.mockReturnValue(of({id: 12}));
    service.openEditDialog({
      cfi: 'epubcfi(/6/4)',
      noteId: 12,
      noteContent: 'Old',
      color: '#2196F3',
    });

    service.saveNote({noteContent: 'New content', color: '#4CAF50'});

    expect(bookNoteV2Service.updateNote).toHaveBeenCalledWith(12, {
      noteContent: 'New content',
      color: '#4CAF50',
    });
    expect(leftSidebarService.refreshNotes).toHaveBeenCalledOnce();
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'readerEbook.toast.noteUpdatedSummary',
      detail: 'readerEbook.toast.noteUpdatedDetail'
    });
  });

  it('shows error toasts for create and update failures and leaves the dialog open', () => {
    bookNoteV2Service.createNote.mockReturnValue(throwError(() => new Error('save failed')));
    service.openEditDialog({
      cfi: 'epubcfi(/6/2)',
      selectedText: 'Quoted text',
    });

    service.saveNote({noteContent: 'Important thought', color: '#FFC107'});

    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'readerEbook.toast.saveFailedSummary',
      detail: 'readerEbook.toast.saveFailedDetail'
    });
    expect(service.dialogState().visible).toBe(true);

    bookNoteV2Service.updateNote.mockReturnValue(throwError(() => new Error('update failed')));
    service.openEditDialog({
      cfi: 'epubcfi(/6/4)',
      noteId: 12,
      noteContent: 'Old',
    });

    service.saveNote({noteContent: 'New content', color: '#4CAF50'});

    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'readerEbook.toast.updateFailedSummary',
      detail: 'readerEbook.toast.updateFailedDetail'
    });
  });

  it('opens the edit dialog from sidebar events and resets cleanly', () => {
    editNote$.next({
      id: 25,
      cfi: 'epubcfi(/6/10)',
      selectedText: 'Selected',
      chapterTitle: 'Chapter 9',
      noteContent: 'Existing note',
      color: '#2196F3',
    });

    expect(service.dialogState()).toEqual({
      visible: true,
      data: {
        cfi: 'epubcfi(/6/10)',
        selectedText: 'Selected',
        chapterTitle: 'Chapter 9',
        noteId: 25,
        noteContent: 'Existing note',
        color: '#2196F3',
      }
    });

    service.closeDialog();
    expect(service.dialogState()).toEqual({visible: false, data: null});

    service.reset();
    expect(service.dialogState()).toEqual({visible: false, data: null});
  });
});
