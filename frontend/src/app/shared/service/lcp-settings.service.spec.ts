import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {API_CONFIG} from '../../core/config/api-config';
import {LcpSettingsService} from './lcp-settings.service';

describe('LcpSettingsService', () => {
  let service: LcpSettingsService;
  let http: HttpTestingController;
  const url = `${API_CONFIG.BASE_URL}/api/v1/users/me/lcp-settings`;

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideHttpClient(), provideHttpClientTesting()]});
    service = TestBed.inject(LcpSettingsService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('puts the code in the request body, never in the URL', () => {
    service.save('fixture-code').subscribe();
    const request = http.expectOne(url);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({passphrase: 'fixture-code'});
    expect(request.request.urlWithParams).not.toContain('fixture-code');
    request.flush(null);
  });

  it('queries and removes only the current user settings', () => {
    service.getStatus().subscribe(value => expect(value).toEqual({hasDefaultPassphrase: true}));
    http.expectOne(url).flush({hasDefaultPassphrase: true});
    service.clear().subscribe();
    const request = http.expectOne(url);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
});
