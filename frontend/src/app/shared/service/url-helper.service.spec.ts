import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {API_CONFIG} from '../../core/config/api-config';
import {AuthService} from './auth.service';
import {UrlHelperService} from './url-helper.service';

describe('UrlHelperService', () => {
  const authServiceStub = {
    getInternalAccessToken: vi.fn(),
  };

  let service: UrlHelperService;

  beforeEach(() => {
    authServiceStub.getInternalAccessToken.mockReturnValue('token-123');

    TestBed.configureTestingModule({
      providers: [
        UrlHelperService,
        {provide: AuthService, useValue: authServiceStub},
      ],
    });

    service = TestBed.inject(UrlHelperService);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns null for thumbnail when no coverUpdatedOn is provided', () => {
    expect(service.getThumbnailUrl(42)).toBeNull();
  });

  it('appends the token to thumbnail urls that already have a cache key', () => {
    const url = service.getThumbnailUrl(42, 'updated-2024');

    expect(url).toBe(`${API_CONFIG.BASE_URL}/api/v1/media/book/42/thumbnail?updated-2024&token=token-123`);
  });

  it('returns null for cover url when no coverUpdatedOn is provided', () => {
    expect(service.getCoverUrl(42)).toBeNull();
  });

  it('returns null for audiobook cover when no updated marker is provided', () => {
    expect(service.getAudiobookCoverUrl(42)).toBeNull();
  });

  it('returns null for audiobook thumbnail when no updated marker is provided', () => {
    expect(service.getAudiobookThumbnailUrl(42)).toBeNull();
  });

  it('uses the audiobook media url when an updated marker is present', () => {
    expect(service.getAudiobookThumbnailUrl(42, 'updated-2024')).toBe(`${API_CONFIG.BASE_URL}/api/v1/media/book/42/audiobook-thumbnail?updated-2024&token=token-123`);
  });

  it('returns direct thumbnail and backup urls without a token when auth is unavailable', () => {
    authServiceStub.getInternalAccessToken.mockReturnValue(null);

    expect(service.getDirectThumbnailUrl(42)).toBe(`${API_CONFIG.BASE_URL}/api/v1/media/book/42/thumbnail`);
    expect(service.getBackupCoverUrl(42)).toBe(`${API_CONFIG.BASE_URL}/api/v1/media/book/42/backup-cover`);
  });
});
