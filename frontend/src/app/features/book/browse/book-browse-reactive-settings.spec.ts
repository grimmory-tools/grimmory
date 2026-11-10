import {computed, signal, type Signal, type WritableSignal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {QueryClient, queryOptions} from '@tanstack/angular-query-experimental';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {createQueryClientHarness, flushSignalAndQueryEffects} from '../../../core/testing/query-testing';
import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {AppSettings, type MetadataProviderSpecificFields} from '../../../shared/model/app-settings.model';
import {type BookQueryFacetKey, type FacetValueMap} from '../data/book-query-params';
import {METADATA_PROVIDER_LIST} from '../../../shared/metadata/metadata-providers';
import {providerFieldsOf} from '../../../shared/metadata/metadata-provider-fields';
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

function allProviderFieldsEnabled(): MetadataProviderSpecificFields {
  const fields = providerFieldsOf(METADATA_PROVIDER_LIST);
  const record: Partial<MetadataProviderSpecificFields> = {};
  for (const field of fields) {
    record[field.name] = true;
  }
  return record as MetadataProviderSpecificFields;
}

function providerSettings(
  fields: Partial<MetadataProviderSpecificFields> = {},
): WritableSignal<AppSettings | null> {
  return signal({
    metadataProviderSpecificFields: {...allProviderFieldsEnabled(), ...fields},
  } as AppSettings);
}

function mockBookQueryService(
  facetFn: ReturnType<typeof vi.fn>,
): BookQueryService {
  return {
    facetIndex: () =>
      queryOptions({
        queryKey: ['mock-facet-index'],
        queryFn: async () => ({links: [], facets: [], facetKeys: [], sortTokens: []}),
      }),
    facet: facetFn,
  } as unknown as BookQueryService;
}

describe('book browse reactive settings integration', () => {
  let appSettings: WritableSignal<AppSettings | null>;
  let queryClient: QueryClient;
  let facetFn: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    appSettings = providerSettings({});
    facetFn = vi.fn(() =>
      queryOptions({
        queryKey: ['mock-facet'],
        queryFn: async () => ({links: [], complete: true, values: []}),
      }),
    );
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      providers: [
        ...createQueryClientHarness().providers,
        {provide: AppSettingsService, useValue: {appSettings}},
        {provide: BookQueryService, useValue: mockBookQueryService(facetFn)},
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
      computed(() => bookSortOptions(tokens, appSettings()?.metadataProviderSpecificFields ?? {})),
    );

    expect(options().map((o: {id: string}) => o.id)).toEqual(['title', 'personalRating', 'amazonRating']);

    appSettings.set({
      metadataProviderSpecificFields: {...allProviderFieldsEnabled(), amazonRating: false},
    } as AppSettings);
    flushSignalAndQueryEffects();
    expect(options().map((o: {id: string}) => o.id)).toEqual(['title', 'personalRating']);

    appSettings.set({
      metadataProviderSpecificFields: allProviderFieldsEnabled(),
    } as AppSettings);
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
      computed(() => bookColumnOptions(preferences, appSettings()?.metadataProviderSpecificFields ?? {})),
    );

    const fields = options().map((o: {field: string}) => o.field);
    expect(fields).toContain('amazonRating');
    expect(fields).toContain('title');
    expect(fields).toContain('pageCount');

    appSettings.set({
      metadataProviderSpecificFields: {...allProviderFieldsEnabled(), amazonRating: false},
    } as AppSettings);
    flushSignalAndQueryEffects();
    const disabledFields = options().map((o: {field: string}) => o.field);
    expect(disabledFields).not.toContain('amazonRating');
    expect(disabledFields).toContain('title');
    expect(disabledFields).toContain('pageCount');

    appSettings.set({
      metadataProviderSpecificFields: allProviderFieldsEnabled(),
    } as AppSettings);
    flushSignalAndQueryEffects();
    const restoredFields = options().map((o: {field: string}) => o.field);
    expect(restoredFields).toContain('amazonRating');
  });

  it('facet options respect the setting reactively', () => {
    const isFacetEnabled = TestBed.runInInjectionContext(() =>
      computed(() => isFacetKeyEnabled('amazon_rating', appSettings()?.metadataProviderSpecificFields ?? {})),
    );

    expect(isFacetEnabled()).toBe(true);

    appSettings.set({
      metadataProviderSpecificFields: {...allProviderFieldsEnabled(), amazonRating: false},
    } as AppSettings);
    flushSignalAndQueryEffects();
    expect(isFacetEnabled()).toBe(false);

    appSettings.set({
      metadataProviderSpecificFields: allProviderFieldsEnabled(),
    } as AppSettings);
    flushSignalAndQueryEffects();
    expect(isFacetEnabled()).toBe(true);
  });

  it('excludes disabled provider facet from railGroups when not selected', () => {
    appSettings.set({
      metadataProviderSpecificFields: {...allProviderFieldsEnabled(), amazonRating: false},
    } as AppSettings);
    setFacetIndex(['genre', 'personal_rating', 'amazon_rating', 'goodreads_rating']);

    const selection = signal<FacetValueMap>({});
    const queries = createQueries(selection);
    flushSignalAndQueryEffects();

    const groups = queries.railGroups();
    expect(groups.map(g => g.key)).not.toContain('amazon_rating');
    expect(groups.map(g => g.key)).toContain('genre');
    expect(groups.map(g => g.key)).toContain('goodreads_rating');
  });

  it('keeps selected disabled facet queryable and the facet query executes', () => {
    appSettings.set({
      metadataProviderSpecificFields: {...allProviderFieldsEnabled(), amazonRating: false},
    } as AppSettings);
    setFacetIndex(['genre', 'personal_rating', 'amazon_rating', 'goodreads_rating']);

    const selection = signal<FacetValueMap>({
      amazon_rating: {include: ['4 stars'], exclude: []},
    });
    const queries = createQueries(selection);
    flushSignalAndQueryEffects();

    // The selected disabled facet remains represented in the filter state
    const groups = queries.railGroups();
    expect(groups.map(g => g.key)).toContain('amazon_rating');
    const amazonGroup = groups.find(g => g.key === 'amazon_rating');
    expect(amazonGroup).toBeDefined();
    expect(amazonGroup!.picks?.get('4 stars')).toBe('included');

    // The facet query factory is invoked for the selected disabled facet
    expect(facetFn).toHaveBeenCalledWith('amazon_rating', expect.any(Object), '');
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
