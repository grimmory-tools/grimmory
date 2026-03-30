import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient} from '@angular/common/http';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {API_CONFIG} from '../../../core/config/api-config';
import {OpdsService} from './opds.service';

describe('OpdsService', () => {
  let service: OpdsService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        OpdsService,
      ]
    });

    service = TestBed.inject(OpdsService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('loads OPDS users', () => {
    service.getUser().subscribe(users => {
      expect(users).toEqual([{id: 1, userId: 10, username: 'reader'}]);
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v2/opds-users`);
    expect(request.request.method).toBe('GET');
    request.flush([{id: 1, userId: 10, username: 'reader'}]);
  });

  it('creates an OPDS user', () => {
    const payload = {username: 'reader', password: 'secret', sortOrder: 'TITLE_ASC' as const};

    service.createUser(payload).subscribe(user => {
      expect(user).toEqual({id: 1, userId: 10, username: 'reader', sortOrder: 'TITLE_ASC'});
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v2/opds-users`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush({id: 1, userId: 10, username: 'reader', sortOrder: 'TITLE_ASC'});
  });

  it('updates an OPDS user sort order', () => {
    service.updateUser(5, 'AUTHOR_DESC').subscribe(user => {
      expect(user).toEqual({id: 5, userId: 10, username: 'reader', sortOrder: 'AUTHOR_DESC'});
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v2/opds-users/5`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({sortOrder: 'AUTHOR_DESC'});
    request.flush({id: 5, userId: 10, username: 'reader', sortOrder: 'AUTHOR_DESC'});
  });

  it('deletes an OPDS credential', () => {
    service.deleteCredential(7).subscribe(response => {
      expect(response).toBeNull();
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v2/opds-users/7`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
});
