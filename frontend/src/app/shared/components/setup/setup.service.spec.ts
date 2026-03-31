import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {API_CONFIG} from '../../../core/config/api-config';
import {SetupService} from './setup.service';

describe('SetupService', () => {
  const setupUrl = `${API_CONFIG.BASE_URL}/api/v1/setup`;

  let service: SetupService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        SetupService,
      ]
    });

    service = TestBed.inject(SetupService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('posts the admin setup payload to the setup endpoint', () => {
    service.createAdmin({email: 'admin@example.com', password: 'secret'}).subscribe();

    const request = httpTestingController.expectOne(setupUrl);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      email: 'admin@example.com',
      password: 'secret',
    });

    request.flush(null);
  });
});
