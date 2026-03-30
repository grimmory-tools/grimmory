import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';

import {AppSettings} from '../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {Book} from '../../../book/model/book.model';
import {BookService} from '../../../book/service/book.service';
import {MetadataRefreshType} from '../../model/request/metadata-refresh-type.enum';
import {MultiBookMetadataFetchComponent} from './multi-book-metadata-fetch-component';

describe('MultiBookMetadataFetchComponent', () => {
  const appSettings = signal<AppSettings | null>(null);
  const getBooksByIds = vi.fn((bookIds: number[]) => bookIds.map(bookId => ({
    id: bookId,
    title: `Book ${bookId}`,
    libraryId: 1,
    libraryName: 'Library',
  } satisfies Book)));

  beforeEach(() => {
    getBooksByIds.mockClear();
    appSettings.set(null);

    TestBed.configureTestingModule({
      providers: [
        {
          provide: DynamicDialogConfig,
          useValue: {
            data: {
              bookIds: [3, 5],
              metadataRefreshType: MetadataRefreshType.BOOKS,
            },
          },
        },
        {provide: DynamicDialogRef, useValue: {close: vi.fn()}},
        {provide: BookService, useValue: {getBooksByIds}},
        {provide: AppSettingsService, useValue: {appSettings}},
      ]
    });
  });

  it('reads dialog data and resolves the books to show on construction', () => {
    const component = TestBed.runInInjectionContext(() => new MultiBookMetadataFetchComponent());

    expect(component.bookIds).toEqual([3, 5]);
    expect(component.metadataRefreshType).toBe(MetadataRefreshType.BOOKS);
    expect(getBooksByIds).toHaveBeenCalledWith([3, 5]);
    expect(component.booksToShow).toEqual([
      {id: 3, title: 'Book 3', libraryId: 1, libraryName: 'Library'},
      {id: 5, title: 'Book 5', libraryId: 1, libraryName: 'Library'},
    ]);
  });

  it('adopts the default metadata refresh options when app settings become available', () => {
    const component = TestBed.runInInjectionContext(() => new MultiBookMetadataFetchComponent());

    expect(component.currentMetadataOptions).toBeUndefined();

    appSettings.set({
      defaultMetadataRefreshOptions: {
        libraryId: 7,
        refreshCovers: true,
        mergeCategories: false,
        reviewBeforeApply: true,
      },
    } as AppSettings);
    TestBed.flushEffects();

    expect(component.currentMetadataOptions).toEqual({
      libraryId: 7,
      refreshCovers: true,
      mergeCategories: false,
      reviewBeforeApply: true,
    });
  });
});
