import {HttpTestingController} from '@angular/common/http/testing';
import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {provideRouter} from '@angular/router';
import {ConfirmationService, MessageService} from '@openng/optimus-ui/api';
import {DialogService} from '@openng/optimus-ui/dynamicdialog';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {API_CONFIG} from '../../../core/config/api-config';
import {createQueryClientHarness, flushQueryAsync} from '../../../core/testing/query-testing';
import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {BrowseGridComponent} from '../../../shared/browse/grid/grid.component';
import {LibraryService} from '../../book/service/library.service';
import {UserService} from '../../settings/user-management/user.service';
import {type AuthorSummary} from '../data/author-response.models';
import {AuthorBrowsePageComponent} from './author-browse-page.component';

const PAGE_URL = `${API_CONFIG.BASE_URL}/api/v1/authors/page`;
const FACETS_URL = `${API_CONFIG.BASE_URL}/api/v1/authors/facets`;

function authorPage(ids: number[]) {
  return {
    content: ids.map(id => ({id, name: `Author ${id}`, bookCount: id})),
    page: {number: 0, size: 60, totalElements: ids.length, totalPages: 1},
    links: [],
  };
}

describe('AuthorBrowsePageComponent', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', class {
      observe = vi.fn();
      unobserve = vi.fn();
      disconnect = vi.fn();
    });
    const harness = createQueryClientHarness();
    harness.queryClient.setDefaultOptions({queries: {retry: false}});
    TestBed.configureTestingModule({
      imports: [AuthorBrowsePageComponent, getTranslocoModule()],
      providers: [
        ...harness.providers,
        provideRouter([]),
        {provide: LibraryService, useValue: {libraries: () => []}},
        {provide: UserService, useValue: {currentUser: signal(null), updateUserSetting: vi.fn()}},
        {provide: MessageService, useValue: {add: () => undefined}},
        ConfirmationService,
        {provide: DialogService, useValue: {open: () => null}},
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.unstubAllGlobals();
    TestBed.resetTestingModule();
    history.replaceState({}, '', '/');
    localStorage.clear();
  });

  it('sizes author cards from the author scale, not the book cover scale', () => {
    localStorage.setItem('coverScalePreference', '1.5');
    localStorage.setItem('authorScalePreference', '1.2');
    const fixture = TestBed.createComponent(AuthorBrowsePageComponent);
    fixture.detectChanges();
    flushFacetRegistry();
    http.expectOne(request => request.url === PAGE_URL).flush(authorPage([]));

    const grid = fixture.debugElement.query(By.directive(BrowseGridComponent))
      .componentInstance as BrowseGridComponent<AuthorSummary>;
    expect(grid.minItemWidth()).toBe(198);
  });

  function flushFacetRegistry(): void {
    http.expectOne(request => request.url === FACETS_URL).flush({
      links: [],
      facets: [{
        metadata: {rel: 'sort', key: 'sort', title: 'Sort'},
        links: [
          {rel: 'sort', href: '', type: '', title: 'name', value: 'name'},
          {rel: 'sort', href: '', type: '', title: 'name descending', value: '-name'},
        ],
      }],
    });
  }

  it('sorts by name by default and passes each result to the grid', async () => {
    const fixture = TestBed.createComponent(AuthorBrowsePageComponent);
    fixture.detectChanges();
    flushFacetRegistry();

    http.expectOne(request =>
      request.url === PAGE_URL && request.params.get('sort') === 'name' && request.params.get('size') === '60',
    ).flush(authorPage([1, 2, 3]));
    await flushQueryAsync();

    const grid = fixture.debugElement.query(By.directive(BrowseGridComponent))
      .componentInstance as BrowseGridComponent<AuthorSummary>;
    expect(grid.items().map(author => author.name)).toEqual(['Author 1', 'Author 2', 'Author 3']);
  });
});
