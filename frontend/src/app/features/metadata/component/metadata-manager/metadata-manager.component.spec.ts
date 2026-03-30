import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {BehaviorSubject, of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TranslocoService} from '@jsverse/transloco';
import {ActivatedRoute, Router} from '@angular/router';
import {MessageService} from 'primeng/api';

import {Book} from '../../../book/model/book.model';
import {BookMetadataManageService} from '../../../book/service/book-metadata-manage.service';
import {BookService} from '../../../book/service/book.service';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {MetadataManagerComponent} from './metadata-manager.component';

describe('MetadataManagerComponent', () => {
  const books = signal<Book[]>([]);
  const isBooksLoading = signal(false);
  const add = vi.fn();
  const navigate = vi.fn(() => Promise.resolve(true));
  const setPageTitle = vi.fn();
  const translate = vi.fn((key: string) => `translated:${key}`);
  const consolidateMetadata = vi.fn(() => of(void 0));
  const deleteMetadata = vi.fn(() => of(void 0));
  let queryParams$: BehaviorSubject<Record<string, unknown>>;

  beforeEach(() => {
    add.mockClear();
    navigate.mockClear();
    setPageTitle.mockClear();
    translate.mockClear();
    consolidateMetadata.mockClear();
    deleteMetadata.mockClear();
    books.set([]);
    isBooksLoading.set(false);
    queryParams$ = new BehaviorSubject<Record<string, unknown>>({});

    TestBed.configureTestingModule({
      providers: [
        {provide: BookService, useValue: {books, isBooksLoading}},
        {provide: BookMetadataManageService, useValue: {consolidateMetadata, deleteMetadata}},
        {provide: PageTitleService, useValue: {setPageTitle}},
        {provide: TranslocoService, useValue: {translate}},
        {provide: MessageService, useValue: {add}},
        {provide: ActivatedRoute, useValue: {queryParams: queryParams$}},
        {provide: Router, useValue: {navigate}},
      ]
    });
  });

  function createBook(id: number, metadata: Partial<Book['metadata']> = {}): Book {
    return {
      id,
      title: `Book ${id}`,
      libraryId: 1,
      libraryName: 'Library',
      metadata: {
        bookId: id,
        ...metadata,
      },
    };
  }

  function createComponent() {
    return TestBed.runInInjectionContext(() => new MetadataManagerComponent());
  }

  it('aggregates metadata once books finish loading', () => {
    books.set([
      createBook(1, {
        authors: ['Alice', 'Bob'],
        categories: ['Fantasy'],
        moods: ['Cozy'],
        tags: ['magic'],
        seriesName: 'Series A',
        publisher: 'Pub One',
        language: 'en',
      }),
      createBook(2, {
        authors: ['Alice'],
        categories: ['Fantasy', 'Adventure'],
        moods: ['Epic'],
        tags: ['magic', 'epic'],
        seriesName: 'Series A',
        publisher: 'Pub Two',
        language: 'fr',
      }),
    ]);

    const component = createComponent();
    TestBed.flushEffects();

    expect(component.loading).toBe(false);
    expect(component.authors[0]).toEqual({value: 'Alice', count: 2, bookIds: [1, 2], selected: false});
    expect(component.categories[0]).toEqual({value: 'Fantasy', count: 2, bookIds: [1, 2], selected: false});
    expect(component.series).toEqual([{value: 'Series A', count: 2, bookIds: [1, 2], selected: false}]);
    expect(component.publishers).toEqual([
      {value: 'Pub One', count: 1, bookIds: [1], selected: false},
      {value: 'Pub Two', count: 1, bookIds: [2], selected: false},
    ]);
    expect(component.languages).toEqual([
      {value: 'en', count: 1, bookIds: [1], selected: false},
      {value: 'fr', count: 1, bookIds: [2], selected: false},
    ]);
  });

  it('waits for the book list to finish loading before extracting metadata', () => {
    books.set([createBook(1, {authors: ['Alice']})]);
    isBooksLoading.set(true);

    const component = createComponent();
    TestBed.flushEffects();

    expect(component.loading).toBe(true);
    expect(component.authors).toEqual([]);

    isBooksLoading.set(false);
    TestBed.flushEffects();

    expect(component.loading).toBe(false);
    expect(component.authors).toEqual([{value: 'Alice', count: 1, bookIds: [1], selected: false}]);
  });

  it('tracks valid and invalid tabs from query params and updates the page title', () => {
    queryParams$.next({tab: 'tags'});

    const component = createComponent();
    component.ngOnInit();

    expect(component.activeTab).toBe('tags');
    expect(setPageTitle).toHaveBeenCalledWith('translated:metadata.manager.title: translated:metadata.manager.tabs.tag');

    queryParams$.next({tab: 'invalid'});

    expect(component.activeTab).toBe('authors');
    expect(navigate).toHaveBeenCalledWith([], {
      relativeTo: expect.anything(),
      queryParams: {tab: 'authors'},
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  });

  it('navigates when the active tab changes through the setter', () => {
    const component = createComponent();
    component.ngOnInit();
    navigate.mockClear();
    setPageTitle.mockClear();

    component.activeTab = 'categories';

    expect(component.activeTab).toBe('categories');
    expect(setPageTitle).toHaveBeenCalledWith('translated:metadata.manager.title: translated:metadata.manager.tabs.genre');
    expect(navigate).toHaveBeenCalledWith([], {
      relativeTo: expect.anything(),
      queryParams: {tab: 'categories'},
      queryParamsHandling: 'merge',
    });
  });

  it('toggles selections, selects similar values, filters tables, and navigates to filtered books', () => {
    const component = createComponent();
    component.authors = [
      {value: 'Alice', count: 2, bookIds: [1, 2], selected: false},
      {value: 'Al', count: 1, bookIds: [3], selected: false},
      {value: 'Bob', count: 1, bookIds: [4], selected: false},
    ];
    const filterGlobal = vi.fn();

    component.toggleAll('authors', true);
    expect(component.authors.every(item => item.selected)).toBe(true);
    expect(component.selectAllAuthors).toBe(true);

    component.clearSelection('authors');
    component.selectSimilar('authors', ' alice ');

    expect(component.authors[0].selected).toBe(true);
    expect(component.authors[1].selected).toBe(true);
    expect(component.authors[2].selected).toBe(false);

    component.filterGlobal({target: {value: 'magic'}} as unknown as Event, {filterGlobal});
    expect(filterGlobal).toHaveBeenCalledWith('magic', 'contains');

    component.onMetadataClick('authors', 'A&B');
    expect(navigate).toHaveBeenCalledWith(['/all-books'], {
      queryParams: {
        view: 'grid',
        sort: 'title',
        direction: 'asc',
        sidebar: true,
        filter: 'author:A%26B',
      }
    });
  });
});
