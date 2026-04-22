import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {CdkDragDrop} from '@angular/cdk/drag-drop';
import {AutoCompleteSelectEvent} from 'primeng/autocomplete';

import {Book, BookMetadata} from '../../../../book/model/book.model';
import {BookService} from '../../../../book/service/book.service';
import {BookMetadataManageService} from '../../../../book/service/book-metadata-manage.service';
import {AppSettings} from '../../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {MetadataFormBuilder} from '../../../../../shared/metadata';
import {MetadataUtilsService} from '../../../../../shared/metadata/metadata-utils.service';
import {MetadataPickerComponent} from './metadata-picker.component';

describe('MetadataPickerComponent', () => {
  const uniqueMetadata = signal({
    authors: ['Alice', 'Bob'],
    categories: ['Fantasy', 'History'],
    moods: ['Hopeful'],
    tags: ['Epic'],
    publishers: ['Orbit'],
    series: ['Saga'],
  });
  const appSettings = signal<AppSettings | null>(null);
  const updateBookMetadata = vi.fn(() => of(void 0));
  const uploadAudiobookCoverFromUrl = vi.fn(() => of(void 0));
  const supportsDualCovers = vi.fn(() => true);
  const messageAdd = vi.fn();
  const translate = vi.fn((key: string, params?: Record<string, unknown>) => params?.['field'] ? `${key}:${params['field']}` : key);
  const getThumbnailUrl = vi.fn((bookId: number, updatedOn?: string) => `thumb:${bookId}:${updatedOn ?? 'none'}`);
  const getCoverUrl = vi.fn((bookId: number, updatedOn?: string) => `cover:${bookId}:${updatedOn ?? 'none'}`);
  const getAudiobookCoverUrl = vi.fn((bookId: number, updatedOn?: string) => `audio-cover:${bookId}:${updatedOn ?? 'none'}`);

  function createMetadata(overrides: Partial<BookMetadata> = {}): BookMetadata {
    return {
      bookId: 21,
      title: 'Original Title',
      subtitle: 'Original Subtitle',
      authors: ['Alice'],
      categories: ['Fantasy'],
      description: 'Original description',
      provider: 'googleBooks',
      coverUpdatedOn: '2026-03-26',
      titleLocked: true,
      comicMetadata: {
        issueNumber: '1',
      },
      ...overrides,
    };
  }

  function createBook(metadata: Partial<BookMetadata> = {}, overrides: Partial<Book> = {}): Book {
    return {
      id: 21,
      libraryId: 1,
      libraryName: 'Library',
      metadata: createMetadata(metadata),
      primaryFile: {id: 1, bookId: 21, bookType: 'EPUB'},
      alternativeFormats: [],
      supplementaryFiles: [],
      isPhysical: false,
      ...overrides,
    } as Book;
  }

  beforeEach(() => {
    uniqueMetadata.set({
      authors: ['Alice', 'Bob'],
      categories: ['Fantasy', 'History'],
      moods: ['Hopeful'],
      tags: ['Epic'],
      publishers: ['Orbit'],
      series: ['Saga'],
    });
    appSettings.set(null);
    updateBookMetadata.mockClear();
    uploadAudiobookCoverFromUrl.mockClear();
    supportsDualCovers.mockClear();
    messageAdd.mockClear();
    translate.mockClear();
    getThumbnailUrl.mockClear();
    getCoverUrl.mockClear();
    getAudiobookCoverUrl.mockClear();

    TestBed.configureTestingModule({
      providers: [
        MetadataFormBuilder,
        MetadataUtilsService,
        {provide: AppSettingsService, useValue: {appSettings}},
        {provide: BookService, useValue: {uniqueMetadata}},
        {
          provide: BookMetadataManageService,
          useValue: {updateBookMetadata, uploadAudiobookCoverFromUrl, supportsDualCovers},
        },
        {
          provide: UrlHelperService,
          useValue: {getThumbnailUrl, getCoverUrl, getAudiobookCoverUrl},
        },
        {provide: MessageService, useValue: {add: messageAdd}},
        {provide: TranslocoService, useValue: {translate}},
      ]
    });
  });

  it('syncs provider field visibility from app settings and filters metadata suggestions', () => {
    appSettings.set({
      metadataProviderSpecificFields: {
        googleId: true,
        audibleId: true,
        amazonRating: false,
      },
    } as AppSettings);

    const component = TestBed.runInInjectionContext(() => new MetadataPickerComponent());
    TestBed.flushEffects();

    expect(component.metadataProviderFields.map(field => field.controlName)).toContain('googleId');
    expect(component.metadataProviderFields.map(field => field.controlName)).toContain('audibleId');
    expect(component.metadataProviderFields.map(field => field.controlName)).not.toContain('amazonRating');

    component.filterItems({query: 'fan'}, 'categories');
    expect(component.getFiltered('categories')).toEqual(['Fantasy']);
    expect(component.getFiltered('missing')).toEqual([]);
  });

  it('patches book metadata into the form and resets review state when a new book arrives', () => {
    const component = TestBed.runInInjectionContext(() => new MetadataPickerComponent());
    component.reviewMode = true;
    component.copiedFields = {title: true};
    component.savedFields = {title: true};
    component.hoveredFields = {title: true};
    component.fetchedMetadata = createMetadata({provider: 'comicvine'});

    component.book = createBook();

    expect(component.currentBookId).toBe(21);
    expect(component.metadataForm.get('title')?.value).toBe('Original Title');
    expect(component.metadataForm.get('authors')?.value).toEqual(['Alice']);
    expect(component.metadataForm.get('title')?.disabled).toBe(true);
    expect(component.originalMetadata.thumbnailUrl).toBe('thumb:21:2026-03-26');
    expect(component.comicSectionExpanded).toBe(true);
    expect(component.copiedFields).toEqual({});
    expect(component.savedFields).toEqual({});
    expect(component.hoveredFields).toEqual({});
  });

  it('handles author and array autocomplete helpers without duplicating values', () => {
    const component = TestBed.runInInjectionContext(() => new MetadataPickerComponent());
    component.book = createBook();
    component.metadataForm.get('authors')?.setValue(['Alice', 'Bob']);

    component.dropAuthor({previousIndex: 1, currentIndex: 0} as CdkDragDrop<string[]>);
    expect(component.metadataForm.get('authors')?.value).toEqual(['Bob', 'Alice']);

    component.removeAuthor(1);
    expect(component.metadataForm.get('authors')?.value).toEqual(['Bob']);

    component.authorInputValue = 'Cara';
    component.onAuthorInputKeyUp({key: 'Enter'} as KeyboardEvent);
    expect(component.metadataForm.get('authors')?.value).toEqual(['Bob', 'Cara']);
    expect(component.authorInputValue).toBe('');

    component.onAutoCompleteSelect('categories', {
      value: 'Fantasy',
      originalEvent: {target: {value: 'Fantasy'}},
    } as unknown as AutoCompleteSelectEvent);
    component.onAutoCompleteKeyUp('categories', {
      key: 'Enter',
      target: {value: 'History'},
    } as unknown as KeyboardEvent);

    expect(component.metadataForm.get('categories')?.value).toEqual(['Fantasy', 'History']);
  });

  it('copies fetched values, respects locked fields, and resets fields back to the original metadata', () => {
    const component = TestBed.runInInjectionContext(() => new MetadataPickerComponent());
    component.book = createBook();
    component.fetchedMetadata = createMetadata({
      title: 'Fetched Title',
      provider: 'Audible',
      thumbnailUrl: 'https://covers.example/audiobook.jpg',
      comicMetadata: {issueNumber: '7'},
    });

    component.copyFetchedToCurrent('title');
    expect(messageAdd).toHaveBeenCalledWith({
      severity: 'warn',
      summary: 'metadata.picker.toast.actionBlockedSummary',
      detail: 'metadata.picker.toast.fieldLockedDetail:title',
    });

    component.metadataForm.get('titleLocked')?.setValue(false);
    component.metadataForm.get('title')?.enable();
    component.copyFetchedToCurrent('title');
    expect(component.metadataForm.get('title')?.value).toBe('Fetched Title');
    expect(component.isValueCopied('title')).toBe(true);

    component.copyFetchedToCurrent('audiobookThumbnailUrl');
    expect(component.metadataForm.get('audiobookThumbnailUrl')?.value).toBe('https://covers.example/audiobook.jpg');
    expect(component.isValueCopied('audiobookThumbnailUrl')).toBe(true);

    component.metadataForm.get('comicIssueNumberLocked')?.setValue(true);
    component.copyFetchedToCurrent('comicIssueNumber');
    expect(messageAdd).toHaveBeenLastCalledWith({
      severity: 'warn',
      summary: 'metadata.picker.toast.actionBlockedSummary',
      detail: 'metadata.picker.toast.fieldLockedDetail:Issue #',
    });

    component.resetField('title');
    expect(component.metadataForm.get('title')?.value).toBe('Original Title');
    expect(component.isValueCopied('title')).toBe(false);
  });

  it('saves metadata, uploads audible covers when needed, and toggles lock states', () => {
    const component = TestBed.runInInjectionContext(() => new MetadataPickerComponent());
    component.fetchedMetadata = createMetadata({
      provider: 'Audible',
      thumbnailUrl: 'https://covers.example/audiobook.jpg',
    });
    component.book = createBook();
    component.copiedFields = {title: true, audiobookThumbnailUrl: true};
    component.metadataForm.get('titleLocked')?.setValue(false);
    component.metadataForm.get('title')?.enable();
    component.metadataForm.get('title')?.setValue('Updated Title');

    component.onSave();

    expect(updateBookMetadata).toHaveBeenCalledWith(21, expect.objectContaining({
      metadata: expect.objectContaining({title: 'Updated Title'}),
      clearFlags: expect.any(Object),
    }), false, 'REPLACE_WHEN_PROVIDED');
    expect(uploadAudiobookCoverFromUrl).toHaveBeenCalledWith(21, 'https://covers.example/audiobook.jpg');
    expect(component.isSaving).toBe(false);
    expect(component.isValueSaved('title')).toBe(true);
    expect(component.isValueSaved('audiobookThumbnailUrl')).toBe(true);
    expect(messageAdd).toHaveBeenCalledWith({
      severity: 'info',
      summary: 'metadata.picker.toast.successSummary',
      detail: 'metadata.picker.toast.metadataUpdated',
    });

    component.toggleLock('thumbnailUrl');
    expect(component.metadataForm.get('coverLocked')?.value).toBe(true);
    expect(component.metadataForm.get('thumbnailUrl')?.disabled).toBe(true);

    component.lockAll();
    component.unlockAll();

    expect(updateBookMetadata).toHaveBeenCalledTimes(4);
  });

  it('covers hover state, back navigation, dual-cover helpers, and comic visibility helpers', () => {
    const component = TestBed.runInInjectionContext(() => new MetadataPickerComponent());
    const goBackEmit = vi.spyOn(component.goBack, 'emit');
    const book = createBook({}, {
      isPhysical: true,
      primaryFile: undefined,
      alternativeFormats: [{id: 2, bookId: 21, bookType: 'AUDIOBOOK'}],
    });

    component.fetchedMetadata = createMetadata({
      provider: 'comicvine',
      narrator: 'Narrator',
      comicMetadata: {issueNumber: '7'},
    });
    component.book = book;
    component.copiedFields = {title: true};
    component.savedFields = {title: false};

    component.onMouseEnter('title');
    expect(component.hoveredFields['title']).toBe(true);

    component.onMouseLeave('title');
    expect(component.hoveredFields['title']).toBe(false);

    component.goBackClick();
    expect(goBackEmit).toHaveBeenCalledWith(true);

    expect(component.isAudibleProvider()).toBe(false);
    component.fetchedMetadata = createMetadata({provider: 'Audible', narrator: 'Narrator', comicMetadata: {issueNumber: '7'}});
    expect(component.isAudibleProvider()).toBe(true);
    expect(component.getFetchedAudiobookValue('narrator')).toBe('Narrator');
    expect(component.getFetchedComicValue('issueNumber')).toBe('7');

    expect(component.hasEbookFormat(book)).toBe(true);
    expect(component.hasAudiobookFormat(book)).toBe(true);
    expect(component.supportsDualCovers(book)).toBe(true);
    expect(component.hasAnyFetchedComicData()).toBe(true);
    expect(component.hasAnyCurrentComicData()).toBe(true);
    expect(component.shouldShowComicSection()).toBe(true);
  });
});
