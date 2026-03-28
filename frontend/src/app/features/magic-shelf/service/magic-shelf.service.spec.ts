import {HttpTestingController} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {createAuthServiceStub, createQueryClientHarness, flushSignalAndQueryEffects} from '../../../core/testing/query-testing';
import type {Book} from '../../book/model/book.model';
import {AuthService} from '../../../shared/service/auth.service';
import type {GroupRule} from '../component/magic-shelf-component';
import {BookService} from '../../book/service/book.service';
import {BookRuleEvaluatorService} from './book-rule-evaluator.service';
import {MagicShelfService, type MagicShelf} from './magic-shelf.service';

function buildMagicShelf(overrides: Partial<MagicShelf> = {}): MagicShelf {
  return {
    id: 1,
    name: 'Favorites',
    icon: 'pi pi-star',
    iconType: 'PRIME_NG',
    filterJson: JSON.stringify(buildGroupRule()),
    isPublic: false,
    ...overrides,
  };
}

function buildGroupRule(overrides: Partial<GroupRule> = {}): GroupRule {
  return {
    name: 'Favorites',
    type: 'group',
    join: 'and',
    rules: [],
    ...overrides,
  };
}

function buildBook(id: number, overrides: Partial<Book> = {}): Book {
  return {
    id,
    libraryId: 1,
    libraryName: 'Main Library',
    metadata: {
      bookId: id,
      title: `Book ${id}`,
    },
    ...overrides,
  };
}

async function flushShelvesQuery(): Promise<void> {
  await Promise.resolve();
  flushSignalAndQueryEffects();
  await new Promise(resolve => setTimeout(resolve, 0));
  flushSignalAndQueryEffects();
  await Promise.resolve();
  flushSignalAndQueryEffects();
}

