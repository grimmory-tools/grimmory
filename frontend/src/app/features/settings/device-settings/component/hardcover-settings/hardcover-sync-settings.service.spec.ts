import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {HardcoverSyncSettingsService} from './hardcover-sync-settings.service';

describe('HardcoverSyncSettingsService', () => {
  let service: HardcoverSyncSettingsService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        HardcoverSyncSettingsService,
      ],
    });

    service = TestBed.inject(HardcoverSyncSettingsService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('loads the saved hardcover sync settings', () => {
    let responseBody: unknown;

    service.getSettings().subscribe(response => {
      responseBody = response;
    });

    const request = httpTestingController.expectOne(req =>
      req.method === 'GET' && req.url.endsWith('/api/v1/hardcover-sync-settings')
    );
    request.flush({
      hardcoverApiKey: 'hardcover-key',
      hardcoverSyncEnabled: true,
    });

    expect(responseBody).toEqual({
      hardcoverApiKey: 'hardcover-key',
      hardcoverSyncEnabled: true,
    });
  });

  it('persists updated hardcover sync settings', () => {
    const payload = {
      hardcoverApiKey: 'next-key',
      hardcoverSyncEnabled: false,
    };

    service.updateSettings(payload).subscribe();

    const request = httpTestingController.expectOne(req =>
      req.method === 'PUT' && req.url.endsWith('/api/v1/hardcover-sync-settings')
    );
    expect(request.request.body).toEqual(payload);
    request.flush(payload);
  });
});
