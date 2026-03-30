import {HttpErrorResponse} from '@angular/common/http';
import {TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {of, throwError} from 'rxjs';

import {AppSettingKey, AppSettings, OidcProviderDetails} from '../../../shared/model/app-settings.model';
import {OidcGroupMapping} from '../../../shared/model/oidc-group-mapping.model';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {OidcGroupMappingService} from '../../../shared/service/oidc-group-mapping.service';
import {LibraryService} from '../../../features/book/service/library.service';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {AuthenticationSettingsComponent} from './authentication-settings.component';

function createSettings(overrides: Partial<AppSettings> = {}): AppSettings {
  return {
    oidcEnabled: false,
    oidcAutoProvisionDetails: undefined,
    oidcProviderDetails: undefined,
    oidcSessionDurationHours: null,
    oidcGroupSyncMode: null,
    oidcForceOnlyMode: false,
    ...overrides,
  } as AppSettings;
}

function completeProvider(overrides: Partial<OidcProviderDetails> = {}): OidcProviderDetails {
  return {
    providerName: 'Example IdP',
    clientId: 'client-id',
    clientSecret: 'secret',
    issuerUri: 'https://issuer.example',
    scopes: 'openid profile email',
    claimMapping: {
      username: 'preferred_username',
      email: 'email',
      name: 'given_name',
      groups: 'groups',
    },
    ...overrides,
  };
}

describe('AuthenticationSettingsComponent', () => {
  const appSettingsSignal = signal<AppSettings | null>(null);
  const librariesSignal = signal([{id: 1, name: 'Main Library'}, {id: 2, name: 'Overflow'}]);

  const appSettingsService = {
    appSettings: appSettingsSignal,
    toggleOidcEnabled: vi.fn(),
    saveSettings: vi.fn(),
    testOidcConnection: vi.fn(),
  };

  const groupMappingService = {
    getAll: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  };

  const messageService = {
    add: vi.fn(),
  };

  const translocoService = {
    translate: vi.fn((key: string) => key),
  };

  const libraryService = {
    libraries: librariesSignal,
  };

  let component: AuthenticationSettingsComponent;

  beforeEach(() => {
    vi.restoreAllMocks();
    appSettingsSignal.set(null);
    librariesSignal.set([{id: 1, name: 'Main Library'}, {id: 2, name: 'Overflow'}]);
    appSettingsService.toggleOidcEnabled.mockReset();
    appSettingsService.saveSettings.mockReset();
    appSettingsService.testOidcConnection.mockReset();
    groupMappingService.getAll.mockReset();
    groupMappingService.create.mockReset();
    groupMappingService.update.mockReset();
    groupMappingService.delete.mockReset();
    messageService.add.mockReset();
    translocoService.translate.mockClear();

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        {provide: AppSettingsService, useValue: appSettingsService},
        {provide: LibraryService, useValue: libraryService},
        {provide: OidcGroupMappingService, useValue: groupMappingService},
        {provide: MessageService, useValue: messageService},
        {provide: TranslocoService, useValue: translocoService},
      ]
    });

    component = TestBed.runInInjectionContext(() => new AuthenticationSettingsComponent());
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('loads defaults when OIDC is disabled and skips group mapping refresh', () => {
    component.loadSettings(createSettings({
      oidcEnabled: false,
      oidcAutoProvisionDetails: undefined,
      oidcProviderDetails: undefined,
      oidcSessionDurationHours: null,
      oidcGroupSyncMode: null,
      oidcForceOnlyMode: false,
    }));

    expect(component.oidcEnabled).toBe(false);
    expect(component.autoUserProvisioningEnabled).toBe(false);
    expect(component.allowLocalAccountLinking).toBe(true);
    expect(component.selectedPermissions).toEqual([]);
    expect(component.editingLibraryIds).toEqual([]);
    expect(component.oidcProvider.claimMapping).toEqual({
      username: 'preferred_username',
      email: 'email',
      name: 'given_name',
      groups: '',
    });
    expect(groupMappingService.getAll).not.toHaveBeenCalled();
  });

  it('applies loaded OIDC settings and refreshes group mappings when enabled', () => {
    const mappings: OidcGroupMapping[] = [
      {id: 1, oidcGroupClaim: 'admins', isAdmin: true, permissions: ['permissionUpload'], libraryIds: [1], description: 'Admins'},
    ];
    groupMappingService.getAll.mockReturnValue(of(mappings));

    component.loadSettings(createSettings({
      oidcEnabled: true,
      oidcAutoProvisionDetails: {
        enableAutoProvisioning: true,
        allowLocalAccountLinking: false,
        defaultPermissions: ['permissionUpload', 'permissionEmailBook'],
        defaultLibraryIds: [1, 2],
      },
      oidcProviderDetails: completeProvider({
        claimMapping: {
          username: 'sub',
          email: 'mail',
          name: 'displayName',
          groups: 'memberOf',
        },
      }),
      oidcSessionDurationHours: 12,
      oidcGroupSyncMode: 'ON_LOGIN',
      oidcForceOnlyMode: true,
    }));

    expect(component.oidcEnabled).toBe(true);
    expect(component.autoUserProvisioningEnabled).toBe(true);
    expect(component.allowLocalAccountLinking).toBe(false);
    expect(component.selectedPermissions).toEqual(['permissionUpload', 'permissionEmailBook']);
    expect(component.availablePermissions.find(p => p.value === 'permissionUpload')?.selected).toBe(true);
    expect(component.availablePermissions.find(p => p.value === 'permissionDeleteBook')?.selected).toBe(false);
    expect(component.editingLibraryIds).toEqual([1, 2]);
    expect(component.sessionDurationHours).toBe(12);
    expect(component.groupSyncMode).toBe('ON_LOGIN');
    expect(component.oidcForceOnlyMode).toBe(true);
    expect(component.oidcProvider.providerName).toBe('Example IdP');
    expect(component.oidcProvider.claimMapping).toEqual({
      username: 'sub',
      email: 'mail',
      name: 'displayName',
      groups: 'memberOf',
    });
    expect(component.groupMappings).toEqual(mappings);
    expect(groupMappingService.getAll).toHaveBeenCalledOnce();
  });

  it('keeps OIDC disabled when the provider form is incomplete', () => {
    component.oidcEnabled = true;

    component.toggleOidcEnabled();

    expect(appSettingsService.toggleOidcEnabled).not.toHaveBeenCalled();
    expect(messageService.add).not.toHaveBeenCalled();
  });

  it('opens a new group mapping dialog with blank state', () => {
    component.editingGroupMapping = {
      oidcGroupClaim: 'existing',
      isAdmin: true,
      permissions: ['permissionUpload'],
      libraryIds: [1],
      description: 'Existing',
    };
    component.editingGroupMappingPerms = component.availablePermissions.map(permission => ({
      ...permission,
      selected: true,
    }));
    component.editingGroupMappingLibraryIds = [1];

    component.openNewGroupMapping();

    expect(component.showGroupMappingDialog).toBe(true);
    expect(component.editingGroupMapping).toEqual({
      oidcGroupClaim: '',
      isAdmin: false,
      permissions: [],
      libraryIds: [],
      description: '',
    });
    expect(component.editingGroupMappingPerms.every(permission => !permission.selected)).toBe(true);
    expect(component.editingGroupMappingLibraryIds).toEqual([]);
  });

  it('opens an existing group mapping dialog with cloned state and selected permissions', () => {
    const mapping: OidcGroupMapping = {
      id: 42,
      oidcGroupClaim: 'admins',
      isAdmin: true,
      permissions: ['permissionRead', 'permissionUpload'],
      libraryIds: [1, 2],
      description: 'Administrators',
    };

    component.openEditGroupMapping(mapping);

    expect(component.showGroupMappingDialog).toBe(true);
    expect(component.editingGroupMapping).toEqual(mapping);
    expect(component.editingGroupMapping).not.toBe(mapping);
    expect(component.editingGroupMappingPerms.find(permission => permission.value === 'permissionUpload')?.selected).toBe(true);
    expect(component.editingGroupMappingPerms.find(permission => permission.value === 'permissionDeleteBook')?.selected).toBe(false);
    expect(component.editingGroupMappingLibraryIds).toEqual([1, 2]);
  });

  it('toggles OIDC enablement and reports persistence failures', () => {
    component.oidcProvider = completeProvider();
    appSettingsService.toggleOidcEnabled.mockReturnValueOnce(of(void 0));
    component.oidcEnabled = true;

    component.toggleOidcEnabled();

    expect(appSettingsService.toggleOidcEnabled).toHaveBeenCalledWith(true);
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'success',
      detail: 'settingsAuth.toast.oidcUpdated',
    }));

    appSettingsService.toggleOidcEnabled.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({status: 500}))
    );
    component.oidcEnabled = false;

    component.toggleOidcEnabled();

    expect(messageService.add).toHaveBeenLastCalledWith(expect.objectContaining({
      severity: 'error',
      detail: 'settingsAuth.toast.oidcError',
    }));
  });

  it('copies the backchannel URI using the helper method', () => {
    const copySpy = vi.spyOn(component, 'copyToClipboard').mockImplementation(() => undefined);

    component.copyBackchannelUri();

    expect(copySpy).toHaveBeenCalledWith(component.backchannelLogoutUri);
  });

  it('saves OIDC provider settings with the session duration only when OIDC is enabled', () => {
    appSettingsService.saveSettings.mockReturnValue(of(void 0));
    component.oidcProvider = completeProvider();
    component.oidcEnabled = false;
    component.sessionDurationHours = 24;

    component.saveOidcProvider();

    expect(appSettingsService.saveSettings).toHaveBeenCalledWith([
      {
        key: AppSettingKey.OIDC_PROVIDER_DETAILS,
        newValue: component.oidcProvider,
      }
    ]);

    component.oidcEnabled = true;
    component.saveOidcProvider();

    expect(appSettingsService.saveSettings).toHaveBeenLastCalledWith([
      {
        key: AppSettingKey.OIDC_PROVIDER_DETAILS,
        newValue: component.oidcProvider,
      },
      {
        key: AppSettingKey.OIDC_SESSION_DURATION_HOURS,
        newValue: 24,
      }
    ]);
  });

  it('saves the group sync mode and reports failures', () => {
    appSettingsService.saveSettings.mockReturnValueOnce(of(void 0));
    component.groupSyncMode = 'ON_LOGIN_ADDITIVE';

    component.saveGroupSyncMode();

    expect(appSettingsService.saveSettings).toHaveBeenCalledWith([
      {
        key: AppSettingKey.OIDC_GROUP_SYNC_MODE,
        newValue: 'ON_LOGIN_ADDITIVE',
      }
    ]);
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'success',
      detail: 'settingsAuth.toast.syncModeSaved',
    }));

    appSettingsService.saveSettings.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({status: 500}))
    );

    component.saveGroupSyncMode();

    expect(messageService.add).toHaveBeenLastCalledWith(expect.objectContaining({
      severity: 'error',
      detail: 'settingsAuth.toast.syncModeError',
    }));
  });

  it('saves the auto-provisioning payload with the selected permissions and library ids', () => {
    appSettingsService.saveSettings.mockReturnValue(of(void 0));
    component.autoUserProvisioningEnabled = true;
    component.allowLocalAccountLinking = false;
    component.availablePermissions.find(p => p.value === 'permissionUpload')!.selected = true;
    component.availablePermissions.find(p => p.value === 'permissionDeleteBook')!.selected = true;
    component.editingLibraryIds = [1, 2];

    component.saveOidcAutoProvisionSettings();

    expect(appSettingsService.saveSettings).toHaveBeenCalledWith([
      {
        key: AppSettingKey.OIDC_AUTO_PROVISION_DETAILS,
        newValue: {
          enableAutoProvisioning: true,
          allowLocalAccountLinking: false,
          defaultPermissions: ['permissionRead', 'permissionUpload', 'permissionDeleteBook'],
          defaultLibraryIds: [1, 2],
        },
      }
    ]);
  });

  it('creates and updates group mappings, and ignores delete requests without an id', () => {
    const createdMapping = {id: 9, oidcGroupClaim: 'readers', isAdmin: false, permissions: ['permissionRead'], libraryIds: [1], description: 'Readers'};
    appSettingsService.saveSettings.mockReturnValue(of(void 0));
    groupMappingService.create.mockReturnValue(of(createdMapping));
    groupMappingService.update.mockReturnValue(of(createdMapping));
    groupMappingService.delete.mockReturnValue(of(void 0));
    groupMappingService.getAll.mockReturnValue(of([createdMapping]));
    component.editingGroupMapping = {oidcGroupClaim: 'readers', isAdmin: false, permissions: [], libraryIds: [], description: ''};
    component.editingGroupMappingPerms = component.availablePermissions.map(permission => ({
      ...permission,
      selected: permission.value === 'permissionUpload',
    }));
    component.editingGroupMappingLibraryIds = [1];

    component.saveGroupMapping();

    expect(groupMappingService.create).toHaveBeenCalledWith({
      oidcGroupClaim: 'readers',
      isAdmin: false,
      permissions: ['permissionRead', 'permissionUpload'],
      libraryIds: [1],
      description: '',
    });

    component.editingGroupMapping = createdMapping;
    component.editingGroupMappingPerms = component.availablePermissions.map(permission => ({
      ...permission,
      selected: false,
    }));
    component.editingGroupMappingLibraryIds = [2];
    component.saveGroupMapping();

    expect(groupMappingService.update).toHaveBeenCalledWith(9, {
      ...createdMapping,
      permissions: ['permissionRead'],
      libraryIds: [2],
    });

    component.deleteGroupMapping({oidcGroupClaim: 'no-id', isAdmin: false, permissions: [], libraryIds: [], description: ''});
    expect(groupMappingService.delete).not.toHaveBeenCalledWith(undefined);
  });

  it('surfaces failures when deleting a mapped group with an id', () => {
    const mapping = {id: 11, oidcGroupClaim: 'errors', isAdmin: false, permissions: [], libraryIds: [], description: ''};
    groupMappingService.delete.mockReturnValue(throwError(() => new HttpErrorResponse({status: 500})));

    component.deleteGroupMapping(mapping);

    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'error',
      detail: 'settingsAuth.toast.groupMappingError',
    }));
  });

  it('deletes mappings with an id and reloads after success', () => {
    const mapping = {id: 3, oidcGroupClaim: 'managers', isAdmin: false, permissions: [], libraryIds: [], description: ''};
    groupMappingService.delete.mockReturnValue(of(void 0));
    groupMappingService.getAll.mockReturnValue(of([]));

    component.deleteGroupMapping(mapping);

    expect(groupMappingService.delete).toHaveBeenCalledWith(3);
    expect(groupMappingService.getAll).toHaveBeenCalledOnce();
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
  });

  it('re-enables the force-only toggle when persistence succeeds', () => {
    appSettingsService.saveSettings.mockReturnValue(of(void 0));
    component.oidcForceOnlyMode = true;

    component.toggleOidcForceOnlyMode();

    expect(component.oidcForceOnlyMode).toBe(true);
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'success',
      detail: 'settingsAuth.oidcOnly.saved',
    }));
  });

  it('tests the OIDC connection and surfaces both success and failure states', () => {
    appSettingsService.testOidcConnection.mockReturnValueOnce(of({
      success: true,
      checks: [{name: 'issuer', status: 'PASS', message: 'ok'}],
    }));

    component.testConnection();

    expect(component.isTestingConnection).toBe(false);
    expect(component.testConnectionResult).toEqual({
      success: true,
      checks: [{name: 'issuer', status: 'PASS', message: 'ok'}],
    });
    expect(component.showTestDetails).toBe(true);

    appSettingsService.testOidcConnection.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({status: 500}))
    );

    component.testConnection();

    expect(component.isTestingConnection).toBe(false);
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'error',
      detail: 'settingsAuth.testConnection.error',
    }));
  });

  it('reverts the force-only toggle when persistence fails', () => {
    component.oidcForceOnlyMode = true;
    appSettingsService.saveSettings.mockReturnValue(
      throwError(() => ({error: {message: 'backend rejected'}}))
    );

    component.toggleOidcForceOnlyMode();

    expect(component.oidcForceOnlyMode).toBe(false);
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'error',
      detail: 'backend rejected',
    }));
  });

  it('returns library names when present and falls back to the numeric id when missing', () => {
    expect(component.getLibraryName(1)).toBe('Main Library');
    expect(component.getLibraryName(999)).toBe('#999');
  });
});
