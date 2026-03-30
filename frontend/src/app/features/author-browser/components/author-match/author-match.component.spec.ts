import {TestBed} from '@angular/core/testing';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from 'primeng/api';
import {Subject, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import type {AuthorDetails, AuthorSearchResult} from '../../model/author.model';
import {AuthorService} from '../../service/author.service';
import {AuthorMatchComponent} from './author-match.component';

describe('AuthorMatchComponent', () => {
  let searchAuthorMetadata: ReturnType<typeof vi.fn>;
  let matchAuthor: ReturnType<typeof vi.fn>;
  let messageService: Pick<MessageService, 'add'>;
  let translate: ReturnType<typeof vi.fn>;

  const createComponent = (overrides?: {authorId?: number; authorName?: string}) => {
    const component = TestBed.runInInjectionContext(() => new AuthorMatchComponent());
    component.authorId = overrides?.authorId ?? 9;
    component.authorName = overrides?.authorName ?? 'Ada Lovelace';
    return component;
  };

  beforeEach(() => {
    searchAuthorMetadata = vi.fn();
    matchAuthor = vi.fn();
    messageService = {
      add: vi.fn(),
    };
    translate = vi.fn((key: string) => key);

    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthorService,
          useValue: {
            searchAuthorMetadata,
            matchAuthor,
          },
        },
        {
          provide: MessageService,
          useValue: messageService,
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

  it('bootstraps searchQuery from authorName and gates canSearch on trimmed query inputs', () => {
    const component = createComponent({authorName: '  Ada Lovelace  '});

    component.ngOnInit();

    expect(component.searchQuery).toBe('  Ada Lovelace  ');
    expect(component.canSearch).toBe(true);

    component.searchQuery = '   ';
    component.asinQuery = '   ';
    expect(component.canSearch).toBe(false);

    component.asinQuery = ' B00TEST ';
    expect(component.canSearch).toBe(true);
  });

  it('does not search when both query inputs are blank after trimming', () => {
    const component = createComponent();
    component.searchQuery = '   ';
    component.asinQuery = '   ';
    component.results = [{source: 'seed', asin: 'old', name: 'Existing'}];

    component.search();

    expect(searchAuthorMetadata).not.toHaveBeenCalled();
    expect(component.searching).toBe(false);
    expect(component.hasSearched).toBe(false);
    expect(component.results).toEqual([{source: 'seed', asin: 'old', name: 'Existing'}]);
  });

  it('searches with trimmed values, clears stale results, and stores returned matches', () => {
    const results$ = new Subject<AuthorSearchResult[]>();
    searchAuthorMetadata.mockReturnValue(results$);

    const component = createComponent();
    component.searchQuery = ' Ada ';
    component.asinQuery = ' B00MATCH ';
    component.selectedRegion = 'uk';
    component.results = [{source: 'seed', asin: 'old', name: 'Existing'}];

    component.search();

    expect(searchAuthorMetadata).toHaveBeenCalledWith(9, 'Ada', 'uk', 'B00MATCH');
    expect(component.searching).toBe(true);
    expect(component.hasSearched).toBe(true);
    expect(component.results).toEqual([]);

    results$.next([
      {source: 'amazon', asin: 'B00MATCH', name: 'Ada Lovelace'},
      {source: 'amazon', asin: 'B00OTHER', name: 'A. Lovelace'},
    ]);
    results$.complete();

    expect(component.results).toEqual([
      {source: 'amazon', asin: 'B00MATCH', name: 'Ada Lovelace'},
      {source: 'amazon', asin: 'B00OTHER', name: 'A. Lovelace'},
    ]);
    expect(component.searching).toBe(false);
  });

  it('reports a translated search failure toast when metadata search errors', () => {
    searchAuthorMetadata.mockReturnValue(throwError(() => new Error('boom')));

    const component = createComponent();
    component.searchQuery = 'Ada';

    component.search();

    expect(component.searching).toBe(false);
    expect(translate).toHaveBeenCalledWith('authorBrowser.match.toast.searchFailedSummary');
    expect(translate).toHaveBeenCalledWith('authorBrowser.match.toast.searchFailedDetail');
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'authorBrowser.match.toast.searchFailedSummary',
      detail: 'authorBrowser.match.toast.searchFailedDetail',
      life: 3000,
    });
  });

  it('shapes the match request, emits the updated author, and reports success on matchAuthor', () => {
    const updatedAuthor: AuthorDetails = {
      id: 9,
      name: 'Ada Lovelace',
      asin: 'B00MATCH',
      nameLocked: false,
      descriptionLocked: false,
      asinLocked: true,
      photoLocked: false,
    };
    const match$ = new Subject<AuthorDetails>();
    matchAuthor.mockReturnValue(match$);

    const component = createComponent();
    component.selectedRegion = 'de';
    const emitSpy = vi.spyOn(component.authorMatched, 'emit');

    component.matchAuthor({
      source: 'amazon',
      asin: 'B00MATCH',
      name: 'Ada Lovelace',
      description: 'Pioneer',
    });

    expect(matchAuthor).toHaveBeenCalledWith(9, {
      source: 'amazon',
      asin: 'B00MATCH',
      region: 'de',
    });
    expect(component.matching).toBe(true);

    match$.next(updatedAuthor);
    match$.complete();

    expect(component.matching).toBe(false);
    expect(translate).toHaveBeenCalledWith('authorBrowser.match.toast.matchSuccessSummary');
    expect(translate).toHaveBeenCalledWith('authorBrowser.match.toast.matchSuccessDetail');
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'authorBrowser.match.toast.matchSuccessSummary',
      detail: 'authorBrowser.match.toast.matchSuccessDetail',
      life: 3000,
    });
    expect(emitSpy).toHaveBeenCalledWith(updatedAuthor);
  });

  it('reports a translated failure toast when matchAuthor errors', () => {
    matchAuthor.mockReturnValue(throwError(() => new Error('nope')));

    const component = createComponent();

    component.matchAuthor({
      source: 'amazon',
      asin: 'B00FAIL',
      name: 'Ada Lovelace',
    });

    expect(component.matching).toBe(false);
    expect(translate).toHaveBeenCalledWith('authorBrowser.match.toast.matchFailedSummary');
    expect(translate).toHaveBeenCalledWith('authorBrowser.match.toast.matchFailedDetail');
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'authorBrowser.match.toast.matchFailedSummary',
      detail: 'authorBrowser.match.toast.matchFailedDetail',
      life: 3000,
    });
  });
});
