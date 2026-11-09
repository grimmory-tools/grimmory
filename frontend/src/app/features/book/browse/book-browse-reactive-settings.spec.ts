import {computed, signal, type Signal, type WritableSignal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {QueryClient, queryOptions} from '@tanstack/angular-query-experimental';
import {beforeEach, describe, expect, it} from 'vitest';

import {createQueryClientHarness, flushSignalAndQueryEffects} from '../../../core/testing/query-testing';
import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {type MetadataProviderSpecificFields} from '../../../shared/model/app-settings.model';
import {type BookQueryFacetKey, type FacetValueMap} from '../data/book-query-params';
import {bookColumnOptions} from './book-browse-columns';
import {bookSortOptions} from './book-browse-sort';
import {isFacetKeyEnabled} from './book-browse-fields';
import {browseFilterGroups} from '../../../shared/browse/facets';
import {createBookBrowseQueries} from './book-browse-queries';
import {type BookBrowseScope} from './book-browse-scope';
import {BookQueryService} from '../data/book-query.service';
import {LibraryService} from '../service/library.service';
import {MagicShelfService} from '../../magic-shelf/service/magic-shelf.service';
import {ShelfDefinitionQueryService} from '../data/shelf-definition-query.service';
import {UserService} from '../../settings/user-management/user.service';

function providerSettings(
  fields: Partial<MetadataProviderSpecificFields> = {},
): WritableSignal<Partial<MetadataProviderSpecificFields>> {
  return signal(fields);
}

function mockAppSettingsService(
  settings: WritableSignal<Partial<MetadataProviderSpecificFields>>,
): AppSettingsService {
  return {appSettings: settings} as unknown as AppSettingsService;
}

function mockBookQueryService(): BookQueryService {
  return {
    facetIndex: () =>
      queryOptions({
        queryKey: ['mock-facet-index'],
        queryFn: async () => ({links: [], facets: [], facetKeys: [], sortTokens: []}),
      }),
    facet: () =>
      queryOptions({
        queryKey: ['mock-facet'],
        queryFn: async () => ({links: [], complete: true, values: []}),
      }),
  } as unknown as BookQueryService;
}

describe('book browse reactive settings integration', () => {
  let appSettings: WritableSignal<Partial<MetadataProviderSpecificFields>>;
  let queryClient: QueryClient;

  beforeEach(() => {
    appSettings = providerSettings({});
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      providers: [
        ...createQueryClientHarness().providers,
        {provide: AppSettingsService, useValue: mockAppSettingsService(appSettings)},
        {provide: BookQueryService, useValue: mockBookQueryService()},
        {provide: LibraryService, useValue: {libraries: signal([])}},
        {provide: MagicShelfService, useValue: {shelves: signal([])}},
        {provide: ShelfDefinitionQueryService, useValue: {
          definitions: () =>
            queryOptions({
              queryKey: ['mock-definitions'],
              queryFn: async () => [],
            }),
        }},
        {provide: UserService, useValue: {currentUser: signal(null)}},
      ],
    });
    queryClient = TestBed.inject(QueryClient);
  });

  function setFacetIndex(facetKeys: readonly string[]): void {
    queryClient.setQueryData(['mock-facet-index'], {
      links: [],
      facets: [],
      facetKeys,
      sortTokens: [],
    });
  }

  function createQueries(selection: Signal<FacetValueMap>): ReturnType<typeof createBookBrowseQueries> {
    return TestBed.runInInjectionContext(() =>
      createBookBrowseQueries({
        selection,
        query: signal(''),
        scope: signal<BookBrowseScope | null>(null),
      }),
    );
  }

  it('sort options update reactively when the setting changes', () => {
    const tokens = ['amazonRating', '-amazonRating', 'title', '-title', 'personalRating', '-personalRating'];
    const options = TestBed.runInInjectionContext(() =>
      computed(() => bookSortOptions(tokens, appSettings())),
    );

    expect(options().map((o: {id: string}) => o.id)).toEqual(['title', 'personalRating', 'amazonRating']);

    appSettings.set({amazonRating: false});
    flushSignalAndQueryEffects();
    expect(options().map((o: {id: string}) => o.id)).toEqual(['title', 'personalRating']);

    appSettings.set({});
    flushSignalAndQueryEffects();
    expect(options().map((o: {id: string}) => o.id)).toEqual(['title', 'personalRating', 'amazonRating']);
  });

  it('column options update reactively when the setting changes', () => {
    const preferences = [
      {field: 'title', visible: true, order: 0},
      {field: 'amazonRating', visible: true, order: 1},
      {field: 'goodreadsRating', visible: true, order: 2},
      {field: 'pageCount', visible: true, order: 3},
    ];
    const options = TestBed.runInInjectionContext(() =>
      computed(() => bookColumnOptions(preferences, appSettings())),
    );

    const fields = options().map((o: {field: string}) => o.field);
    expect(fields).toContain('amazonRating');
    expect(fields).toContain('title');
    expect(fields).toContain('pageCount');

    appSettings.set({amazonRating: false});
    flushSignalAndQueryEffects();
    const disabledFields = options().map((o: {field: string}) => o.field);
    expect(disabledFields).not.toContain('amazonRating');
    expect(disabledFields).toContain('title');
    expect(disabledFields).toContain('pageCount');

    appSettings.set({});
    flushSignalAndQueryEffects();
    const restoredFields = options().map((o: {field: string}) => o.field);
    expect(restoredFields).toContain('amazonRating');
  });

  it('facet options respect the setting reactively', () => {
    const isFacetEnabled = TestBed.runInInjectionContext(() =>
      computed(() => isFacetKeyEnabled('amazon_rating', appSettings())),
    );

    expect(isFacetEnabled()).toBe(true);

    appSettings.set({amazonRating: false});
    flushSignalAndQueryEffects();
    expect(isFacetEnabled()).toBe(false);

    appSettings.set({});
    flushSignalAndQueryEffects();
    expect(isFacetEnabled()).toBe(true);
  });

  it('keeps disabled provider facet queryable when it has an active selection', () => {
    appSettings.set({amazonRating: false});
    setFacetIndex(['genre', 'personal_rating', 'amazon_rating', 'goodreads_rating']);

    const selection = signal<FacetValueMap>({
      amazon_rating: {include: ['4 stars'], exclude: []},
    });
    const queries = createQueries(selection);
    flushSignalAndQueryEffects();

    const groups = queries.railGroups();
    expect(groups.map(g => g.key)).toContain('amazon_rating');
  });

  it('keeps selected value available when facet data is not cached', () => {
    appSettings.set({amazonRating: false});
    setFacetIndex(['genre', 'personal_rating', 'amazon_rating', 'goodreads_rating']);

    const selection = signal<FacetValueMap>({
      amazon_rating: {include: ['4 stars'], exclude: []},
    });
    const queries = createQueries(selection);
    flushSignalAndQueryEffects();

    const groups = queries.railGroups();
    const amazonGroup = groups.find(g => g.key === 'amazon_rating');
    expect(amazonGroup).toBeDefined();
    expect(amazonGroup!.picks?.get('4 stars')).toBe('included');
  });

  it('excludes disabled provider facet from groups when not selected', () => {
    const available = new Set<BookQueryFacetKey>(['genre', 'personal_rating', 'goodreads_rating']);
    const served: never[] = [];
    const definitions = {
      order: ['genre', 'personal_rating', 'amazon_rating', 'goodreads_rating'] as const,
      isKey: (key: string): key is BookQueryFacetKey =>
        key === 'genre' || key === 'personal_rating' || key === 'amazon_rating' || key === 'goodreads_rating',
      labelKey: () => 'label',
      multiValued: () => false,
    };

    const groups = browseFilterGroups(available, served, definitions, {});
    expect(groups.map(g => g.key)).toEqual(['genre', 'personal_rating', 'goodreads_rating']);
    expect(groups.map(g => g.key)).not.toContain('amazon_rating');
  });

  it('keeps disabled provider facets visible when they have active selections', () => {
    const available = new Set<BookQueryFacetKey>(['genre', 'personal_rating']);
    const served: never[] = [];
    const definitions = {
      order: ['genre', 'personal_rating', 'amazon_rating'] as const,
      isKey: (key: string): key is BookQueryFacetKey =>
        key === 'genre' || key === 'personal_rating' || key === 'amazon_rating',
      labelKey: () => 'label',
      multiValued: () => false,
    };

    const groupsWithoutSelection = browseFilterGroups(available, served, definitions, {});
    expect(groupsWithoutSelection.map(g => g.key)).toEqual(['genre', 'personal_rating']);

    const selectionWithAmazon = {
      amazon_rating: {include: ['4 stars'], exclude: []},
    };
    const groupsWithSelection = browseFilterGroups(available, served, definitions, selectionWithAmazon);
    expect(groupsWithSelection.map(g => g.key)).toEqual(['genre', 'personal_rating', 'amazon_rating']);
    expect(groupsWithSelection.find(g => g.key === 'amazon_rating')).not.toBeUndefined();
  });

  it('removes disabled provider facet from groups once its selection is cleared', () => {
    const available = new Set<BookQueryFacetKey>(['genre']);
    const served: never[] = [];
    const definitions = {
      order: ['genre', 'amazon_rating'] as const,
      isKey: (key: string): key is BookQueryFacetKey => key === 'genre' || key === 'amazon_rating',
      labelKey: () => 'label',
      multiValued: () => false,
    };

    const selectionWithAmazon = {
      amazon_rating: {include: ['4 stars'], exclude: []},
    };
    const groupsWithSelection = browseFilterGroups(available, served, definitions, selectionWithAmazon);
    expect(groupsWithSelection.map(g => g.key)).toContain('amazon_rating');

    const emptySelection: FacetValueMap = {};
    const groupsWithoutSelection = browseFilterGroups(available, served, definitions, emptySelection);
    expect(groupsWithoutSelection.map(g => g.key)).not.toContain('amazon_rating');
  });

  it('keeps enabled provider facets and standard facets unaffected in groups', () => {
    const available = new Set<BookQueryFacetKey>(['genre', 'amazon_rating', 'goodreads_rating']);
    const served: never[] = [];
    const definitions = {
      order: ['genre', 'amazon_rating', 'goodreads_rating'] as const,
      isKey: (key: string): key is BookQueryFacetKey =>
        key === 'genre' || key === 'amazon_rating' || key === 'goodreads_rating',
      labelKey: () => 'label',
      multiValued: () => false,
    };

    const selection = {
      genre: {include: ['Fantasy'], exclude: []},
      amazon_rating: {include: ['4 stars'], exclude: []},
    };
    const groups = browseFilterGroups(available, served, definitions, selection);
    expect(groups.map(g => g.key)).toEqual(['genre', 'amazon_rating', 'goodreads_rating']);
    expect(groups.find(g => g.key === 'genre')).toBeDefined();
    expect(groups.find(g => g.key === 'amazon_rating')).toBeDefined();
  });
});
