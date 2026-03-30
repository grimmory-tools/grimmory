import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {API_CONFIG} from '../../core/config/api-config';
import {OidcGroupMappingService} from './oidc-group-mapping.service';

describe('OidcGroupMappingService', () => {
  const baseUrl = `${API_CONFIG.BASE_URL}/api/v1/admin/oidc-group-mappings`;

  let service: OidcGroupMappingService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        OidcGroupMappingService,
      ]
    });

    service = TestBed.inject(OidcGroupMappingService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('fetches all mappings', () => {
    service.getAll().subscribe();

    const request = httpTestingController.expectOne(baseUrl);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('creates a mapping', () => {
    const mapping = {
      oidcGroupClaim: 'admins',
      isAdmin: true,
      permissions: ['BOOK_EDIT'],
      libraryIds: [1],
      description: 'Admin group',
    };

    service.create(mapping).subscribe();

    const request = httpTestingController.expectOne(baseUrl);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(mapping);
    request.flush(mapping);
  });

  it('updates and deletes mappings by id', () => {
    const mapping = {
      oidcGroupClaim: 'members',
      isAdmin: false,
      permissions: ['BOOK_VIEW'],
      libraryIds: [2],
      description: 'Member group',
    };

    service.update(7, mapping).subscribe();
    const updateRequest = httpTestingController.expectOne(`${baseUrl}/7`);
    expect(updateRequest.request.method).toBe('PUT');
    expect(updateRequest.request.body).toEqual(mapping);
    updateRequest.flush(mapping);

    service.delete(7).subscribe();
    const deleteRequest = httpTestingController.expectOne(`${baseUrl}/7`);
    expect(deleteRequest.request.method).toBe('DELETE');
    deleteRequest.flush(null);
  });
});
