import {CUSTOM_ELEMENTS_SCHEMA} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideTanStackQuery, QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {AudiobookService} from '../../../../../readers/audiobook-player/audiobook.service';
import {BookMetadataManageService} from '../../../../../book/service/book-metadata-manage.service';
import {BookNavigationService} from '../../../../../book/service/book-navigation.service';
import {BookMenuComponent} from '../../../../../book/components/book-menu/book-menu.component';
import {BookRowComponent} from '../../../../../book/components/book-row/book-row.component';
import {BookQueryService} from '../../../../../book/data/book-query.service';
import {AppSettingsService} from '../../../../../../shared/service/app-settings.service';
import {UrlHelperService} from '../../../../../../shared/service/url-helper.service';
import {MetadataTabsComponent} from './metadata-tabs.component';
import {Book} from '../../../../../book/model/book.model';
import {getTranslocoModule} from '../../../../../../core/testing/transloco-testing';

describe('MetadataTabsComponent default tab selection', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', class {
      observe = vi.fn();
      unobserve = vi.fn();
      disconnect = vi.fn();
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    TestBed.resetTestingModule();
  });

  function createFixture(seriesName?: string): ComponentFixture<MetadataTabsComponent> {
    TestBed.configureTestingModule({
      imports: [
        MetadataTabsComponent,
        getTranslocoModule({translocoConfig: {reRenderOnLangChange: false}}),
      ],
      providers: [
        {provide: UrlHelperService, useValue: {getCoverUrl: () => null, getAudiobookCoverUrl: () => null}},
        {provide: BookMetadataManageService, useValue: {supportsDualCovers: () => false}},
        {provide: AudiobookService, useValue: {getAudiobookInfo: () => undefined}},
        {provide: BookNavigationService, useValue: {readBook: vi.fn()}},
        {
          provide: BookQueryService,
          useValue: {
            infinitePage: () => ({queryKey: ['page'], queryFn: () => new Promise(() => undefined), initialPageParam: null, getNextPageParam: () => undefined}),
            recommendations: () => ({queryKey: ['recommendations'], queryFn: () => new Promise(() => undefined)}),
          },
        },
        {provide: AppSettingsService, useValue: {appSettings: () => null}},
        provideTanStackQuery(new QueryClient()),
      ],
    });

    TestBed.overrideComponent(MetadataTabsComponent, {
      remove: {imports: [BookMenuComponent, BookRowComponent]},
      add: {schemas: [CUSTOM_ELEMENTS_SCHEMA]},
    });

    const fixture = TestBed.createComponent(MetadataTabsComponent);
    fixture.componentRef.setInput('book', {
      id: 21,
      libraryId: 1,
      libraryName: 'Library',
      metadata: {bookId: 21, title: 'Test Book', authors: [], seriesName},
      alternativeFormats: [],
      supplementaryFiles: [],
    } satisfies Book);
    fixture.detectChanges();

    return fixture;
  }

  it('selects the series tab from book metadata before series contents load', () => {
    const fixture = createFixture('Dune');
    const component = fixture.componentInstance;

    expect(component.activeTab()).toBe('series');
  });
});

// TODO(seam): This tab surface coordinates nested review, notes, reading-session, and
// audiobook child components, so it needs an integration harness around real tab changes.
describe.skip('MetadataTabsComponent', () => {
  it('needs a tab harness for chapter loading and file action emission branches', () => {
    expect.hasAssertions();
  });
});
