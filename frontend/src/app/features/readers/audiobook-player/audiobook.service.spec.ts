import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {AuthService} from '../../../shared/service/auth.service';
import {AudiobookInfo, AudiobookProgress} from './audiobook.model';
import {AudiobookService} from './audiobook.service';

describe('AudiobookService', () => {
  let service: AudiobookService;
  let httpTestingController: HttpTestingController;
  let getInternalAccessToken: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    getInternalAccessToken = vi.fn(() => 'token 123');

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        AudiobookService,
        {
          provide: AuthService,
          useValue: {getInternalAccessToken},
        },
      ],
    });

    service = TestBed.inject(AudiobookService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('fetches audiobook info and forwards the optional book type parameter', () => {
    const info: AudiobookInfo = {
      bookId: 7,
      bookFileId: 8,
      durationMs: 123_000,
      folderBased: false,
    };
    let result: AudiobookInfo | undefined;

    service.getAudiobookInfo(7, 'AUDIOBOOK').subscribe(value => {
      result = value;
    });

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/audiobooks/7/info'));
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('bookType')).toBe('AUDIOBOOK');
    request.flush(info);

    expect(result).toEqual(info);
  });

  it('builds stream and cover URLs with an encoded token and optional track index', () => {
    expect(service.getStreamUrl(4, 2)).toContain('/api/v1/audiobooks/4/stream?ngsw-bypass=true&token=token%20123&trackIndex=2');
    expect(service.getTrackStreamUrl(4, 9)).toContain('/api/v1/audiobooks/4/track/9/stream?ngsw-bypass=true&token=token%20123');
    expect(service.getEmbeddedCoverUrl(4)).toContain('/api/v1/audiobooks/4/cover?ngsw-bypass=true&token=token%20123');
  });

  it('saves audiobook progress directly when no book file id is provided', () => {
    const progress: AudiobookProgress = {
      positionMs: 50_000,
      trackIndex: 3,
      percentage: 42.5,
    };
    let completed = false;

    service.saveProgress(9, progress).subscribe(() => {
      completed = true;
    });

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books/progress'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      bookId: 9,
      audiobookProgress: progress,
    });
    request.flush(null);

    expect(completed).toBe(true);
  });

  it('maps audiobook progress into a file progress payload when a book file id is present', () => {
    const progress: AudiobookProgress = {
      positionMs: 75_000,
      trackIndex: 5,
      percentage: 80,
    };

    service.saveProgress(12, progress, 99).subscribe();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books/progress'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      bookId: 12,
      fileProgress: {
        bookFileId: 99,
        positionData: '75000',
        positionHref: '5',
        progressPercent: 80,
      },
    });
    request.flush(null);
  });
});
