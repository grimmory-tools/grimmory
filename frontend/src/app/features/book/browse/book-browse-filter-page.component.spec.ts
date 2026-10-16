import {HttpTestingController} from '@angular/common/http/testing';
import {Component, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {Router, provideRouter} from '@angular/router';
import {RouterTestingHarness} from '@angular/router/testing';
import {MessageService} from '@openng/optimus-ui/api';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {API_CONFIG} from '../../../core/config/api-config';
import {createQueryClientHarness, flushQueryAsync} from '../../../core/testing/query-testing';
import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {MagicShelfService} from '../../magic-shelf/service/magic-shelf.service';
import {UserService} from '../../settings/user-management/user.service';
import {ShelfDefinitionQueryService} from '../data/shelf-definition-query.service';
import {LibraryService} from '../service/library.service';
import {BookBrowseFilterPageComponent} from './book-browse-filter-page.component';

const PAGE_URL = `${API_CONFIG.BASE_URL}/api/v1/books/page`;
const FACETS_URL = `${API_CONFIG.BASE_URL}/api/v1/books/facets`;

@Component({selector: 'app-test-route', template: ''})
class TestRouteComponent {}

const GENRES = {
  links: [],
  facets: [{
    metadata: {rel: 'facet', key: 'genre'},
    links: [
      {rel: 'facet', href: '', type: '', title: 'Fantasy', value: 'Fantasy', properties: {numberOfItems: 12}},
      {rel: 'facet', href: '', type: '', title: 'Horror', value: 'Horror', properties: {numberOfItems: 4}},
    ],
  }],
};

function clickByText(elements: Iterable<HTMLElement>, text: string): void {
  const element = Array.from(elements).find(candidate => candidate.textContent.includes(text));
  if (!element) throw new Error(`Could not find "${text}"`);
  element.click();
}

describe('BookBrowseFilterPageComponent', () => {
  let http: HttpTestingController;
  let updateUserSetting: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    const harness = createQueryClientHarness();
    harness.queryClient.setDefaultOptions({queries: {retry: false}});
    updateUserSetting = vi.fn();
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      providers: [
        ...harness.providers,
        provideRouter([
          {path: 'library/:libraryId/books', children: [
            {path: '', component: TestRouteComponent},
            {path: 'filter', component: BookBrowseFilterPageComponent},
          ]},
        ]),
        {
          provide: ShelfDefinitionQueryService,
          useValue: {
            definitions: () => ({
              queryKey: ['shelves', 'query', 'definitions'] as const,
              queryFn: () => Promise.resolve([]),
            }),
          },
        },
        {provide: MagicShelfService, useValue: {shelves: () => []}},
        {provide: LibraryService, useValue: {libraries: () => [{id: 3, name: 'Cookbooks'}]}},
        {provide: UserService, useValue: {currentUser: signal({id: 1, userSettings: {}, permissions: {}}), updateUserSetting}},
        {provide: MessageService, useValue: {add: () => undefined}},
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  function flushGenres() {
    http.match(request => request.url === `${FACETS_URL}/genre`).forEach(request => request.flush(GENRES));
  }

  function previewRequest(facets: string[]) {
    return http.expectOne(request =>
      request.url === PAGE_URL &&
      request.params.get('size') === '1' &&
      request.params.get('query') === 'dune' &&
      JSON.stringify(request.params.getAll('facet')) === JSON.stringify(facets));
  }

  it('stages facet changes against a pinned preview and applies them to the browse page on Show', async () => {
    const routerHarness = await RouterTestingHarness.create();
    await routerHarness.navigateByUrl('/library/3/books/filter?facet=genre:Fantasy&query=dune', BookBrowseFilterPageComponent);
    routerHarness.detectChanges();
    http.expectOne(request => request.url === FACETS_URL).flush({
      links: [],
      facets: [{metadata: {rel: 'facet', key: 'genre', hasItems: true}, links: []}],
    });
    previewRequest(['+library:3', 'genre:Fantasy']).flush({
      content: [], page: {number: 0, size: 1, totalElements: 12, totalPages: 12}, links: [],
    });
    await flushQueryAsync();
    flushGenres();
    await flushQueryAsync();
    routerHarness.detectChanges();
    const root = routerHarness.routeNativeElement!;
    expect(root.textContent).toContain('Show 12 Books');

    clickByText(root.querySelectorAll<HTMLButtonElement>('button[aria-pressed]'), 'Horror');
    routerHarness.detectChanges();
    await flushQueryAsync(1);
    flushGenres();
    previewRequest(['+library:3', 'genre:Fantasy', 'genre:Horror']).flush({
      content: [], page: {number: 0, size: 1, totalElements: 14, totalPages: 14}, links: [],
    });
    await flushQueryAsync();
    routerHarness.detectChanges();
    expect(root.textContent).toContain('Show 14 Books');

    root.querySelector<HTMLButtonElement>('button[aria-label="More Actions"]')!.click();
    routerHarness.detectChanges();
    clickByText(document.querySelectorAll<HTMLElement>('.cdk-overlay-container app-menu-item'), 'Filter Logic');
    routerHarness.detectChanges();
    clickByText(document.querySelectorAll<HTMLElement>('.cdk-overlay-container app-menu-radio'), 'Match All');
    routerHarness.detectChanges();
    await flushQueryAsync(1);
    flushGenres();
    previewRequest(['+genre:Fantasy', '+genre:Horror', '+library:3']).flush({
      content: [], page: {number: 0, size: 1, totalElements: 4, totalPages: 4}, links: [],
    });
    await flushQueryAsync();

    const router = TestBed.inject(Router);
    expect(router.url).toBe('/library/3/books/filter?facet=genre:Fantasy&query=dune');
    expect(updateUserSetting).toHaveBeenCalledWith(1, 'filterMatchAll', true, expect.any(Function));

    routerHarness.detectChanges();
    const show = Array.from(routerHarness.routeNativeElement!.querySelectorAll('button'))
      .find(button => button.textContent.includes('Show 4 Books'))!;
    show.click();
    await routerHarness.fixture.whenStable();

    expect(decodeURIComponent(router.url))
      .toBe('/library/3/books?facet=+genre:Fantasy&facet=+genre:Horror&query=dune');
  });
});
