import {MetadataCatalogService} from '../../../../../shared/metadata/metadata-catalog.service';
import {METADATA_PROVIDER_LIST, type MetadataProviderId} from '../../../../../shared/metadata/metadata-providers';
import {Component, computed, input, output, provideZonelessChangeDetection, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {experimental_streamedQuery, provideTanStackQuery, queryOptions, QueryClient} from '@tanstack/angular-query-experimental';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../../../core/testing/transloco-testing';
import {AppSettings} from '../../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {CoverComponent} from '../../../../../shared/components/cover/cover.component';
import {Book, BookMetadata} from '../../../../book/model/book.model';
import {MetadataSourceQueryService, type MetadataSearchParams, type MetadataSearchResult} from '../../../sources/metadata-source-query.service';
import {MetadataPickerComponent} from '../metadata-picker/metadata-picker.component';
import {MetadataSearcherComponent} from './metadata-searcher.component';

@Component({selector: 'app-metadata-picker', template: '', standalone: true})
class PickerStub {
  readonly fetchedMetadata = input<BookMetadata>();
  readonly book = input<Book | null>();
  readonly goBack = output<boolean>();
}

@Component({selector: 'app-cover', template: '', standalone: true})
class CoverStub {
  readonly src = input<string | undefined>();
  readonly title = input<string | undefined>();
  readonly authors = input<string[]>();
  readonly alt = input<string>();
}

describe('MetadataSearcherComponent', () => {
  const providers = signal(METADATA_PROVIDER_LIST);
  const providersLoading = signal(false);
  let fixture: ComponentFixture<MetadataSearcherComponent>;
  let component: MetadataSearcherComponent;
  let results: MetadataSearchResult[];
  let streamError: Error | null;
  let queryResult: Promise<MetadataSearchResult[]>;
  let queryClient: QueryClient;
  const appSettings = signal<Pick<AppSettings, 'autoBookSearch' | 'metadataProviderSettings'> | null>(null);
  const prospective = vi.fn((params: MetadataSearchParams) => queryOptions({
    queryKey: ['prospective', params],
    queryFn: experimental_streamedQuery({
      streamFn: async function* () {
        yield* await queryResult;
        if (streamError) throw streamError;
      },
    }),
    retry: false,
  }));

  const settings = (autoBookSearch = false): Pick<AppSettings, 'autoBookSearch' | 'metadataProviderSettings'> => ({
    autoBookSearch,
    metadataProviderSettings: {
      openLibrary: {enabled: true},
      amazon: {enabled: false, cookie: '', domain: 'com'},
      google: {enabled: false, language: '', apiKey: ''},
      goodReads: {enabled: true},
      ranobedb: {enabled: false, preferRomaji: false},
      hardcover: {enabled: false, apiKey: ''},
      comicvine: {enabled: false, apiKey: ''},
      douban: {enabled: false},
      lubimyczytac: {enabled: false},
      audible: {enabled: false, domain: 'com'},
      appleBooks: {enabled: false, country: ''},
    },
  });

  const book = (id = 1) => ({
    id,
    libraryId: 1,
    metadata: {bookId: id, title: 'Dune', authors: ['Frank Herbert'], isbn13: '9780441013593'},
  });

  beforeEach(async () => {
    providers.set(METADATA_PROVIDER_LIST);
    providersLoading.set(false);
    results = [];
    streamError = null;
    queryResult = Promise.resolve(results);
    prospective.mockClear();
    appSettings.set(null);

    await TestBed.configureTestingModule({
      imports: [MetadataSearcherComponent, getTranslocoModule()],
      providers: [
        provideZonelessChangeDetection(),
        provideTanStackQuery(queryClient = new QueryClient()),
        {provide: AppSettingsService, useValue: {appSettings}},
        {provide: MetadataSourceQueryService, useValue: {
          prospective,
          providersLoading: providersLoading.asReadonly(),
          enabledProviders: computed(() => providers().filter(provider => appSettings()?.metadataProviderSettings[provider.settingsKey]?.enabled)),
        }},
        {provide: MetadataCatalogService, useValue: {
          providers: providers.asReadonly(),
          provider: (id: MetadataProviderId) => providers().find(provider => provider.id === id),
        }},
      ],
    })
      .overrideComponent(MetadataSearcherComponent, {
        remove: {imports: [MetadataPickerComponent, CoverComponent]},
        add: {imports: [PickerStub, CoverStub]},
      })
      .compileComponents();

    fixture = TestBed.createComponent(MetadataSearcherComponent);
    component = fixture.componentInstance;
  });

  function setUp(isActiveTab = false, autoBookSearch = false, id = 1): void {
    appSettings.set(settings(autoBookSearch));
    fixture.componentRef.setInput('book', book(id));
    fixture.componentRef.setInput('isActiveTab', isActiveTab);
    fixture.detectChanges();
  }

  function latestQuery() {
    const query = prospective.mock.results.at(-1);
    if (!query || query.type !== 'return') throw new Error('Expected a prospective query');
    return query.value;
  }

  async function submit(): Promise<void> {
    component.onSubmit();
    TestBed.flushEffects();
    await queryClient.fetchQuery(latestQuery()).catch(() => undefined);
    await fixture.whenStable();
    await new Promise(resolve => setTimeout(resolve, 0));
    TestBed.flushEffects();
    fixture.detectChanges();
  }

  it('keeps the selected result provider', async () => {
    results = [{bookId: 1, provider: 'OpenLibrary', title: 'Dune'}];
    queryResult = Promise.resolve(results);
    setUp();

    await submit();
    component.onBookClick(component.results()[0]);

    expect(component.selected()?.provider).toBe('OpenLibrary');
  });

  it('keeps typed terms through a tab switch', () => {
    setUp(true);
    component.form.patchValue({title: 'Dune Messiah', isbn: ''});

    fixture.componentRef.setInput('isActiveTab', false);
    fixture.detectChanges();
    fixture.componentRef.setInput('isActiveTab', true);
    fixture.detectChanges();

    expect(component.form.value).toMatchObject({title: 'Dune Messiah', isbn: ''});
  });

});
