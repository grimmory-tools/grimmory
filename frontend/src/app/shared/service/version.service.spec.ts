import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {API_CONFIG} from '../../core/config/api-config';
import {VersionService} from './version.service';

describe('VersionService', () => {
  let service: VersionService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        VersionService,
      ]
    });

    service = TestBed.inject(VersionService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('fetches the current application version', () => {
    service.getVersion().subscribe(version => {
      expect(version).toEqual({current: '1.0.0', latest: '1.0.1'});
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/version`);
    expect(request.request.method).toBe('GET');
    request.flush({current: '1.0.0', latest: '1.0.1'});
  });

  it('fetches the changelog entries', () => {
    service.getChangelog().subscribe(entries => {
      expect(entries).toEqual([{
        version: '1.0.1',
        name: 'Patch release',
        changelog: 'Bug fixes',
        url: 'https://example.test/release/1.0.1',
        publishedAt: '2026-03-26T00:00:00Z',
      }]);
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/version/changelog`);
    expect(request.request.method).toBe('GET');
    request.flush([{
      version: '1.0.1',
      name: 'Patch release',
      changelog: 'Bug fixes',
      url: 'https://example.test/release/1.0.1',
      publishedAt: '2026-03-26T00:00:00Z',
    }]);
  });
});
