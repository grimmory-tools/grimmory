import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {API_CONFIG} from '../../../../core/config/api-config';
import {EmailV2RecipientService} from './email-v2-recipient.service';

describe('EmailV2RecipientService', () => {
  let service: EmailV2RecipientService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        EmailV2RecipientService,
      ]
    });

    service = TestBed.inject(EmailV2RecipientService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('loads recipients', () => {
    service.getRecipients().subscribe(recipients => {
      expect(recipients).toEqual([{id: 1, email: 'reader@test', name: 'Reader', defaultRecipient: true, isEditing: false}]);
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/email/recipients`);
    expect(request.request.method).toBe('GET');
    request.flush([{id: 1, email: 'reader@test', name: 'Reader', defaultRecipient: true, isEditing: false}]);
  });

  it('creates a recipient', () => {
    const recipient = {id: 1, email: 'reader@test', name: 'Reader', defaultRecipient: false, isEditing: false};

    service.createRecipient(recipient).subscribe(result => {
      expect(result).toEqual(recipient);
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/email/recipients`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(recipient);
    request.flush(recipient);
  });

  it('updates a recipient', () => {
    const recipient = {id: 2, email: 'reader@test', name: 'Reader', defaultRecipient: false, isEditing: true};

    service.updateRecipient(recipient).subscribe(result => {
      expect(result).toEqual(recipient);
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/email/recipients/2`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(recipient);
    request.flush(recipient);
  });

  it('deletes a recipient', () => {
    service.deleteRecipient(3).subscribe(result => {
      expect(result).toBeNull();
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/email/recipients/3`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });

  it('marks a recipient as default', () => {
    service.setDefaultRecipient(4).subscribe(result => {
      expect(result).toBeNull();
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/email/recipients/4/set-default`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({});
    request.flush(null);
  });
});
