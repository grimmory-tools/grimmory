import {TestBed} from '@angular/core/testing';
import {of, Subject} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {BookNoteV2, BookNoteV2Service} from '../../../../../shared/service/book-note-v2.service';
import {ReaderViewManagerService} from '../../core/view-manager.service';
import {ReaderLeftSidebarService} from './panel.service';

describe('ReaderLeftSidebarService', () => {
  let service: ReaderLeftSidebarService;
  let destroy$: Subject<void>;

  const notes = [
    {id: 1, bookId: 9, cfi: 'epubcfi(/6/2)', noteContent: 'Alpha', selectedText: 'Quote', chapterTitle: 'Intro', createdAt: '2026-03-26T00:00:00Z'},
    {id: 2, bookId: 9, cfi: 'epubcfi(/6/4)', noteContent: 'Beta', selectedText: 'Passage', chapterTitle: 'Middle', createdAt: '2026-03-26T00:00:00Z'},
  ];

  const bookNoteV2Service = {
    getNotesForBook: vi.fn(),
    deleteNote: vi.fn(),
  };

  const asyncSearch = async function* () {
    yield {progress: 0.4};
    yield {
      label: 'Chapter 1',
      subitems: [
        {
          cfi: 'epubcfi(/6/8)',
          excerpt: {pre: 'before', match: 'match', post: 'after'},
        },
      ],
    };
    yield 'done' as const;
  };

  const viewManager = {
    goTo: vi.fn(() => of(void 0)),
    search: vi.fn(() => asyncSearch()),
    clearSearch: vi.fn(),
  };

  beforeEach(() => {
    destroy$ = new Subject<void>();
    bookNoteV2Service.getNotesForBook.mockReset();
    bookNoteV2Service.getNotesForBook.mockReturnValue(of(notes));
    bookNoteV2Service.deleteNote.mockReset();
    bookNoteV2Service.deleteNote.mockReturnValue(of(void 0));
    viewManager.goTo.mockReset();
    viewManager.goTo.mockReturnValue(of(void 0));
    viewManager.search.mockReset();
    viewManager.search.mockImplementation(() => asyncSearch());
    viewManager.clearSearch.mockReset();

    TestBed.configureTestingModule({
      providers: [
        ReaderLeftSidebarService,
        {provide: BookNoteV2Service, useValue: bookNoteV2Service},
        {provide: ReaderViewManagerService, useValue: viewManager},
      ]
    });

    service = TestBed.inject(ReaderLeftSidebarService);
    service.initialize(9, destroy$);
  });

  afterEach(() => {
    vi.useRealTimers();
    destroy$.next();
    destroy$.complete();
    TestBed.resetTestingModule();
  });

  it('initializes and filters notes', () => {
    expect(bookNoteV2Service.getNotesForBook).toHaveBeenCalledWith(9);
    expect(service.notes()).toEqual(notes);

    service.setNotesSearchQuery('quote');
    expect(service.filteredNotes()).toEqual([notes[0]]);

    service.setNotesSearchQuery('middle');
    expect(service.filteredNotes()).toEqual([notes[1]]);
  });

  it('opens, closes, toggles, and schedules search from openWithSearch', async () => {
    vi.useFakeTimers();
    const searchSpy = vi.spyOn(service, 'search').mockResolvedValue(undefined);

    service.open('notes');
    expect(service.isOpen()).toBe(true);
    expect(service.activeTab()).toBe('notes');

    service.toggle('notes');
    expect(service.isOpen()).toBe(false);

    service.openWithSearch('magic');
    await vi.advanceTimersByTimeAsync(100);

    expect(service.isOpen()).toBe(true);
    expect(service.activeTab()).toBe('search');
    expect(searchSpy).toHaveBeenCalledWith('magic');
  });

  it('searches, tracks progress, and clears when the query is blank', async () => {
    await service.search('magic');

    expect(viewManager.search).toHaveBeenCalledWith({query: 'magic'});
    expect(service.searchState()).toEqual({
      query: 'magic',
      results: [
        {
          cfi: 'epubcfi(/6/8)',
          excerpt: {pre: 'before', match: 'match', post: 'after'},
          sectionLabel: 'Chapter 1',
        },
      ],
      isSearching: false,
      progress: 1,
    });

    await service.search('   ');
    expect(viewManager.clearSearch).toHaveBeenCalledOnce();
    expect(service.searchState()).toEqual({
      query: '',
      results: [],
      isSearching: false,
      progress: 0,
    });
  });

  it('handles search errors without leaving the searching flag stuck', async () => {
    viewManager.search.mockImplementation(async function* () {
      yield {progress: 0};
      throw new Error('search failed');
    });

    await service.search('broken');

    expect(service.searchState()).toEqual({
      query: 'broken',
      results: [],
      isSearching: false,
      progress: 0,
    });
  });

  it('navigates to notes and search results, deletes notes, emits edit events, and resets', () => {
    const editEvents: BookNoteV2[] = [];
    service.editNote$.subscribe(note => editEvents.push(note));

    service.open();
    service.navigateToNote('epubcfi(/6/2)');
    service.navigateToSearchResult('epubcfi(/6/8)');
    expect(viewManager.goTo).toHaveBeenNthCalledWith(1, 'epubcfi(/6/2)');
    expect(viewManager.goTo).toHaveBeenNthCalledWith(2, 'epubcfi(/6/8)');
    expect(service.isOpen()).toBe(false);

    service.deleteNote(1);
    expect(bookNoteV2Service.deleteNote).toHaveBeenCalledWith(1);
    expect(service.notes()).toEqual([notes[1]]);

    service.open();
    service.editNote(notes[1]);
    expect(editEvents).toEqual([notes[1]]);
    expect(service.isOpen()).toBe(false);

    service.reset();
    expect(service.isOpen()).toBe(false);
    expect(service.activeTab()).toBe('search');
    expect(service.notes()).toEqual([]);
    expect(service.notesSearchQuery()).toBe('');
    expect(service.searchState()).toEqual({
      query: '',
      results: [],
      isSearching: false,
      progress: 0,
    });
  });
});
