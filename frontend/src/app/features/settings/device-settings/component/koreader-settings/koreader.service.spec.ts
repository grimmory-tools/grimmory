import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {KoreaderService} from './koreader.service';

describe('KoreaderService', () => {
  let service: KoreaderService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        KoreaderService,
      ],
    });

    service = TestBed.inject(KoreaderService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('creates a KOReader user with the expected payload', () => {
    let responseBody: unknown;

    service.createUser('reader', 'secret123').subscribe(response => {
      responseBody = response;
    });

    const request = httpTestingController.expectOne(req =>
      req.method === 'PUT' && req.url.endsWith('/api/v1/koreader-users/me')
    );
    expect(request.request.body).toEqual({
      username: 'reader',
      password: 'secret123',
    });

    request.flush({
      username: 'reader',
      password: 'secret123',
      syncEnabled: true,
    });

    expect(responseBody).toEqual({
      username: 'reader',
      password: 'secret123',
      syncEnabled: true,
    });
  });

  it('loads the current KOReader user from the profile endpoint', () => {
    let responseBody: unknown;

    service.getUser().subscribe(response => {
      responseBody = response;
    });

    const request = httpTestingController.expectOne(req =>
      req.method === 'GET' && req.url.endsWith('/api/v1/koreader-users/me')
    );
    request.flush({
      username: 'reader',
      password: 'secret123',
      syncEnabled: false,
      syncWithGrimmoryReader: true,
    });

    expect(responseBody).toEqual({
      username: 'reader',
      password: 'secret123',
      syncEnabled: false,
      syncWithGrimmoryReader: true,
    });
  });

  it('patches the sync-enabled flag using the enabled query parameter', () => {
    service.toggleSync(true).subscribe();

    const request = httpTestingController.expectOne(req =>
      req.method === 'PATCH' && req.url.endsWith('/api/v1/koreader-users/me/sync')
    );
    expect(request.request.params.get('enabled')).toBe('true');
    expect(request.request.body).toBeNull();
    request.flush(null);
  });

  it('uses the Grimmory sync-progress endpoint before falling back to the legacy Booklore route', () => {
    let completed = false;

    service.toggleSyncProgressWithGrimmoryReader(false).subscribe(() => {
      completed = true;
    });

    const grimmoryRequest = httpTestingController.expectOne(req =>
      req.method === 'PATCH' && req.url.endsWith('/api/v1/koreader-users/me/sync-progress-with-grimmory')
    );
    expect(grimmoryRequest.request.params.get('enabled')).toBe('false');
    grimmoryRequest.flush('missing route', {status: 404, statusText: 'Not Found'});

    const legacyRequest = httpTestingController.expectOne(req =>
      req.method === 'PATCH' && req.url.endsWith('/api/v1/koreader-users/me/sync-progress-with-booklore')
    );
    expect(legacyRequest.request.params.get('enabled')).toBe('false');
    legacyRequest.flush(null);

    expect(completed).toBe(true);
  });
});
