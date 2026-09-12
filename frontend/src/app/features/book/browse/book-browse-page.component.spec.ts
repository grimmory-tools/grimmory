import {HttpTestingController} from '@angular/common/http/testing';
import {Component, signal, type WritableSignal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {Router, provideRouter} from '@angular/router';
import {RouterTestingHarness} from '@angular/router/testing';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {API_CONFIG} from '../../../core/config/api-config';
import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {createQueryClientHarness, flushQueryAsync} from '../../../core/testing/query-testing';
import {BrowseGridComponent} from '../../../shared/browse/grid/grid.component';
import {BrowseGridViewportComponent} from '../../../shared/browse/grid/grid-viewport.component';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {SEARCH_DEBOUNCE_MS} from '../../../shared/util/search-terms';
import {UrlHelperService} from '../../../shared/service/url-helper.service';
import {type BrowseLink} from '../../../core/data/browse.models';
import {type BookSummary} from '../data/book-response.models';
import {ConfirmationService, MessageService} from '@openng/optimus-ui/api';
import {DialogService} from '@openng/optimus-ui/dynamicdialog';

import {BookFileService} from '../service/book-file.service';
import {LibraryService} from '../service/library.service';
import {LibraryShelfMenuService} from '../service/library-shelf-menu.service';
import {MagicShelfService} from '../../magic-shelf/service/magic-shelf.service';
import {ShelfDefinitionQueryService} from '../data/shelf-definition-query.service';
import {BookDialogHelperService} from '../service/book-dialog-helper.service';
import {UserService} from '../../settings/user-management/user.service';
import {EmailService} from '../../settings/email-v2/email.service';
import {type BookSortTerm} from '../data/book-query-params';
import {BookBrowsePageComponent} from './book-browse-page.component';
import {BookBrowseFilterPageComponent} from './book-browse-filter-page.component';

const PAGE_URL = `${API_CONFIG.BASE_URL}/api/v1/books/page`;
const FACETS_URL = `${API_CONFIG.BASE_URL}/api/v1/books/facets`;

@Component({selector: 'app-test-route', template: ''})
class TestRouteComponent {}

function bookPage(ids: number[], totalElements = ids.length, links: BrowseLink[] = []) {
  return {
    content: ids.map(id => ({id, libraryId: 1, libraryName: 'Library'})),
    page: {number: 0, size: 60, totalElements, totalPages: Math.ceil(totalElements / 60)},
    links,
  };
}

interface PageHarness {
  detailLineFor(book: BookSummary): string | null;
  selection: {
    count(): number;
    toggle(book: BookSummary, index: number, shiftKey: boolean): void;
  };
  preferences: {setSortTerms(terms: readonly BookSortTerm[]): void};
  searchDraft: {set(value: string): void};
  onSortDirectionChange(term: BookSortTerm): void;
  onClearQuery(): void;
}

const GLOBAL_PREFERENCE = {
  sortKey: 'title', sortDir: 'ASC', view: 'GRID', coverSize: 1, seriesCollapsed: false, overlayBookType: true,
};

function userFixture(permissions: Record<string, boolean>, id?: number, global: Record<string, unknown> = {}) {
  return {
    ...(id === undefined ? {} : {id}),
    permissions,
    userSettings: {entityViewPreferences: {global: {...GLOBAL_PREFERENCE, ...global}, overrides: []}},
  };
}

describe('BookBrowsePageComponent', () => {
  let fixture: ComponentFixture<BookBrowsePageComponent>;

  let http: HttpTestingController;
  let queryClient: QueryClient;
  let currentUser: WritableSignal<ReturnType<typeof userFixture> | null>;
  let updateUserSetting: ReturnType<typeof vi.fn>;

  function grid(): BrowseGridComponent<BookSummary> {
    return fixture.debugElement.query(By.directive(BrowseGridComponent))
      .componentInstance as BrowseGridComponent<BookSummary>;
  }

  function viewport(): BrowseGridViewportComponent<BookSummary> {
    return fixture.debugElement.query(By.directive(BrowseGridViewportComponent))
      .componentInstance as BrowseGridViewportComponent<BookSummary>;
  }

  function page(): PageHarness {
    return fixture.componentInstance as unknown as PageHarness;
  }

  function flushFacetRegistry(): void {
    for (const request of http.match(candidate => candidate.url === FACETS_URL)) {
      request.flush({
        links: [{rel: 'self', href: '/api/v1/books/facets', type: 'application/json'}],
        facets: [{
          metadata: {rel: 'sort', key: 'sort', title: 'Sort'},
          links: [
            {rel: 'sort', href: '', type: '', title: 'title ascending', value: 'title'},
            {rel: 'sort', href: '', type: '', title: 'title descending', value: '-title'},
            {rel: 'sort', href: '', type: '', title: 'page count', value: 'pageCount'},
          ],
        }],
      });
    }
  }

  function expectInitialPageRequest() {
    flushFacetRegistry();
    return http.expectOne(request =>
      request.url === PAGE_URL &&
      request.params.get('sort') === 'title' &&
      request.params.get('size') === '60' &&
      !request.params.has('page') &&
      !request.params.has('cursor'),
    );
  }

  async function routeTo(url: string): Promise<RouterTestingHarness> {
    const routerHarness = await RouterTestingHarness.create();
    await routerHarness.navigateByUrl(url);
    routerHarness.detectChanges();
    return routerHarness;
  }

  beforeEach(() => {
    const harness = createQueryClientHarness();
    queryClient = harness.queryClient;
    queryClient.setDefaultOptions({queries: {retry: false}});
    currentUser = signal(null);
    updateUserSetting = vi.fn();

    TestBed.configureTestingModule({
      imports: [BookBrowsePageComponent, getTranslocoModule()],
      providers: [
        ...harness.providers,
        provideRouter([
          {path: '', component: TestRouteComponent},
          {path: 'book/:bookId', component: TestRouteComponent},
          {path: 'library/:libraryId/books', children: [
            {path: '', component: BookBrowsePageComponent},
            {path: 'filter', component: BookBrowseFilterPageComponent},
          ]},
          {path: 'magic-shelf/:magicShelfId/books', children: [{path: '', component: BookBrowsePageComponent}]},
          {path: 'unshelved-books', children: [
            {path: '', component: BookBrowsePageComponent, data: {browseScope: 'unshelved'}},
          ]},
        ]),
        {
          provide: UrlHelperService,
          useValue: {
            getThumbnailUrl: (id: number) => `/thumb/${id}`,
            getAudiobookThumbnailUrl: (id: number) => `/audio-thumb/${id}`,
          },
        },
        {
          provide: ShelfDefinitionQueryService,
          useValue: {
            definitions: () => ({
              queryKey: ['shelves', 'query', 'definitions'] as const,
              queryFn: () => Promise.resolve([]),
            }),
          },
        },
        {provide: MagicShelfService, useValue: {shelves: () => [{id: 9, name: 'Witchy Reads'}]}},
        {provide: LibraryService, useValue: {libraries: () => [{id: 3, name: 'Cookbooks'}]}},
        {provide: LibraryShelfMenuService, useValue: {}},
        {provide: UserService, useValue: {currentUser, updateUserSetting}},
        {provide: AppSettingsService, useValue: {appSettings: signal({diskType: 'LOCAL'})}},
        {provide: BookDialogHelperService, useValue: {}},
        {provide: BookFileService, useValue: {}},
        {provide: EmailService, useValue: {}},
        {provide: ConfirmationService, useValue: {confirm: () => undefined}},
        {provide: MessageService, useValue: {add: () => undefined}},
        {provide: DialogService, useValue: {open: () => null}},
      ],
    });

    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
    history.replaceState({}, '', '/');
  });

  it('pins the route scope onto page and facet requests, over any URL facet for the same key', async () => {
    const routerHarness = await routeTo('/library/3/books?facet=genre:Fantasy&facet=library:99');
    for (const facets of http.match(candidate => candidate.url === FACETS_URL)) {
      expect(facets.request.params.getAll('facet')).toEqual(['library:3']);
      facets.flush({facets: []});
    }
    const library = http.expectOne(candidate => candidate.url === PAGE_URL);
    expect(library.request.params.getAll('facet')).toEqual(['genre:Fantasy', 'library:3']);
    library.flush(bookPage([1], 1));
    await flushQueryAsync();

    await routerHarness.navigateByUrl('/magic-shelf/9/books');
    routerHarness.detectChanges();
    flushFacetRegistry();
    const magic = http.expectOne(candidate => candidate.url === PAGE_URL);
    expect(magic.request.params.getAll('facet')).toEqual(['shelf:magic:9']);
    magic.flush(bookPage([1], 1));
    await flushQueryAsync();

    await routerHarness.navigateByUrl('/unshelved-books');
    routerHarness.detectChanges();
    flushFacetRegistry();
    const unshelved = http.expectOne(candidate => candidate.url === PAGE_URL);
    expect(unshelved.request.params.getAll('facet')).toEqual(['shelf_status:unshelved']);
    unshelved.flush(bookPage([1], 1));
    await flushQueryAsync();
  });

  it('serves a remount from the shared cache and ignores view-only URL changes', async () => {
    fixture = TestBed.createComponent(BookBrowsePageComponent);
    fixture.detectChanges();
    expectInitialPageRequest().flush(bookPage([1, 2, 3], 100_000));
    await flushQueryAsync();

    fixture.destroy();
    fixture = TestBed.createComponent(BookBrowsePageComponent);
    fixture.detectChanges();
    await flushQueryAsync();

    http.expectNone(candidate => candidate.url === PAGE_URL);
    expect(grid().items().map(item => item.id)).toEqual([1, 2, 3]);

    await TestBed.inject(Router).navigate([], {queryParams: {view: 'grid'}});
    await fixture.whenStable();
    await flushQueryAsync(1);

    http.expectNone(candidate => candidate.url === PAGE_URL);
  });

  it('follows the opaque next link and retries a continuation failure', async () => {
    fixture = TestBed.createComponent(BookBrowsePageComponent);
    fixture.detectChanges();
    expectInitialPageRequest().flush(bookPage([1], 600, [{
      rel: ['next'],
      href: '/api/v1/books/page?cursor=opaque%2Bcursor&sort=title&size=60',
      type: 'application/json',
    }]));
    await flushQueryAsync();

    viewport().renderedRange.set({start: 0, end: 0});
    await flushQueryAsync(1);

    const nextUrl = `${API_CONFIG.BASE_URL}/api/v1/books/page?cursor=opaque%2Bcursor&sort=title&size=60`;
    http.expectOne(nextUrl).flush('Could not load', {status: 400, statusText: 'Bad Request'});
    await flushQueryAsync();

    expect(grid().status()).toBe('success');
    expect(grid().nextPageError()).toBe(true);
    expect(grid().items().map(item => item.id)).toEqual([1]);

    grid().retryNextPage.emit();
    http.expectOne(nextUrl).flush(bookPage([2], 600));
    await flushQueryAsync();

    expect(grid().nextPageError()).toBe(false);
    expect(grid().items().map(item => item.id)).toEqual([1, 2]);
  });

  it('passes sort, search and facets to the endpoint exactly while the URL keeps the raw query', async () => {
    fixture = TestBed.createComponent(BookBrowsePageComponent);
    await TestBed.inject(Router).navigate([], {
      queryParams: {sort: '-title,pageCount', query: 'dune!', facet: ['genre:Fantasy', 'language:en']},
    });
    fixture.detectChanges();
    flushFacetRegistry();

    const request = http.expectOne(candidate => candidate.url === PAGE_URL);
    expect(request.request.params.get('sort')).toBe('-title,pageCount');
    expect(request.request.params.get('query')).toBe('dune');
    expect(request.request.params.getAll('facet')).toEqual(['genre:Fantasy', 'language:en']);
    expect(request.request.params.has('page')).toBe(false);
    request.flush(bookPage([1], 1));
    await flushQueryAsync();

    expect(TestBed.inject(Router).routerState.snapshot.root.queryParamMap.get('query')).toBe('dune!');
  });

  it('applies the saved default sort when the URL has none, mapping old author keys, then drops sorts the server does not offer', async () => {
    fixture = TestBed.createComponent(BookBrowsePageComponent);
    currentUser.set(userFixture({}, undefined, {
      sortKey: 'author',
      sortCriteria: [{field: 'author', direction: 'ASC'}, {field: 'title', direction: 'DESC'}],
    }));
    fixture.detectChanges();
    flushFacetRegistry();

    http.expectOne(request =>
      request.url === PAGE_URL &&
      request.params.get('sort') === 'authorName,-title' &&
      !request.params.has('cursor'),
    ).flush(bookPage([1], 1));
    await flushQueryAsync();
    http.expectOne(request =>
      request.url === PAGE_URL && request.params.get('sort') === '-title',
    ).flush(bookPage([1], 1));
    await flushQueryAsync();
  });

  it('ignores reselecting the active sort and flips only the primary term on a direction toggle', async () => {
    fixture = TestBed.createComponent(BookBrowsePageComponent);
    await TestBed.inject(Router).navigate([], {queryParams: {sort: 'pageCount'}});
    fixture.detectChanges();
    flushFacetRegistry();
    http.expectOne(request =>
      request.url === PAGE_URL && request.params.get('sort') === 'pageCount',
    ).flush(bookPage([1], 1));
    await flushQueryAsync();

    page().preferences.setSortTerms([{key: 'pageCount', direction: 'asc'}]);
    await fixture.whenStable();
    await flushQueryAsync(1);
    http.expectNone(candidate => candidate.url === PAGE_URL);

    await TestBed.inject(Router).navigate([], {queryParams: {sort: '-title,pageCount'}});
    await fixture.whenStable();
    http.expectOne(request =>
      request.url === PAGE_URL && request.params.get('sort') === '-title,pageCount',
    ).flush(bookPage([1], 1));
    await flushQueryAsync();

    page().onSortDirectionChange({key: 'title', direction: 'asc'});
    await fixture.whenStable();
    await flushQueryAsync(1);
    http.expectOne(request =>
      request.url === PAGE_URL && request.params.get('sort') === 'title,pageCount',
    ).flush(bookPage([1], 1));
    await flushQueryAsync();
  });

  it('debounces typed text into one replaceUrl navigation and clears immediately', () => {
    fixture = TestBed.createComponent(BookBrowsePageComponent);
    vi.useFakeTimers();
    try {
      fixture.detectChanges();
      expectInitialPageRequest().flush(bookPage([1], 1));
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

      page().searchDraft.set('the war');
      page().searchDraft.set('the warden ');
      TestBed.flushEffects();
      vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS - 1);
      expect(navigate).not.toHaveBeenCalled();

      vi.advanceTimersByTime(1);
      TestBed.flushEffects();
      expect(navigate).toHaveBeenCalledExactlyOnceWith([], expect.objectContaining({
        queryParams: {query: 'the warden'},
        replaceUrl: true,
      }));

      navigate.mockClear();
      page().searchDraft.set('the wardens');
      TestBed.flushEffects();
      page().onClearQuery();
      expect(navigate).toHaveBeenCalledExactlyOnceWith([], expect.objectContaining({
        queryParams: {query: null},
        queryParamsHandling: 'merge',
      }));

      vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS);
      TestBed.flushEffects();
      expect(navigate).toHaveBeenCalledTimes(1);
    } finally {
      vi.useRealTimers();
    }
  });
});
