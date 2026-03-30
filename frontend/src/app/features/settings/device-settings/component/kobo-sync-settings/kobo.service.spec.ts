import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {KoboService} from './kobo.service';

describe('KoboService', () => {
  let service: KoboService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        KoboService,
      ],
    });

    service = TestBed.inject(KoboService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('loads the current kobo sync settings', () => {
    let responseBody: unknown;

    service.getUser().subscribe(response => {
      responseBody = response;
    });

    const request = httpTestingController.expectOne(req =>
      req.method === 'GET' && req.url.endsWith('/api/v1/kobo-settings')
    );
    request.flush({
      token: 'abc123',
      syncEnabled: true,
      autoAddToShelf: false,
      twoWayProgressSync: true,
    });

    expect(responseBody).toEqual({
      token: 'abc123',
      syncEnabled: true,
      autoAddToShelf: false,
      twoWayProgressSync: true,
    });
  });

  it('creates or refreshes the kobo token through the token endpoint', () => {
    service.createOrUpdateToken().subscribe();

    const request = httpTestingController.expectOne(req =>
      req.method === 'PUT' && req.url.endsWith('/api/v1/kobo-settings/token')
    );
    expect(request.request.body).toBeNull();
    request.flush({
      token: 'new-token',
      syncEnabled: false,
      autoAddToShelf: true,
      twoWayProgressSync: false,
    });
  });

  it('persists updated kobo settings with a PUT request', () => {
    const payload = {
      token: 'abc123',
      syncEnabled: true,
      autoAddToShelf: true,
      twoWayProgressSync: false,
      progressMarkAsReadingThreshold: 25,
      progressMarkAsFinishedThreshold: 95,
    };

    service.updateSettings(payload).subscribe();

    const request = httpTestingController.expectOne(req =>
      req.method === 'PUT' && req.url.endsWith('/api/v1/kobo-settings')
    );
    expect(request.request.body).toEqual(payload);
    request.flush(payload);
  });
});
