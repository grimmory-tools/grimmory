import {NO_ERRORS_SCHEMA} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {BookType, ReadStatus} from '../../../book/model/book.model';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {SeriesCoverBook, SeriesSummary} from '../../model/series.model';
import {SeriesCardComponent} from './series-card.component';

function makeCoverBook(bookId: number, primaryFileType: BookType = 'EPUB'): SeriesCoverBook {
  return {
    bookId,
    primaryFileType,
    seriesNumber: null,
    coverUpdatedOn: primaryFileType === 'AUDIOBOOK' ? '2026-03-02' : '2026-03-01',
  };
}

function makeSeries(overrides: Partial<SeriesSummary> = {}): SeriesSummary {
  return {
    seriesName: 'Dune',
    authors: ['Frank Herbert', 'Brian Herbert', 'Kevin J. Anderson'],
    categories: ['Sci-Fi'],
    bookCount: 4,
    readCount: 2,
    progress: 0.48,
    seriesStatus: ReadStatus.READING,
    lastReadTime: null,
    coverBooks: [makeCoverBook(1), makeCoverBook(2, 'AUDIOBOOK')],
    addedOn: null,
    ...overrides,
  };
}

describe('SeriesCardComponent', () => {
  let fixture: ComponentFixture<SeriesCardComponent>;
  let component: SeriesCardComponent;
  let urlHelper: {
    getThumbnailUrl: ReturnType<typeof vi.fn>;
    getAudiobookThumbnailUrl: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    urlHelper = {
      getThumbnailUrl: vi.fn((bookId: number, updatedOn?: string) => `thumb:${bookId}:${updatedOn ?? 'none'}`),
      getAudiobookThumbnailUrl: vi.fn((bookId: number, updatedOn?: string) => `audio:${bookId}:${updatedOn ?? 'none'}`),
    };

    await TestBed.configureTestingModule({
      imports: [SeriesCardComponent, getTranslocoModule()],
      providers: [
        {provide: UrlHelperService, useValue: urlHelper},
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(SeriesCardComponent);
    component = fixture.componentInstance;
    component.series = makeSeries();
    fixture.detectChanges();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('derives progress and author summaries for compact display', () => {
    expect(component.progressPercent).toBe(48);
    expect(component.authorsDisplay).toBe('Frank Herbert, Brian Herbert +1');
  });

  it('builds thumbnail URLs from ebook and audiobook covers', () => {
    expect(component.getCoverUrl(0)).toBe('thumb:1:2026-03-01');
    expect(component.getCoverUrl(1)).toBe('audio:2:2026-03-02');
    expect(component.getCoverUrl(99)).toBeNull();
  });

  it('emits a card click and stops propagation', () => {
    const emitSpy = vi.spyOn(component.cardClick, 'emit');
    const event = {stopPropagation: vi.fn()} as unknown as Event;

    component.onCardClick(event);

    expect(event.stopPropagation).toHaveBeenCalled();
    expect(emitSpy).toHaveBeenCalledWith(component.series);
  });

  it('renders the series name and progress label', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Dune');
    expect(text).toContain('2/4');
  });
});
