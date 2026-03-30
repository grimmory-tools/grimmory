import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {ReadingSessionApiService} from './reading-session-api.service';
import {AudiobookSessionService} from './audiobook-session.service';

describe('AudiobookSessionService', () => {
  let createSession: ReturnType<typeof vi.fn>;
  let sendSessionBeacon: ReturnType<typeof vi.fn>;
  let service: AudiobookSessionService;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-03-26T00:00:00Z'));

    createSession = vi.fn(() => of(void 0));
    sendSessionBeacon = vi.fn(() => true);

    TestBed.configureTestingModule({
      providers: [
        AudiobookSessionService,
        {
          provide: ReadingSessionApiService,
          useValue: {
            createSession,
            sendSessionBeacon,
          },
        },
      ],
    });

    service = TestBed.inject(AudiobookSessionService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('starts a session and reports current listening time while playback is active', () => {
    service.startSession(7, 0, 1);

    vi.advanceTimersByTime(12_000);

    expect(service.isSessionActive()).toBe(true);
    expect(service.isPlaying()).toBe(true);
    expect(service.getCurrentListeningTime()).toBe(12);
  });

  it('accumulates listening time on pause using the playback rate', () => {
    service.startSession(7, 30_000, 1.5);

    vi.advanceTimersByTime(10_000);
    service.pauseSession(45_000);

    expect(service.isPlaying()).toBe(false);
    expect(service.getCurrentListeningTime()).toBe(15);
  });

  it('discards short sessions without sending them to the backend', () => {
    service.startSession(8, 0, 1);

    vi.advanceTimersByTime(20_000);
    service.endSession(20_000);

    expect(createSession).not.toHaveBeenCalled();
    expect(service.isSessionActive()).toBe(false);
  });

  it('sends completed sessions that meet the minimum duration threshold', () => {
    service.startSession(11, 0, 1, 99, 2);

    vi.advanceTimersByTime(31_000);
    service.endSession(90_000);

    expect(createSession).toHaveBeenCalledOnce();
    expect(createSession).toHaveBeenCalledWith(expect.objectContaining({
      bookId: 11,
      bookType: 'AUDIOBOOK',
      durationSeconds: 31,
      durationFormatted: '31s',
      startLocation: '0:00',
      endLocation: '1:30',
    }));
    expect(service.isSessionActive()).toBe(false);
  });

  it('sends a beacon on unload for long enough active sessions', () => {
    service.startSession(12, 65_000, 1);

    vi.advanceTimersByTime(35_000);
    window.dispatchEvent(new Event('beforeunload'));

    expect(sendSessionBeacon).toHaveBeenCalledOnce();
    expect(sendSessionBeacon).toHaveBeenCalledWith(expect.objectContaining({
      bookId: 12,
      bookType: 'AUDIOBOOK',
      durationSeconds: 35,
      startLocation: '1:05',
      endLocation: '1:05',
    }));
  });
});
