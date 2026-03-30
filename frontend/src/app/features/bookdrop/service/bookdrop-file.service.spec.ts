import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {AuthService} from '../../../shared/service/auth.service';
import {BookdropFileApiService} from './bookdrop-file-api.service';
import {BookdropFileNotification, BookdropFileService} from './bookdrop-file.service';
import {UserService} from '../../settings/user-management/user.service';

describe('BookdropFileService', () => {
  let getNotification: ReturnType<typeof vi.fn>;
  let currentUser: ReturnType<typeof signal<{
    permissions: {admin: boolean; canAccessBookdrop: boolean};
  } | null>>;
  let token: ReturnType<typeof signal<string | null>>;
  let service: BookdropFileService;

  const summary: BookdropFileNotification = {
    pendingCount: 2,
    totalCount: 6,
    lastUpdatedAt: '2026-03-26T00:00:00Z',
  };

  beforeEach(() => {
    getNotification = vi.fn(() => of(summary));
    currentUser = signal({
      permissions: {
        admin: false,
        canAccessBookdrop: true,
      },
    });
    token = signal('token');

    TestBed.configureTestingModule({
      providers: [
        BookdropFileService,
        {provide: BookdropFileApiService, useValue: {getNotification}},
        {provide: AuthService, useValue: {token}},
        {provide: UserService, useValue: {currentUser}},
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('initializes from the API once for users who can access bookdrop and have a token', () => {
    service = TestBed.inject(BookdropFileService);
    TestBed.flushEffects();

    let latestSummary: BookdropFileNotification | undefined;
    service.summary$.subscribe(value => {
      latestSummary = value;
    });

    expect(getNotification).toHaveBeenCalledOnce();
    expect(latestSummary).toEqual(summary);
  });

  it('does not auto-refresh when the current user lacks access', () => {
    currentUser.set({
      permissions: {
        admin: false,
        canAccessBookdrop: false,
      },
    });

    service = TestBed.inject(BookdropFileService);
    TestBed.flushEffects();

    expect(getNotification).not.toHaveBeenCalled();
  });

  it('publishes incoming file summaries and pending state', () => {
    service = TestBed.inject(BookdropFileService);

    let hasPendingFiles: boolean | undefined;
    service.hasPendingFiles$.subscribe(value => {
      hasPendingFiles = value;
    });

    service.handleIncomingFile(summary);
    expect(hasPendingFiles).toBe(true);

    service.handleIncomingFile({...summary, pendingCount: 0});
    expect(hasPendingFiles).toBe(false);
  });

  it('warns when manual refresh fails', () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    getNotification.mockReturnValueOnce(throwError(() => new Error('boom')));
    service = TestBed.inject(BookdropFileService);
    TestBed.flushEffects();

    service.refresh();

    expect(warnSpy).toHaveBeenCalled();
  });
});
