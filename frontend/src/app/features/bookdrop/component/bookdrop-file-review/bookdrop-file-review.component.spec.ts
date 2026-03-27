import {DestroyRef, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ActivatedRoute} from '@angular/router';
import {FormGroup} from '@angular/forms';
import {TranslocoService} from '@jsverse/transloco';
import {ConfirmationService, MessageService} from 'primeng/api';
import {of} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {DialogLauncherService} from '../../../../shared/services/dialog-launcher.service';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {BookdropFileReviewComponent, BookdropFileUI} from './bookdrop-file-review.component';
import {BookdropFile, BookdropFileStatus, BookdropService} from '../../service/bookdrop.service';
import {Library} from '../../../book/model/library.model';
import {LibraryService} from '../../../book/service/library.service';

describe('BookdropFileReviewComponent', () => {
  const add = vi.fn();
  const confirm = vi.fn();
  const getPendingFiles = vi.fn();
  const discardFiles = vi.fn();
  const finalizeImport = vi.fn();
  const rescan = vi.fn();
  const openBookdropFinalizeResultDialog = vi.fn();
  const openDialog = vi.fn();
  const openDirectoryPickerDialog = vi.fn();
  const setPageTitle = vi.fn();
  const translate = vi.fn((key: string, params?: Record<string, unknown>) => {
    if (!params) {
      return key;
    }

    return `${key}:${Object.entries(params).map(([paramKey, value]) => `${paramKey}=${value}`).join(',')}`;
  });

  const libraries = signal<Library[]>([]);
  const appSettings = signal<unknown>(null);

  function makeLibrary(id: number, name: string, pathIds: number[]): Library {
    return {
      id,
      name,
      watch: false,
      paths: pathIds.map((pathId, index) => ({id: pathId, path: `${name}-${index + 1}`})),
    };
  }

  function makeFile(id: number): BookdropFile {
    return {
      id,
      fileName: `file-${id}.epub`,
      filePath: `/incoming/file-${id}.epub`,
      fileSize: 1024,
      originalMetadata: {bookId: id, title: `Book ${id}`},
      createdAt: '2026-03-26T00:00:00Z',
      updatedAt: '2026-03-26T00:00:00Z',
      status: BookdropFileStatus.ACCEPTED,
      showDetails: false,
    };
  }

  function makeFileUi(
    id: number,
    overrides: Partial<BookdropFileUI> = {}
  ): BookdropFileUI {
    return {
      file: makeFile(id),
      metadataForm: new FormGroup({}),
      copiedFields: {},
      savedFields: {},
      selected: false,
      showDetails: false,
      selectedLibraryId: null,
      selectedPathId: null,
      availablePaths: [],
      ...overrides,
    };
  }

  function createComponent(): BookdropFileReviewComponent {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: BookdropService,
          useValue: {
            getPendingFiles,
            discardFiles,
            finalizeImport,
            rescan,
          },
        },
        {
          provide: LibraryService,
          useValue: {
            libraries: libraries.asReadonly(),
          },
        },
        {provide: ConfirmationService, useValue: {confirm}},
        {provide: DestroyRef, useValue: {onDestroy: vi.fn()}},
        {
          provide: DialogLauncherService,
          useValue: {
            openBookdropFinalizeResultDialog,
            openDialog,
            openDirectoryPickerDialog,
          },
        },
        {provide: AppSettingsService, useValue: {appSettings: appSettings.asReadonly()}},
        {provide: MessageService, useValue: {add}},
        {provide: UrlHelperService, useValue: {getBookdropCoverUrl: vi.fn((id: number) => `/covers/${id}`)}},
        {provide: ActivatedRoute, useValue: {queryParams: of({}), snapshot: {queryParams: {}}}},
        {provide: PageTitleService, useValue: {setPageTitle}},
        {provide: TranslocoService, useValue: {translate}},
      ],
    });

    return TestBed.runInInjectionContext(() => new BookdropFileReviewComponent());
  }

  beforeEach(() => {
    add.mockClear();
    confirm.mockClear();
    getPendingFiles.mockReset();
    discardFiles.mockReset();
    finalizeImport.mockReset();
    rescan.mockReset();
    openBookdropFinalizeResultDialog.mockClear();
    openDialog.mockClear();
    openDirectoryPickerDialog.mockClear();
    setPageTitle.mockClear();
    translate.mockClear();
    libraries.set([]);
    appSettings.set(null);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('derives defaults, selection counts, and finalize eligibility from both selection modes', () => {
    libraries.set([
      makeLibrary(1, 'Alpha', [10]),
      makeLibrary(2, 'Beta', [20, 21]),
    ]);

    const component = createComponent();
    const first = makeFileUi(1, {selected: true, selectedLibraryId: '1', selectedPathId: '10'});
    const second = makeFileUi(2, {selected: true, selectedLibraryId: '1', selectedPathId: '10'});
    const third = makeFileUi(3, {selected: false});

    component.fileUiCache = {1: first, 2: second, 3: third};
    component.bookdropFileUis = [first, second, third];

    component.defaultLibraryId = '1';
    expect(component.defaultPathId).toBe('10');
    expect(component.selectedLibraryPaths).toEqual([{label: 'Alpha-1', value: '10'}]);
    expect(component.canApplyDefaults).toBe(true);

    expect(component.hasSelectedFiles).toBe(true);
    expect(component.selectedCount).toBe(2);
    expect(component.canFinalize).toBe(true);

    second.selectedPathId = null;
    component.fileUiCache[2].selectedPathId = null;
    expect(component.canFinalize).toBe(false);

    second.selectedPathId = '10';
    component.fileUiCache[2].selectedPathId = '10';
    component.selectAllAcrossPages = true;
    component.totalRecords = 10;
    component.excludedFiles.add(3);

    expect(component.hasSelectedFiles).toBe(true);
    expect(component.selectedCount).toBe(9);
    expect(component.canFinalize).toBe(true);

    component.fileUiCache[2].selectedPathId = null;
    expect(component.canFinalize).toBe(false);
  });

  it('keeps the current-page selection state in sync with select-all changes', () => {
    const component = createComponent();
    const first = makeFileUi(1);
    const second = makeFileUi(2);

    component.fileUiCache = {1: first, 2: second};
    component.bookdropFileUis = [first, second];

    component.selectAll(true);
    expect(component.selectAllAcrossPages).toBe(true);
    expect(component.excludedFiles.size).toBe(0);
    expect(component.bookdropFileUis.map(file => file.selected)).toEqual([true, true]);
    expect(Object.values(component.fileUiCache).map(file => file.selected)).toEqual([true, true]);

    component.toggleFileSelection(1, false);
    expect(component.excludedFiles.has(1)).toBe(true);
    expect(component.fileUiCache[1].selected).toBe(false);
    expect(component.bookdropFileUis[0].selected).toBe(false);

    component.toggleFileSelection(1, true);
    expect(component.excludedFiles.has(1)).toBe(false);
    expect(component.fileUiCache[1].selected).toBe(true);

    component.selectAll(false);
    expect(component.selectAllAcrossPages).toBe(false);
    expect(component.excludedFiles.size).toBe(0);
    expect(component.bookdropFileUis.map(file => file.selected)).toEqual([false, false]);
    expect(Object.values(component.fileUiCache).map(file => file.selected)).toEqual([false, false]);
  });

  it('warns instead of prompting when destructive actions have no selected files', () => {
    const component = createComponent();
    component.bookdropFileUis = [];
    component.fileUiCache = {};

    component.confirmReset();
    component.confirmFinalize();
    component.confirmDelete();

    expect(confirm).not.toHaveBeenCalled();
    expect(add).toHaveBeenCalledTimes(3);
    expect(add).toHaveBeenNthCalledWith(1, expect.objectContaining({
      severity: 'warn',
      summary: 'bookdrop.fileReview.toast.noFilesSelectedSummary',
      detail: 'bookdrop.fileReview.toast.noFilesResetDetail',
    }));
    expect(add).toHaveBeenNthCalledWith(2, expect.objectContaining({
      severity: 'warn',
      summary: 'bookdrop.fileReview.toast.noFilesSelectedSummary',
      detail: 'bookdrop.fileReview.toast.noFilesFinalizeDetail',
    }));
    expect(add).toHaveBeenNthCalledWith(3, expect.objectContaining({
      severity: 'warn',
      summary: 'bookdrop.fileReview.toast.noFilesSelectedSummary',
      detail: 'bookdrop.fileReview.toast.noFilesDeleteDetail',
    }));
  });
});
