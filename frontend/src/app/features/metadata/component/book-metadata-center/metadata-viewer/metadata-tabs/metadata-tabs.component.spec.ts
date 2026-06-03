import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {AudiobookService} from '../../../../../readers/audiobook-player/audiobook.service';
import {BookMetadataManageService} from '../../../../../book/service/book-metadata-manage.service';
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

  function createFixture(hasSeries = false): ComponentFixture<MetadataTabsComponent> {
    TestBed.configureTestingModule({
      imports: [
        MetadataTabsComponent,
        getTranslocoModule({translocoConfig: {reRenderOnLangChange: false}}),
      ],
      providers: [
        {provide: UrlHelperService, useValue: {getCoverUrl: () => null, getAudiobookCoverUrl: () => null}},
        {provide: BookMetadataManageService, useValue: {supportsDualCovers: () => false}},
        {provide: AudiobookService, useValue: {getAudiobookInfo: () => undefined}},
      ],
    });

    const fixture = TestBed.createComponent(MetadataTabsComponent);
    fixture.componentRef.setInput('book', {
      id: 21,
      libraryId: 1,
      libraryName: 'Library',
      metadata: {bookId: 21, title: 'Test Book', authors: []},
      alternativeFormats: [],
      supplementaryFiles: [],
    } satisfies Book);
    fixture.componentRef.setInput('hasSeries', hasSeries);
    fixture.componentRef.setInput('bookInSeries', []);
    fixture.detectChanges();

    return fixture;
  }

  it('selects the series tab from book metadata before series contents load', () => {
    const fixture = createFixture(true);
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
