import {SimpleChange} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {of, throwError} from 'rxjs';
import {TranslocoService} from '@jsverse/transloco';

import {ReadingSessionApiService, ReadingSessionResponse} from '../../../../../shared/service/reading-session-api.service';
import {BookReadingSessionsComponent} from './book-reading-sessions.component';

describe('BookReadingSessionsComponent', () => {
  const getSessionsByBookId = vi.fn();
  const translate = vi.fn((key: string) => `translated:${key}`);

  function createSession(overrides: Partial<ReadingSessionResponse> = {}): ReadingSessionResponse {
    return {
      id: 1,
      bookId: 7,
      bookTitle: 'Example',
      bookType: 'EPUB',
      startTime: '2026-03-26T10:00:00Z',
      endTime: '2026-03-26T10:05:30Z',
      durationSeconds: 330,
      startProgress: 10,
      endProgress: 18,
      progressDelta: 8,
      startLocation: '15',
      endLocation: '20',
      createdAt: '2026-03-26T10:05:30Z',
      ...overrides,
    };
  }

  beforeEach(() => {
    getSessionsByBookId.mockReset();
    translate.mockClear();

    TestBed.configureTestingModule({
      providers: [
        {provide: ReadingSessionApiService, useValue: {getSessionsByBookId}},
        {provide: TranslocoService, useValue: {translate}},
      ]
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('loads reading sessions on init and exposes the translated page report template', () => {
    const session = createSession();
    getSessionsByBookId.mockReturnValue(of({
      content: [session],
      page: {totalElements: 1, totalPages: 1, number: 0, size: 100},
    }));

    const component = TestBed.runInInjectionContext(() => new BookReadingSessionsComponent());
    component.bookId = 7;
    component.ngOnInit();

    expect(getSessionsByBookId).toHaveBeenCalledWith(7, 0, 100);
    expect(component.sessions).toEqual([session]);
    expect(component.loading).toBe(false);
    expect(component.pageReportTemplate).toBe('translated:metadata.readingSessions.pageReport');
  });

  it('reloads when the book id changes after the first change and clears loading on errors', () => {
    getSessionsByBookId
      .mockReturnValueOnce(throwError(() => new Error('failed')))
      .mockReturnValueOnce(of({
        content: [createSession({id: 2, bookId: 9})],
        page: {totalElements: 1, totalPages: 1, number: 0, size: 100},
      }));

    const component = TestBed.runInInjectionContext(() => new BookReadingSessionsComponent());
    component.bookId = 7;

    component.loadSessions();
    expect(component.loading).toBe(false);
    expect(component.sessions).toEqual([]);

    component.bookId = 9;
    component.ngOnChanges({
      bookId: new SimpleChange(7, 9, false),
    });

    expect(getSessionsByBookId).toHaveBeenNthCalledWith(2, 9, 0, 100);
    expect(component.sessions).toEqual([createSession({id: 2, bookId: 9})]);
    expect(component.loading).toBe(false);
  });

  it('ignores the initial ngOnChanges first-change event', () => {
    const component = TestBed.runInInjectionContext(() => new BookReadingSessionsComponent());

    component.ngOnChanges({
      bookId: new SimpleChange(undefined, 7, true),
    });

    expect(getSessionsByBookId).not.toHaveBeenCalled();
  });

  it('formats durations, actual durations, and date helpers consistently', () => {
    const component = TestBed.runInInjectionContext(() => new BookReadingSessionsComponent());
    const session = createSession();

    expect(component.formatDuration(3725)).toBe('1h 2m');
    expect(component.formatDuration(125)).toBe('2m 5s');
    expect(component.formatDuration(45)).toBe('45s');
    expect(component.calculateActualDuration(session)).toBe(330);
    expect(component.getActualDuration(session)).toBe('5m 30s');
    expect(component.getActualDuration(createSession({durationSeconds: 300}))).toBe('5m 30s');
    expect(component.formatDate(session.startTime)).toBe(new Date(session.startTime).toLocaleString());
    expect(component.formatSessionDate(session.startTime)).toBe(new Date(session.startTime).toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    }));
    expect(component.formatTime(session.startTime)).toBe(new Date(session.startTime).toLocaleTimeString(undefined, {
      hour: 'numeric',
      minute: '2-digit'
    }));
  });

  it('maps book types, progress deltas, and location formatting branches', () => {
    const component = TestBed.runInInjectionContext(() => new BookReadingSessionsComponent());
    const pageSession = createSession();
    const audioSession = createSession({bookType: 'AUDIOBOOK', startLocation: '00:01:00', endLocation: '00:02:00'});

    expect(component.getBookTypeIcon('PDF')).toBe('pi pi-file-pdf');
    expect(component.getBookTypeIcon('CBR')).toBe('pi pi-images');
    expect(component.getBookTypeIcon('UNKNOWN')).toBe('pi pi-file');
    expect(component.formatBookType('AUDIOBOOK')).toBe('translated:metadata.readingSessions.audio');
    expect(component.formatBookType('EPUB')).toBe('EPUB');

    expect(component.getProgressColor(3)).toBe('success');
    expect(component.getProgressColor(-1)).toBe('danger');
    expect(component.getProgressColor(0)).toBe('secondary');
    expect(component.getProgressDeltaClass(3)).toBe('progress-positive');
    expect(component.getProgressDeltaClass(-1)).toBe('progress-negative');
    expect(component.getProgressDeltaClass(0)).toBe('progress-neutral');
    expect(component.getProgressDeltaIcon(3)).toBe('pi pi-arrow-up');
    expect(component.getProgressDeltaIcon(-1)).toBe('pi pi-arrow-down');
    expect(component.getProgressDeltaIcon(0)).toBe('pi pi-minus');

    expect(component.isPageNumber('15')).toBe(true);
    expect(component.isPageNumber('chapter-1')).toBe(false);
    expect(component.isPageNumber(undefined)).toBe(false);
    expect(component.formatLocation(pageSession)).toBe('translated:metadata.readingSessions.page 15 → 20');
    expect(component.formatLocation(audioSession)).toBe('00:01:00 → 00:02:00');
    expect(component.formatLocation(createSession({startLocation: undefined, endLocation: undefined}))).toBe('-');
    expect(component.formatLocation(createSession({startLocation: 'chapter-1', endLocation: 'chapter-2'}))).toBe('-');
  });
});
