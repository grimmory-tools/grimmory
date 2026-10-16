import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from '@openng/optimus-ui/api';
import {ActivatedRoute, convertToParamMap, Router} from '@angular/router';
import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';

import {BookService} from '../../../book/service/book.service';
import {BookCardOverlayPreferenceService} from '../../../book/components/legacy-book-card/book-card-overlay-preference.service';
import {CoverScalePreferenceService} from '../../../../shared/service/cover-scale-preference.service';
import {UserService} from '../../../settings/user-management/user.service';
import type {AuthorDetail} from '../../data/author-response.models';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {AuthorDetailComponent} from './author-detail.component';
import {authorQueryKeys} from '../../data/author-query-keys';
import {createQueryClientHarness, flushQueryAsync} from '../../../../core/testing/query-testing';
import {AuthorCommandService} from '../../data/author-command.service';
import {AuthorQueryService} from '../../data/author-query.service';

describe('AuthorDetailComponent', () => {
  let getAuthorDetails: Mock<(authorId: number) => Promise<AuthorDetail>>;
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
    getAuthorDetails = vi.fn<(authorId: number) => Promise<AuthorDetail>>();
    getAuthorPhotoUrl = vi.fn((authorId: number, version?: number) => `/api/authors/${authorId}/photo?${version}`);
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
          provide: Router,
          useValue: {},
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
            detail: (authorId: number) => ({queryKey: authorQueryKeys.detail(authorId), queryFn: () => getAuthorDetails(authorId), retry: false}),
          },
        },
        {
          provide: AuthorCommandService,
          useValue: {
            quickMatch: () => ({mutationFn: vi.fn()}),
          },
        },
        {
          provide: BookService,
          useValue: {
            books: signal([]),
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
          provide: BookCardOverlayPreferenceService,
          useValue: {},
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
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('loads the author from the route, honors the tab query param, and updates page title on success', async () => {
    setRouteState(42, 'series');
    getAuthorDetails.mockResolvedValue({...baseAuthor, id: 42, name: 'Grace Hopper'});

    const component = createComponent();
    component.ngOnInit();
    await flushQueryAsync();

    expect(getAuthorDetails).toHaveBeenCalledWith(42);
    expect(component.tab).toBe('series');
    expect(component.loading()).toBe(false);
    expect(component.author()).toEqual({...baseAuthor, id: 42, name: 'Grace Hopper'});
    expect(setPageTitle).toHaveBeenCalledWith('Grace Hopper');
  });

  it('keeps the default tab and clears loading when author load fails', async () => {
    setRouteState(9);
    getAuthorDetails.mockRejectedValue(new Error('load failed'));

    const component = createComponent();
    component.ngOnInit();
    await flushQueryAsync();

    expect(component.tab).toBe('books');
    expect(component.loading()).toBe(false);
    expect(component.author()).toBeNull();
    expect(setPageTitle).not.toHaveBeenCalled();
  });

  it('derives photoUrl from the loaded author and gates canEditMetadata from user permissions', async () => {
    getAuthorDetails.mockResolvedValue(baseAuthor);
    vi.spyOn(Date, 'now').mockReturnValue(321);
    const component = createComponent();

    expect(component.photoUrl).toBe('');
    expect(component.canEditMetadata).toBe(false);

    await flushQueryAsync();
    expect(component.photoUrl).toBe('/api/authors/9/photo?321');

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
