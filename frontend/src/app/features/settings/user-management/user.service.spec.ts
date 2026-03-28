import {HttpTestingController} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {AuthService} from '../../../shared/service/auth.service';
import {createAuthServiceStub, createQueryClientHarness, flushSignalAndQueryEffects} from '../../../core/testing/query-testing';
import {CURRENT_USER_QUERY_KEY} from './user-query-keys';
import {type User, type UserSettings, UserService} from './user.service';

type BuildUserOverrides = Omit<Partial<User>, 'permissions' | 'userSettings'> & {
  permissions?: Partial<User['permissions']>;
  userSettings?: Partial<UserSettings>;
};

function buildUserSettings(overrides: Partial<UserSettings> = {}): UserSettings {
  return {
    perBookSetting: {pdf: 'pdf', epub: 'epub', cbx: 'cbx'},
    pdfReaderSetting: {pageSpread: 'off', pageZoom: '100%', showSidebar: true},
    epubReaderSetting: {
      theme: 'light',
      font: 'serif',
      fontSize: 16,
      flow: 'paginated',
      spread: 'auto',
      lineHeight: 1.5,
      margin: 1,
      letterSpacing: 0,
    },
    ebookReaderSetting: {
      lineHeight: 1.5,
      justify: true,
      hyphenate: true,
      maxColumnCount: 1,
      gap: 1,
      fontSize: 16,
      theme: 'light',
      maxInlineSize: 100,
      maxBlockSize: 100,
      fontFamily: 'serif',
      isDark: false,
      flow: 'paginated',
    },
    cbxReaderSetting: {pageSpread: 'EVEN', pageViewMode: 'SINGLE_PAGE', fitMode: 'AUTO'},
    newPdfReaderSetting: {pageSpread: 'EVEN', pageViewMode: 'SINGLE_PAGE', fitMode: 'AUTO'},
    sidebarLibrarySorting: {field: 'name', order: 'ASC'},
    sidebarShelfSorting: {field: 'name', order: 'ASC'},
    sidebarMagicShelfSorting: {field: 'name', order: 'ASC'},
    filterMode: 'and',
    metadataCenterViewMode: 'route',
    enableSeriesView: true,
    entityViewPreferences: {
      global: {
        sortKey: 'title',
        sortDir: 'ASC',
        view: 'GRID',
        coverSize: 100,
        seriesCollapsed: false,
        overlayBookType: false,
      },
      overrides: [],
    },
    koReaderEnabled: false,
    autoSaveMetadata: true,
    ...overrides,
  } as UserSettings;
}

function buildUser(overrides: BuildUserOverrides = {}): User {
  const {permissions: permissionOverrides, userSettings, ...userOverrides} = overrides;
  const permissions: User['permissions'] = {
    admin: false,
    canUpload: false,
    canDownload: false,
    canEmailBook: false,
    canDeleteBook: false,
    canEditMetadata: false,
    canManageLibrary: false,
    canManageMetadataConfig: false,
    canSyncKoReader: false,
    canSyncKobo: false,
    canAccessOpds: false,
    canAccessBookdrop: false,
    canAccessLibraryStats: false,
    canAccessUserStats: false,
    canAccessTaskManager: false,
    canManageEmailConfig: false,
    canManageGlobalPreferences: false,
    canManageIcons: false,
    canManageFonts: false,
    demoUser: false,
    canBulkAutoFetchMetadata: false,
    canBulkCustomFetchMetadata: false,
    canBulkEditMetadata: false,
    canBulkRegenerateCover: false,
    canMoveOrganizeFiles: false,
    canBulkLockUnlockMetadata: false,
    canBulkResetGrimmoryReadProgress: false,
    canBulkResetBookloreReadProgress: false,
    canBulkResetKoReaderReadProgress: false,
    canBulkResetBookReadStatus: false,
  };

  if (permissionOverrides) {
    Object.assign(permissions, permissionOverrides);
  }

  return {
    id: 7,
    username: 'reader',
    name: 'Reader',
    email: 'reader@example.test',
    assignedLibraries: [],
    permissions,
    userSettings: buildUserSettings(userSettings),
    ...userOverrides,
  };
}

