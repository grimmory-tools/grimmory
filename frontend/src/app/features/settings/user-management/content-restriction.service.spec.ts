import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {API_CONFIG} from '../../../core/config/api-config';
import {ContentRestriction, ContentRestrictionMode, ContentRestrictionType} from './content-restriction.model';
import {ContentRestrictionService} from './content-restriction.service';

describe('ContentRestrictionService', () => {
  let service: ContentRestrictionService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ContentRestrictionService,
      ]
    });

    service = TestBed.inject(ContentRestrictionService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('loads content restrictions', () => {
    const restrictions = [{
      id: 1,
      userId: 11,
      restrictionType: ContentRestrictionType.TAG,
      mode: ContentRestrictionMode.EXCLUDE,
      value: 'TAG'
    } as ContentRestriction];

    service.getUserRestrictions(11).subscribe(result => {
      expect(result).toEqual(restrictions);
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/users/11/content-restrictions`);
    expect(request.request.method).toBe('GET');
    request.flush(restrictions);
  });

  it('adds a restriction', () => {
    const restriction = {
      id: 1,
      userId: 11,
      restrictionType: ContentRestrictionType.TAG,
      mode: ContentRestrictionMode.EXCLUDE,
      value: 'TAG'
    } as ContentRestriction;

    service.addRestriction(11, restriction).subscribe(result => {
      expect(result).toEqual(restriction);
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/users/11/content-restrictions`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(restriction);
    request.flush(restriction);
  });

  it('updates restrictions', () => {
    const restrictions = [{
      id: 1,
      userId: 11,
      restrictionType: ContentRestrictionType.TAG,
      mode: ContentRestrictionMode.EXCLUDE,
      value: 'TAG'
    } as ContentRestriction];

    service.updateRestrictions(11, restrictions).subscribe(result => {
      expect(result).toEqual(restrictions);
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/users/11/content-restrictions`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(restrictions);
    request.flush(restrictions);
  });

  it('deletes a single restriction', () => {
    service.deleteRestriction(11, 9).subscribe(result => {
      expect(result).toBeNull();
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/users/11/content-restrictions/9`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });

  it('deletes all restrictions for a user', () => {
    service.deleteAllRestrictions(11).subscribe(result => {
      expect(result).toBeNull();
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/users/11/content-restrictions`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
});
