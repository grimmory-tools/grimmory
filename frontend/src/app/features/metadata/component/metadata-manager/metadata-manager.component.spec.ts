import {TestBed} from '@angular/core/testing';
import {HttpTestingController, TestRequest} from '@angular/common/http/testing';
import {BehaviorSubject, map} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TranslocoService} from '@jsverse/transloco';
import {ActivatedRoute, convertToParamMap, Router} from '@angular/router';
import {MessageService} from '@openng/optimus-ui/api';

import {BookMetadataManageService} from '../../../book/service/book-metadata-manage.service';
import {BookQueryService} from '../../../book/data/book-query.service';
import {AuthService} from '../../../../shared/service/auth.service';
import {createAuthServiceStub, createQueryClientHarness} from '../../../../core/testing/query-testing';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {MetadataManagerComponent} from './metadata-manager.component';

describe('MetadataManagerComponent', () => {
  const add = vi.fn();
  const navigate = vi.fn(() => Promise.resolve(true));
  const setPageTitle = vi.fn();
  const translate = vi.fn((key: string) => `translated:${key}`);
  let queryParams$: BehaviorSubject<Record<string, unknown>>;
  let http: HttpTestingController;

  beforeEach(() => {
    add.mockClear();
    navigate.mockClear();
    setPageTitle.mockClear();
    translate.mockClear();
    queryParams$ = new BehaviorSubject<Record<string, unknown>>({});
    const harness = createQueryClientHarness();
    harness.queryClient.setDefaultOptions({queries: {retry: false}});

    TestBed.configureTestingModule({
      providers: [
        ...harness.providers,
        BookQueryService,
        BookMetadataManageService,
        {provide: AuthService, useValue: createAuthServiceStub()},
        {provide: PageTitleService, useValue: {setPageTitle}},
        {provide: TranslocoService, useValue: {translate}},
        {provide: MessageService, useValue: {add}},
        {provide: ActivatedRoute, useValue: {queryParamMap: queryParams$.pipe(map(convertToParamMap))}},
        {provide: Router, useValue: {navigate}},
      ]
    });
    http = TestBed.inject(HttpTestingController);
  });

  function createComponent() {
    return TestBed.runInInjectionContext(() => new MetadataManagerComponent());
  }

  function expectFacetPage(page: number): TestRequest {
    TestBed.tick();
    return http.expectOne(req =>
      req.url.endsWith('/api/v1/books/facets/author')
      && (req.params.get('page') ?? '0') === String(page));
  }

  function facetResponse(values: [string, number][], hasNext: boolean) {
    return {
      links: hasNext ? [{rel: 'next', href: '', type: 'application/json'}] : [],
      facets: [{
        metadata: {rel: 'facet', key: 'author', title: 'Authors'},
        links: values.map(([value, count]) => ({rel: 'facet', href: '', type: '', title: value, value, properties: {numberOfItems: count}})),
      }],
    };
  }

  async function expectCommand(path: string): Promise<TestRequest> {
    let request: TestRequest | undefined;
    await vi.waitFor(() => {
      TestBed.tick();
      request = http.expectOne(req => req.method === 'POST' && req.url.endsWith(path));
    });
    return request!;
  }

  async function settle(component: MetadataManagerComponent) {
    await vi.waitFor(() => {
      TestBed.tick();
      expect(component.rowsQuery.isFetching()).toBe(false);
    });
  }

  it('sends select all as the search with the unticked names left out', async () => {
    const component = createComponent();
    expectFacetPage(0).flush(facetResponse([['Alice', 3], ['Bob', 1]], true));
    await settle(component);

    component.setAllSelected(true);
    component.setRowSelected({value: 'Bob', count: 1}, false);
    component.openDeleteDialog('authors');
    component.confirmDelete();

    const request = await expectCommand('/api/v1/books/metadata/manage/delete');
    expect(request.request.body).toEqual({metadataType: 'authors', selectAll: true, search: '', excludedValues: ['Bob']});
  });

  it('keeps ticks across pages and counts the books affected once', async () => {
    const component = createComponent();
    expectFacetPage(0).flush(facetResponse([['Alice', 3]], true));
    await settle(component);
    component.setRowSelected({value: 'Alice', count: 3}, true);

    component.page.set(1);
    expectFacetPage(1).flush(facetResponse([['Bob', 2]], false));
    await settle(component);
    component.setRowSelected({value: 'Bob', count: 2}, true);

    component.openMergeDialog('authors');
    TestBed.tick();
    const count = http.expectOne(req => req.url.endsWith('/api/v1/books/page'));
    expect(count.request.params.getAll('facet')).toEqual(['author:Alice', 'author:Bob']);
    expect(count.request.params.get('size')).toBe('1');
    count.flush({content: [], page: {number: 0, size: 1, totalElements: 4, totalPages: 4, cursor: ''}, links: []});

    await vi.waitFor(() => expect(component.affectedBooks()).toBe(4));

    component.mergeTarget = 'Alice';
    component.confirmMerge();

    const request = await expectCommand('/api/v1/books/metadata/manage/consolidate');
    expect(request.request.body).toEqual({metadataType: 'authors', targetValues: ['Alice'], valuesToMerge: ['Alice', 'Bob']});
  });

  it('tracks valid and invalid tabs from query params and updates the page title', () => {
    queryParams$.next({tab: 'tags'});

    const component = createComponent();
    component.ngOnInit();

    expect(component.activeTab()).toBe('tags');
    expect(setPageTitle).toHaveBeenCalledWith('translated:metadata.manager.title: translated:metadata.manager.tabs.tag');

    queryParams$.next({tab: 'invalid'});

    expect(component.activeTab()).toBe('authors');
    expect(navigate).toHaveBeenCalledWith([], {
      relativeTo: expect.anything(),
      queryParams: {tab: 'authors'},
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  });

  it('navigates when a tab is selected', () => {
    const component = createComponent();
    component.ngOnInit();
    navigate.mockClear();
    setPageTitle.mockClear();

    component.selectTab('categories');

    expect(component.activeTab()).toBe('categories');
    expect(setPageTitle).toHaveBeenCalledWith('translated:metadata.manager.title: translated:metadata.manager.tabs.genre');
    expect(navigate).toHaveBeenCalledWith([], {
      relativeTo: expect.anything(),
      queryParams: {tab: 'categories'},
      queryParamsHandling: 'merge',
    });
  });
});
