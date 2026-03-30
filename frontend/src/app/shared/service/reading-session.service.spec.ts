import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {ReadingSessionApiService} from './reading-session-api.service';
import {ReadingSessionService} from './reading-session.service';

describe('ReadingSessionService', () => {
  let createSession: ReturnType<typeof vi.fn>;
  let sendSessionBeacon: ReturnType<typeof vi.fn>;
  let service: ReadingSessionService;
  let hidden = false;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-03-26T00:00:00Z'));
    hidden = false;

    Object.defineProperty(document, 'hidden', {
      configurable: true,
      get: () => hidden,
    });

    createSession = vi.fn(() => of(void 0));
    sendSessionBeacon = vi.fn(() => true);

    TestBed.configureTestingModule({
      providers: [
        ReadingSessionService,
        {
          provide: ReadingSessionApiService,
          useValue: {
            createSession,
            sendSessionBeacon,
          },
        },
      ],
    });

    service = TestBed.inject(ReadingSessionService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('starts and tracks an active reading session', () => {
    service.startSession(4, 'EPUB', 'chapter-1', 12.345);

    expect(service.isSessionActive()).toBe(true);
  });

  it('discards sessions that are shorter than the minimum duration', () => {
    service.startSession(5, 'EPUB', 'start', 10);
    service.updateProgress('middle', 25);

    vi.advanceTimersByTime(20_000);
    service.endSession('end', 35);

    expect(createSession).not.toHaveBeenCalled();
    expect(service.isSessionActive()).toBe(false);
  });

  it('sends completed sessions to the backend with rounded progress values', () => {
    service.startSession(6, 'PDF', 'page-1', 12.3456);
    service.updateProgress('page-9', 45.6789);

    vi.advanceTimersByTime(31_000);
    service.endSession();

    expect(createSession).toHaveBeenCalledOnce();
    expect(createSession).toHaveBeenCalledWith(expect.objectContaining({
      bookId: 6,
      bookType: 'PDF',
      durationSeconds: 31,
      durationFormatted: '31s',
      startLocation: 'page-1',
      endLocation: 'page-9',
      startProgress: 12.35,
      endProgress: 45.68,
      progressDelta: 33.33,
    }));
    expect(service.isSessionActive()).toBe(false);
  });

  it('sends a beacon on unload for long enough active sessions', () => {
    service.startSession(7, 'EPUB', 'cfi-1', 5);
    service.updateProgress('cfi-2', 9.5);

    vi.advanceTimersByTime(35_000);
    window.dispatchEvent(new Event('beforeunload'));

    expect(sendSessionBeacon).toHaveBeenCalledOnce();
    expect(sendSessionBeacon).toHaveBeenCalledWith(expect.objectContaining({
      bookId: 7,
      bookType: 'EPUB',
      durationSeconds: 35,
      startLocation: 'cfi-1',
      endLocation: 'cfi-2',
      startProgress: 5,
      endProgress: 9.5,
      progressDelta: undefined,
    }));
  });

  it('ends sessions that stay hidden longer than the idle timeout once the tab becomes visible again', () => {
    service.startSession(8, 'EPUB', 'cfi-1', 1);
    service.updateProgress('cfi-2', 5);

    hidden = true;
    document.dispatchEvent(new Event('visibilitychange'));

    vi.advanceTimersByTime(5 * 60 * 1000 + 5_000);

    hidden = false;
    document.dispatchEvent(new Event('visibilitychange'));

    expect(createSession).toHaveBeenCalledOnce();
    expect(service.isSessionActive()).toBe(false);
  });
});
