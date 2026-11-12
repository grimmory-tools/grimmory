import {TestBed} from '@angular/core/testing';
import {HttpTestingController} from '@angular/common/http/testing';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from '@openng/optimus-ui/api';
import {ActivatedRoute, convertToParamMap} from '@angular/router';
import {DialogService} from '@openng/optimus-ui/dynamicdialog';
import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';

import {CoverScalePreferenceService} from '../../../../shared/service/cover-scale-preference.service';
import {UserService} from '../../../settings/user-management/user.service';
import type {AuthorDetail} from '../../data/author-query.models';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {AuthorDetailComponent} from './author-detail.component';
import {authorQueryKeys} from '../../data/author-query-keys';
import {createQueryClientHarness, flushQueryAsync} from '../../../../core/testing/query-testing';
import {API_CONFIG} from '../../../../core/config/api-config';
import {AuthorCommandService} from '../../data/author-command.service';
import {AuthorQueryService} from '../../data/author-query.service';

describe('AuthorDetailComponent', () => {
  let loadAuthor: Mock<(authorId: number) => Promise<AuthorDetail>>;
  let getAuthorPhotoUrl: ReturnType<typeof vi.fn>;
  let getCurrentUser: ReturnType<typeof vi.fn>;
  let setPageTitle: ReturnType<typeof vi.fn>;
  let translate: ReturnType<typeof vi.fn>;
  let messageService: Pick<MessageService, 'add'>;
  let route: {
    snapshot: {
      paramMap: ReturnType<typeof convertToParamMap>;
      queryParamMap: ReturnType<typeof convertToParamMap>;
    };
  };

  const baseAuthor: AuthorDetail = {
    id: 9,
    name: 'Ada Lovelace',
    description: 'Analytical engine pioneer',
    asin: 'B00ADA',
    nameLocked: false,
    descriptionLocked: false,
    asinLocked: false,
    photoLocked: false,
  };

  const setRouteState = (authorId = 9, tab?: string) => {
    route.snapshot.paramMap = convertToParamMap({authorId: String(authorId)});
    route.snapshot.queryParamMap = convertToParamMap(tab ? {tab} : {});
  };

  const createComponent = () => TestBed.runInInjectionContext(() => new AuthorDetailComponent());

  beforeEach(() => {
    loadAuthor = vi.fn<(authorId: number) => Promise<AuthorDetail>>();
    getAuthorPhotoUrl = vi.fn((authorId: number, version: number) => `/api/authors/${authorId}/photo?t=${version}`);
    getCurrentUser = vi.fn(() => null);
    setPageTitle = vi.fn();
    translate = vi.fn((key: string) => key);
    messageService = {
      add: vi.fn(),
    };
    route = {
      snapshot: {
        paramMap: convertToParamMap({authorId: '9'}),
        queryParamMap: convertToParamMap({}),
      },
    };

    const harness = createQueryClientHarness();

    TestBed.configureTestingModule({
      providers: [
        ...harness.providers,
        {
          provide: ActivatedRoute,
          useValue: route,
        },
        {
          provide: UrlHelperService,
          useValue: {
            getAuthorPhotoUrl,
          },
        },
        {
          provide: AuthorQueryService,
          useValue: {
            detail: (authorId: number) => ({queryKey: authorQueryKeys.detail(authorId), queryFn: () => loadAuthor(authorId), retry: false}),
          },
        },
        {
          provide: AuthorCommandService,
          useValue: {
            quickMatch: () => ({mutationFn: vi.fn()}),
          },
        },
        {
          provide: MessageService,
          useValue: messageService,
        },
        {
          provide: CoverScalePreferenceService,
          useValue: {
            currentCardSize: vi.fn(() => ({width: 135, height: 220})),
          },
        },
        {
          provide: UserService,
          useValue: {
            getCurrentUser,
          },
        },
        {
          provide: PageTitleService,
          useValue: {
            setPageTitle,
          },
        },
        {
          provide: TranslocoService,
          useValue: {
            translate,
          },
        },
        {
          provide: DialogService,
          useValue: {
            open: vi.fn(),
          },
        },
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('loads the author from the route, honors the tab query param, and updates page title on success', async () => {
    setRouteState(42, 'series');
    loadAuthor.mockResolvedValue({...baseAuthor, id: 42, name: 'Grace Hopper'});

    const component = createComponent();
    component.ngOnInit();
    await flushQueryAsync();

    expect(loadAuthor).toHaveBeenCalledWith(42);
    expect(component.tab).toBe('series');
    expect(setPageTitle).toHaveBeenCalledWith('Grace Hopper');
  });

  it('requests the author\'s books only after the author has loaded', async () => {
    const pageUrl = `${API_CONFIG.BASE_URL}/api/v1/books/page`;
    let resolveAuthor: (author: AuthorDetail) => void = () => undefined;
    setRouteState(42);
    loadAuthor.mockReturnValue(new Promise<AuthorDetail>(resolve => {
      resolveAuthor = resolve;
    }));
    const http = TestBed.inject(HttpTestingController);

    createComponent().ngOnInit();
    await flushQueryAsync();

    http.expectNone(request => request.url === pageUrl);

    resolveAuthor({...baseAuthor, id: 42, name: 'Grace Hopper'});
    await flushQueryAsync();

    const request = http.expectOne(request => request.url === pageUrl);
    expect(request.request.params.getAll('facet')).toEqual(['author:Grace Hopper']);
  });

  it('leaves the page title alone and builds no photo address when the author fails to load', async () => {
    setRouteState(9);
    loadAuthor.mockRejectedValue(new Error('load failed'));

    const component = createComponent();
    component.ngOnInit();
    await flushQueryAsync();

    expect(component.photoUrl).toBe('');
    expect(setPageTitle).not.toHaveBeenCalled();
  });

  it('derives photoUrl from the loaded author and gates canEditMetadata from user permissions', async () => {
    loadAuthor.mockResolvedValue(baseAuthor);
    vi.spyOn(Date, 'now').mockReturnValue(321);
    const component = createComponent();

    expect(component.photoUrl).toBe('');
    expect(component.canEditMetadata).toBe(false);

    await flushQueryAsync();
    expect(component.photoUrl).toBe('/api/authors/9/photo?t=321');

    getCurrentUser.mockReturnValue({permissions: {admin: false, canEditMetadata: true}});
    expect(component.canEditMetadata).toBe(true);

    getCurrentUser.mockReturnValue({permissions: {admin: true, canEditMetadata: false}});
    expect(component.canEditMetadata).toBe(true);
  });

  it('detects description overflow from element dimensions only while collapsed', () => {
    const component = createComponent();

    component.updateDescriptionOverflow({
      scrollHeight: 120,
      clientHeight: 60,
    });

    expect(component.isOverflowing).toBe(true);

    component.isExpanded = true;
    component.updateDescriptionOverflow({
      scrollHeight: 20,
      clientHeight: 80,
    });

    expect(component.isOverflowing).toBe(true);
  });
});
