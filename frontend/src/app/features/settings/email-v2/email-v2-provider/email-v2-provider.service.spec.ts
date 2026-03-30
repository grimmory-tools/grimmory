import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {API_CONFIG} from '../../../../core/config/api-config';
import {EmailV2ProviderService} from './email-v2-provider.service';

describe('EmailV2ProviderService', () => {
  let service: EmailV2ProviderService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        EmailV2ProviderService,
      ]
    });

    service = TestBed.inject(EmailV2ProviderService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('loads providers', () => {
    service.getEmailProviders().subscribe(providers => {
      expect(providers).toEqual([{id: 1, userId: 10, name: 'SMTP', host: 'mail.test', port: 587, username: 'reader', password: 'secret', fromAddress: 'reader@test', auth: true, startTls: true, defaultProvider: true, shared: false, isEditing: false}]);
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/email/providers`);
    expect(request.request.method).toBe('GET');
    request.flush([{id: 1, userId: 10, name: 'SMTP', host: 'mail.test', port: 587, username: 'reader', password: 'secret', fromAddress: 'reader@test', auth: true, startTls: true, defaultProvider: true, shared: false, isEditing: false}]);
  });

  it('creates a provider', () => {
    const provider = {id: 1, userId: 10, name: 'SMTP', host: 'mail.test', port: 587, username: 'reader', password: 'secret', fromAddress: 'reader@test', auth: true, startTls: true, defaultProvider: false, shared: false, isEditing: false};

    service.createEmailProvider(provider).subscribe(result => {
      expect(result).toEqual(provider);
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/email/providers`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(provider);
    request.flush(provider);
  });

  it('updates a provider', () => {
    const provider = {id: 2, userId: 10, name: 'SMTP', host: 'mail.test', port: 587, username: 'reader', password: 'secret', fromAddress: 'reader@test', auth: true, startTls: true, defaultProvider: false, shared: false, isEditing: true};

    service.updateProvider(provider).subscribe(result => {
      expect(result).toEqual(provider);
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/email/providers/2`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(provider);
    request.flush(provider);
  });

  it('deletes a provider', () => {
    service.deleteProvider(3).subscribe(result => {
      expect(result).toBeNull();
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/email/providers/3`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });

  it('marks a provider as default', () => {
    service.setDefaultProvider(4).subscribe(result => {
      expect(result).toBeNull();
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/email/providers/4/set-default`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({});
    request.flush(null);
  });
});
