import {ElementRef, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from 'primeng/api';
import {ActivatedRoute, convertToParamMap, Router} from '@angular/router';
import {Subject, of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {BookService} from '../../../book/service/book.service';
import {BookCardOverlayPreferenceService} from '../../../book/components/book-browser/book-card-overlay-preference.service';
import {CoverScalePreferenceService} from '../../../book/components/book-browser/cover-scale-preference.service';
import {UserService} from '../../../settings/user-management/user.service';
import type {AuthorDetails} from '../../model/author.model';
import {AuthorService} from '../../service/author.service';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {AuthorDetailComponent} from './author-detail.component';

describe('AuthorDetailComponent', () => {
  let getAuthorDetails: ReturnType<typeof vi.fn>;
  let getAuthorPhotoUrl: ReturnType<typeof vi.fn>;
  let patchAuthorInCache: ReturnType<typeof vi.fn>;
  let quickMatchAuthor: ReturnType<typeof vi.fn>;
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

  const baseAuthor: AuthorDetails = {
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
    getAuthorDetails = vi.fn();
    getAuthorPhotoUrl = vi.fn((authorId: number) => `/api/authors/${authorId}/photo`);
    patchAuthorInCache = vi.fn();
    quickMatchAuthor = vi.fn();
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

    TestBed.configureTestingModule({
      providers: [
        {
          provide: ActivatedRoute,
          useValue: route,
        },
        {
          provide: Router,
          useValue: {},
        },
        {
          provide: AuthorService,
          useValue: {
            getAuthorDetails,
            getAuthorPhotoUrl,
            patchAuthorInCache,
            quickMatchAuthor,
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
            currentCardSize: vi.fn(() => 'medium'),
            gridColumnMinWidth: vi.fn(() => '12rem'),
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

  it('loads the author from the route, honors the tab query param, and updates page title on success', () => {
    setRouteState(42, 'series');
    getAuthorDetails.mockReturnValue(of({...baseAuthor, id: 42, name: 'Grace Hopper'}));

    const component = createComponent();

    component.ngOnInit();

    expect(getAuthorDetails).toHaveBeenCalledWith(42);
    expect(component.tab).toBe('series');
    expect(component.loading).toBe(false);
    expect(component.author()).toEqual({...baseAuthor, id: 42, name: 'Grace Hopper'});
    expect(setPageTitle).toHaveBeenCalledWith('Grace Hopper');
  });

  it('keeps the default tab and clears loading when author load fails', () => {
    setRouteState(9);
    getAuthorDetails.mockReturnValue(throwError(() => new Error('load failed')));

    const component = createComponent();

    component.ngOnInit();

    expect(component.tab).toBe('books');
    expect(component.loading).toBe(false);
    expect(component.author()).toBeNull();
    expect(setPageTitle).not.toHaveBeenCalled();
  });

  it('derives photoUrl from the loaded author state and gates canEditMetadata from user permissions', () => {
    const nowSpy = vi.spyOn(Date, 'now').mockReturnValue(321);
    const component = createComponent();

    expect(component.photoUrl).toBe('');
    expect(component.canEditMetadata).toBe(false);

    component.onAuthorUpdated(baseAuthor);

    expect(nowSpy).toHaveBeenCalled();
    expect(component.photoUrl).toBe('/api/authors/9/photo&t=321');

    getCurrentUser.mockReturnValue({permissions: {admin: false, canEditMetadata: true}});
    expect(component.canEditMetadata).toBe(true);

    getCurrentUser.mockReturnValue({permissions: {admin: true, canEditMetadata: false}});
    expect(component.canEditMetadata).toBe(true);
  });

  it('detects description overflow from the assigned element ref only while collapsed', () => {
    const component = createComponent();

    component.descriptionContentRef = {
      nativeElement: {
        scrollHeight: 120,
        clientHeight: 60,
      },
    } as unknown as ElementRef<HTMLElement>;

    component.ngAfterViewChecked();

    expect(component.isOverflowing).toBe(true);

    component.isExpanded = true;
    component.descriptionContentRef = {
      nativeElement: {
        scrollHeight: 20,
        clientHeight: 80,
      },
    } as unknown as ElementRef<HTMLElement>;

    component.ngAfterViewChecked();

    expect(component.isOverflowing).toBe(true);
  });

  it('patches the cached author summary and refreshes photo state when the author is updated', () => {
    const nowSpy = vi.spyOn(Date, 'now').mockReturnValue(777);
    const component = createComponent();

    component.hasPhoto = false;
    component.photoTimestamp = 10;

    component.onAuthorUpdated({
      ...baseAuthor,
      asin: 'B00UPDATED',
    });

    expect(component.author()).toEqual({
      ...baseAuthor,
      asin: 'B00UPDATED',
    });
    expect(component.hasPhoto).toBe(true);
    expect(component.photoTimestamp).toBe(777);
    expect(patchAuthorInCache).toHaveBeenCalledWith(9, {
      name: 'Ada Lovelace',
      asin: 'B00UPDATED',
      hasPhoto: true,
    });
    expect(nowSpy).toHaveBeenCalled();
  });

  it('guards quickMatch when there is no author loaded or a match is already in flight', () => {
    const component = createComponent();

    component.quickMatch();
    expect(quickMatchAuthor).not.toHaveBeenCalled();

    component.onAuthorUpdated(baseAuthor);
    vi.clearAllMocks();
    component.quickMatching = true;

    component.quickMatch();

    expect(quickMatchAuthor).not.toHaveBeenCalled();
  });

  it('quick-matches the loaded author, patches state, and reports a translated success toast', () => {
    const quickMatch$ = new Subject<AuthorDetails>();
    const matchedAuthor: AuthorDetails = {
      ...baseAuthor,
      asin: 'B00MATCH',
    };
    quickMatchAuthor.mockReturnValue(quickMatch$);

    const component = createComponent();
    component.onAuthorUpdated(baseAuthor);
    vi.clearAllMocks();

    component.quickMatch();

    expect(quickMatchAuthor).toHaveBeenCalledWith(9);
    expect(component.quickMatching).toBe(true);

    quickMatch$.next(matchedAuthor);
    quickMatch$.complete();

    expect(component.quickMatching).toBe(false);
    expect(component.author()).toEqual(matchedAuthor);
    expect(patchAuthorInCache).toHaveBeenCalledWith(9, {
      name: 'Ada Lovelace',
      asin: 'B00MATCH',
      hasPhoto: true,
    });
    expect(translate).toHaveBeenCalledWith('authorBrowser.toast.quickMatchSuccessSummary');
    expect(translate).toHaveBeenCalledWith('authorBrowser.toast.quickMatchSuccessDetail');
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'authorBrowser.toast.quickMatchSuccessSummary',
      detail: 'authorBrowser.toast.quickMatchSuccessDetail',
    });
  });

  it('clears the in-flight quick-match flag and reports a translated error toast on failure', () => {
    quickMatchAuthor.mockReturnValue(throwError(() => new Error('quick match failed')));

    const component = createComponent();
    component.onAuthorUpdated(baseAuthor);
    vi.clearAllMocks();

    component.quickMatch();

    expect(component.quickMatching).toBe(false);
    expect(translate).toHaveBeenCalledWith('authorBrowser.toast.quickMatchFailedSummary');
    expect(translate).toHaveBeenCalledWith('authorBrowser.toast.quickMatchFailedDetail');
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'authorBrowser.toast.quickMatchFailedSummary',
      detail: 'authorBrowser.toast.quickMatchFailedDetail',
    });
  });
});