async function flushCurrentUserQuery(): Promise<void> {
  await Promise.resolve();
  flushSignalAndQueryEffects();
  await new Promise(resolve => setTimeout(resolve, 0));
  flushSignalAndQueryEffects();
  await Promise.resolve();
  flushSignalAndQueryEffects();
}

describe('UserService', () => {
  let service: UserService;
  let httpTestingController: HttpTestingController;
  let authService: ReturnType<typeof createAuthServiceStub>;
  let queryClientHarness: ReturnType<typeof createQueryClientHarness>;

  beforeEach(() => {
    authService = createAuthServiceStub();
    queryClientHarness = createQueryClientHarness();
    queryClientHarness.queryClient.setDefaultOptions({
      queries: {
        retry: false,
      },
    });

    TestBed.configureTestingModule({
      providers: [
        ...queryClientHarness.providers,
        UserService,
        {provide: AuthService, useValue: authService},
      ],
    });

    service = TestBed.inject(UserService);
    httpTestingController = TestBed.inject(HttpTestingController);
    flushSignalAndQueryEffects();
  });

  afterEach(() => {
    httpTestingController.verify();
    queryClientHarness.queryClient.clear();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('eagerly fetches /users/me and normalizes the permission alias into current-user state', async () => {
    expect(service.currentUser()).toBeNull();
    expect(service.isUserLoading()).toBe(true);
    expect(service.userError()).toBeNull();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/users/me'));
    expect(request.request.method).toBe('GET');
    request.flush(buildUser({
      permissions: {
        canBulkResetGrimmoryReadProgress: undefined,
        canBulkResetBookloreReadProgress: true,
      },
    }));
    await flushCurrentUserQuery();

    expect(service.currentUser()).toEqual(expect.objectContaining({
      id: 7,
      username: 'reader',
    }));
    expect(service.currentUser()?.permissions.canBulkResetGrimmoryReadProgress).toBe(true);
    expect(service.currentUser()?.permissions.canBulkResetBookloreReadProgress).toBe(true);
    expect(service.isUserLoading()).toBe(false);
    expect(service.userError()).toBeNull();
  });

  it('derives a query error when the eager current-user request fails', async () => {
    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/users/me'));
    request.flush({message: 'boom'}, {status: 500, statusText: 'Server Error'});
    await flushCurrentUserQuery();

    expect(service.currentUser()).toBeNull();
    expect(service.isUserLoading()).toBe(false);
    expect(service.userError()).toBe('Failed to load user');
  });

  it('removes the current-user cache when the auth token is cleared', async () => {
    const removeQueriesSpy = vi.spyOn(queryClientHarness.queryClient, 'removeQueries');

    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/users/me')).flush(buildUser());
    await flushCurrentUserQuery();
    expect(queryClientHarness.queryClient.getQueryData(CURRENT_USER_QUERY_KEY)).toEqual(expect.objectContaining({id: 7}));

    authService.token.set(null);
    flushSignalAndQueryEffects();

    expect(removeQueriesSpy).toHaveBeenCalledWith({queryKey: CURRENT_USER_QUERY_KEY});
    expect(queryClientHarness.queryClient.getQueryData(CURRENT_USER_QUERY_KEY)).toBeUndefined();
    expect(service.currentUser()).toBeNull();
    expect(service.isUserLoading()).toBe(false);
    expect(service.userError()).toBeNull();
  });

  it('updates the cached current user after updateUserSetting succeeds', async () => {
    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/users/me')).flush(buildUser());
    await flushCurrentUserQuery();
    service.setInitialUser(buildUser({
      id: 21,
      userSettings: buildUserSettings({filterMode: 'and'}),
    }));
    await flushCurrentUserQuery();

    service.updateUserSetting(21, 'filterMode', 'or');

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/users/21/settings'));
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({key: 'filterMode', value: 'or'});
    expect(request.request.headers.get('Content-Type')).toBe('application/json');
    request.flush('');
    await flushCurrentUserQuery();

    expect(service.currentUser()?.userSettings.filterMode).toBe('or');
    expect(queryClientHarness.queryClient.getQueryData<User>(CURRENT_USER_QUERY_KEY)).toEqual(
      expect.objectContaining({
        id: 21,
        userSettings: expect.objectContaining({filterMode: 'or'}),
      }),
    );
  });
});
