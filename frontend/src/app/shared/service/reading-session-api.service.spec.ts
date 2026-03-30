import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {API_CONFIG} from '../../core/config/api-config';
import {ReadingSessionApiService} from './reading-session-api.service';

describe('ReadingSessionApiService', () => {
  const baseUrl = `${API_CONFIG.BASE_URL}/api/v1/reading-sessions`;
  const originalSendBeaconDescriptor = Object.getOwnPropertyDescriptor(navigator, 'sendBeacon');

  let service: ReadingSessionApiService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ReadingSessionApiService,
      ]
    });

    service = TestBed.inject(ReadingSessionApiService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    if (originalSendBeaconDescriptor) {
      Object.defineProperty(navigator, 'sendBeacon', originalSendBeaconDescriptor);
    } else {
      Object.defineProperty(navigator, 'sendBeacon', {
        configurable: true,
        value: undefined,
      });
    }
    TestBed.resetTestingModule();
  });

  it('creates a reading session', () => {
    const session = {
      bookId: 3,
      bookType: 'EPUB' as const,
      startTime: '2024-01-01T00:00:00Z',
      endTime: '2024-01-01T00:10:00Z',
      durationSeconds: 600,
      durationFormatted: '10m 0s',
    };

    service.createSession(session).subscribe();

    const request = httpTestingController.expectOne(baseUrl);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(session);
    request.flush(null);
  });

  it('loads paged sessions for a book', () => {
    service.getSessionsByBookId(3, 2, 20).subscribe();

    const request = httpTestingController.expectOne(
      requestMatch => requestMatch.url === `${baseUrl}/book/3`
        && requestMatch.params.get('page') === '2'
        && requestMatch.params.get('size') === '20'
    );

    expect(request.request.method).toBe('GET');
    request.flush({
      content: [],
      page: {
        totalElements: 0,
        totalPages: 0,
        number: 2,
        size: 20,
      }
    });
  });

  it('uses sendBeacon when the browser supports it and returns false when it throws', () => {
    const session = {
      bookId: 3,
      bookType: 'EPUB' as const,
      startTime: '2024-01-01T00:00:00Z',
      endTime: '2024-01-01T00:10:00Z',
      durationSeconds: 600,
      durationFormatted: '10m 0s',
    };
    const sendBeaconSpy = vi.fn();
    Object.defineProperty(navigator, 'sendBeacon', {
      configurable: true,
      value: sendBeaconSpy,
    });

    sendBeaconSpy.mockReturnValue(true);
    expect(service.sendSessionBeacon(session)).toBe(true);

    sendBeaconSpy.mockImplementation(() => {
      throw new Error('unsupported');
    });
    expect(service.sendSessionBeacon(session)).toBe(false);
  });
});
