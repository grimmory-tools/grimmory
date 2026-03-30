import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {API_CONFIG} from '../../../core/config/api-config';
import {EmailService} from './email.service';

describe('EmailService', () => {
  let service: EmailService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        EmailService,
      ]
    });

    service = TestBed.inject(EmailService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('emails a book with the detailed payload', () => {
    const payload = {bookId: 1, providerId: 2, recipientId: 3, bookFileId: 4};

    service.emailBook(payload).subscribe(result => {
      expect(result).toBeNull();
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/email/book`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush(null);
  });

  it('emails a book quickly', () => {
    service.emailBookQuick(99).subscribe(result => {
      expect(result).toBeNull();
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/email/book/99`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush(null);
  });
});