describe('MagicShelfService', () => {
  let service: MagicShelfService;
  let httpTestingController: HttpTestingController;
  let authService: ReturnType<typeof createAuthServiceStub>;
  let queryClientHarness: ReturnType<typeof createQueryClientHarness>;
  let bookService: {
    books: ReturnType<typeof vi.fn>;
  };
  let ruleEvaluatorService: {
    evaluateGroup: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    authService = createAuthServiceStub();
    queryClientHarness = createQueryClientHarness();
    queryClientHarness.queryClient.setDefaultOptions({
      queries: {
        retry: false,
      },
    });

    bookService = {
      books: vi.fn(() => []),
    };
    ruleEvaluatorService = {
      evaluateGroup: vi.fn(() => false),
    };

    vi.spyOn(queryClientHarness.queryClient, 'invalidateQueries').mockResolvedValue(undefined);
    vi.spyOn(queryClientHarness.queryClient, 'removeQueries').mockImplementation(() => undefined);

    TestBed.configureTestingModule({
      providers: [
        ...queryClientHarness.providers,
        MagicShelfService,
        {provide: AuthService, useValue: authService},
        {provide: BookService, useValue: bookService},
        {provide: BookRuleEvaluatorService, useValue: ruleEvaluatorService},
      ],
    });

    service = TestBed.inject(MagicShelfService);
    httpTestingController = TestBed.inject(HttpTestingController);
    flushSignalAndQueryEffects();
  });

  afterEach(() => {
    httpTestingController.verify();
    queryClientHarness.queryClient.clear();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('eagerly fetches shelves and hydrates the computed shelves signal', async () => {
    const shelves = [
      buildMagicShelf({id: 1, name: 'Reading'}),
      buildMagicShelf({id: 2, name: 'Finished', isPublic: true}),
    ];

    expect(service.shelves()).toEqual([]);
    expect(service.isShelvesLoading()).toBe(true);
    expect(service.shelvesError()).toBeNull();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/magic-shelves'));
    expect(request.request.method).toBe('GET');
    request.flush(shelves);
    await flushShelvesQuery();

    expect(service.shelves()).toEqual(shelves);
    expect(service.isShelvesLoading()).toBe(false);
    expect(service.shelvesError()).toBeNull();
  });

  it('removes shelf queries when the auth token is cleared', async () => {
    const removeQueriesSpy = vi.spyOn(queryClientHarness.queryClient, 'removeQueries').mockImplementation(() => undefined);

    httpTestingController.expectOne(req => req.url.endsWith('/api/magic-shelves')).flush([
      buildMagicShelf({id: 7, name: 'Cached'}),
    ]);
    await flushShelvesQuery();

    authService.token.set(null);
    flushSignalAndQueryEffects();

    expect(removeQueriesSpy).toHaveBeenCalledWith({queryKey: ['magicShelves']});
  });

  it('invalidates shelf queries after save and serializes the group payload', () => {
    const invalidateQueriesSpy = vi.spyOn(queryClientHarness.queryClient, 'invalidateQueries').mockResolvedValue(undefined);
    const group = buildGroupRule({
      rules: [
        {
          field: 'title',
          operator: 'contains',
          value: 'magic',
        },
      ],
    });

    httpTestingController.expectOne(req => req.url.endsWith('/api/magic-shelves')).flush([]);

    service.saveShelf({
      name: 'Magic',
      icon: 'pi pi-bolt',
      iconType: 'PRIME_NG',
      group,
      isPublic: true,
    }).subscribe();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/magic-shelves'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(expect.objectContaining({
      name: 'Magic',
      icon: 'pi pi-bolt',
      iconType: 'PRIME_NG',
      filterJson: JSON.stringify(group),
      isPublic: true,
    }));
    request.flush(buildMagicShelf({id: 11, name: 'Magic', filterJson: JSON.stringify(group), isPublic: true}));

    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: ['magicShelves'], exact: true});
  });

  it('invalidates shelf queries after delete', () => {
    const invalidateQueriesSpy = vi.spyOn(queryClientHarness.queryClient, 'invalidateQueries').mockResolvedValue(undefined);

    httpTestingController.expectOne(req => req.url.endsWith('/api/magic-shelves')).flush([]);

    service.deleteShelf(11).subscribe();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/magic-shelves/11'));
    expect(request.request.method).toBe('DELETE');
    request.flush(null);

    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: ['magicShelves'], exact: true});
  });

  it('finds shelves by id from the hydrated query state', async () => {
    const readingShelf = buildMagicShelf({id: 1, name: 'Reading'});
    const archiveShelf = buildMagicShelf({id: 2, name: 'Archive'});

    httpTestingController.expectOne(req => req.url.endsWith('/api/magic-shelves')).flush([
      readingShelf,
      archiveShelf,
    ]);
    await flushShelvesQuery();

    expect(service.findShelfById(2)).toEqual(archiveShelf);
    expect(service.findShelfById(999)).toBeUndefined();
  });

  it('returns zero and logs when a shelf filter contains invalid JSON', async () => {
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    httpTestingController.expectOne(req => req.url.endsWith('/api/magic-shelves')).flush([
      buildMagicShelf({id: 1, filterJson: '{invalid-json'}),
    ]);
    await flushShelvesQuery();

    expect(service.getBookCountValue(1)).toBe(0);
    expect(bookService.books).not.toHaveBeenCalled();
    expect(ruleEvaluatorService.evaluateGroup).not.toHaveBeenCalled();
    expect(consoleErrorSpy).toHaveBeenCalledWith('Invalid filter JSON', expect.any(SyntaxError));
  });

  it('counts matching books against the parsed shelf rule group', async () => {
    const group = buildGroupRule({
      rules: [
        {
          field: 'title',
          operator: 'contains',
          value: 'book',
        },
      ],
    });
    const books = [
      buildBook(1),
      buildBook(2),
      buildBook(3),
    ];

    bookService.books.mockReturnValue(books);
    ruleEvaluatorService.evaluateGroup.mockImplementation((book: Book, parsedGroup: GroupRule, allBooks: Book[]) =>
      parsedGroup.name === group.name && allBooks === books && book.id !== 2
    );

    httpTestingController.expectOne(req => req.url.endsWith('/api/magic-shelves')).flush([
      buildMagicShelf({id: 5, filterJson: JSON.stringify(group)}),
    ]);
    await flushShelvesQuery();

    expect(service.getBookCountValue(5)).toBe(2);
    expect(ruleEvaluatorService.evaluateGroup).toHaveBeenCalledTimes(3);
    expect(ruleEvaluatorService.evaluateGroup).toHaveBeenNthCalledWith(1, books[0], group, books);
    expect(ruleEvaluatorService.evaluateGroup).toHaveBeenNthCalledWith(2, books[1], group, books);
    expect(ruleEvaluatorService.evaluateGroup).toHaveBeenNthCalledWith(3, books[2], group, books);
  });
});
