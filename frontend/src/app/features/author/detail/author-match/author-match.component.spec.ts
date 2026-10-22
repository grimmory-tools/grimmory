import {TestBed} from '@angular/core/testing';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from '@openng/optimus-ui/api';
import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';

import {createQueryClientHarness, flushQueryAsync} from '../../../../core/testing/query-testing';
import {AuthorCommandService, type MatchAuthorVariables} from '../../data/author-command.service';
import {type AuthorMetadataSearchParams} from '../../data/author-query-params';
import {AuthorQueryService} from '../../data/author-query.service';
import {type AuthorDetail, type AuthorSearchResult} from '../../data/author-response.models';
import {AuthorMatchComponent} from './author-match.component';

describe('AuthorMatchComponent', () => {
  let searchMetadata: Mock<(params: AuthorMetadataSearchParams) => Promise<AuthorSearchResult[]>>;
  let matchAuthor: Mock<(variables: MatchAuthorVariables) => Promise<AuthorDetail>>;
  let messageService: Pick<MessageService, 'add'>;

  const createComponent = () => {
    const component = TestBed.runInInjectionContext(() => new AuthorMatchComponent());
    component.authorId = 9;
    component.authorName = 'Ada Lovelace';
    return component;
  };

  beforeEach(() => {
    searchMetadata = vi.fn<(params: AuthorMetadataSearchParams) => Promise<AuthorSearchResult[]>>();
    matchAuthor = vi.fn<(variables: MatchAuthorVariables) => Promise<AuthorDetail>>();
    messageService = {add: vi.fn()};

    TestBed.configureTestingModule({
      providers: [
        ...createQueryClientHarness().providers,
        {
          provide: AuthorQueryService,
          useValue: {
            metadataSearch: (authorId: number, params: AuthorMetadataSearchParams) => ({
              queryKey: ['metadata-search', authorId, params],
              queryFn: () => searchMetadata(params),
              retry: false,
            }),
          },
        },
        {provide: AuthorCommandService, useValue: {match: () => ({mutationFn: matchAuthor})}},
        {provide: MessageService, useValue: messageService},
        {provide: TranslocoService, useValue: {translate: (key: string) => key}},
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('gates canSearch on the trimmed query inputs', () => {
    const component = createComponent();
    component.ngOnInit();
    expect(component.canSearch()).toBe(true);

    component.searchQuery.set('   ');
    component.asinQuery.set('   ');
    expect(component.canSearch()).toBe(false);

    component.asinQuery.set(' B00TEST ');
    expect(component.canSearch()).toBe(true);
  });

  it('searches with trimmed values and shows the results', async () => {
    const results: AuthorSearchResult[] = [{source: 'AUDNEXUS', asin: 'B1', name: 'Ada Lovelace'}];
    searchMetadata.mockResolvedValue(results);
    const component = createComponent();
    component.searchQuery.set('  Ada  ');
    component.asinQuery.set(' B1 ');

    component.search();
    await flushQueryAsync();

    expect(searchMetadata).toHaveBeenCalledWith({query: 'Ada', region: 'us', asin: 'B1'});
    expect(component.hasSearched()).toBe(true);
    expect(component.results()).toEqual(results);
  });

  it('applies the chosen match, reports success and tells the page', async () => {
    matchAuthor.mockResolvedValue({id: 9, name: 'Ada Lovelace', nameLocked: false, descriptionLocked: false, asinLocked: false, photoLocked: false});
    const component = createComponent();
    const matched = vi.fn();
    component.authorMatched.subscribe(matched);
    component.selectedRegion.set('uk');

    component.matchAuthor({source: 'AUDNEXUS', asin: 'B1', name: 'Ada Lovelace'});
    await flushQueryAsync();

    expect(matchAuthor.mock.calls[0][0]).toEqual({authorId: 9, request: {source: 'AUDNEXUS', asin: 'B1', region: 'uk'}});
    expect(matched).toHaveBeenCalled();
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
  });
});
