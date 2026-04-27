import {Component, effect, inject} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {InputText} from 'primeng/inputtext';
import {Button} from 'primeng/button';
import {Checkbox} from 'primeng/checkbox';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {InputNumber} from 'primeng/inputnumber';
import {MessageService} from 'primeng/api';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {AppSettingKey, AppSettings, OidcProviderDetails, OidcTestResult} from '../../../shared/model/app-settings.model';
import {MultiSelect} from 'primeng/multiselect';
import {LibraryService} from '../../../features/book/service/library.service';
import {ExternalDocLinkComponent} from '../../../shared/components/external-doc-link/external-doc-link.component';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {OidcGroupMapping} from '../../../shared/model/oidc-group-mapping.model';
import {OidcGroupMappingService} from '../../../shared/service/oidc-group-mapping.service';
import {Select} from 'primeng/select';
import {TableModule} from 'primeng/table';
import {Dialog} from 'primeng/dialog';
import {TagComponent} from '../../../shared/components/tag/tag.component';
import {API_CONFIG} from '../../config/api-config';

@Component({
  selector: 'app-authentication-settings',
  templateUrl: './authentication-settings.component.html',
  standalone: true,
  imports: [
    FormsModule,
    InputText,
    Checkbox,
    ToggleSwitch,
    Button,
    MultiSelect,
    InputNumber,
    ReactiveFormsModule,
    ExternalDocLinkComponent,
    TranslocoDirective,
    TranslocoPipe,
    Select,
    TableModule,
    Dialog,
    TagComponent
  ],
  styleUrls: ['./authentication-settings.component.scss']
})
export class AuthenticationSettingsComponent {
  availablePermissions = [
    {label: 'Upload Books', value: 'permissionUpload', selected: false, translationKey: 'perms.uploadBooks'},
    {label: 'Download Books', value: 'permissionDownload', selected: false, translationKey: 'perms.downloadBooks'},
    {label: 'Delete Books', value: 'permissionDeleteBook', selected: false, translationKey: 'perms.deleteBooks'},
    {label: 'Manage Library', value: 'permissionManageLibrary', selected: false, translationKey: 'perms.manageLibrary'},
    {label: 'Email Books', value: 'permissionEmailBook', selected: false, translationKey: 'perms.emailBooks'},
    {label: 'Access Bookdrop', value: 'permissionAccessBookdrop', selected: false, translationKey: 'perms.accessBookdrop'},
    {label: 'View Library Statistics', value: 'permissionAccessLibraryStats', selected: false, translationKey: 'perms.viewLibraryStats'},
    {label: 'View User Reading Statistics', value: 'permissionAccessUserStats', selected: false, translationKey: 'perms.viewUserStats'},
    {label: 'Manage Metadata Configuration', value: 'permissionManageMetadataConfig', selected: false, translationKey: 'perms.manageMetadataConfig'},
    {label: 'Manage Application Preferences', value: 'permissionManageGlobalPreferences', selected: false, translationKey: 'perms.manageAppPreferences'},
    {label: 'Access Task Manager', value: 'permissionAccessTaskManager', selected: false, translationKey: 'perms.accessTaskManager'},
    {label: 'Manage Icons', value: 'permissionManageIcons', selected: false, translationKey: 'perms.manageIcons'},
    {label: 'Manage Fonts', value: 'permissionManageFonts', selected: false, translationKey: 'perms.manageFonts'},
    {label: 'Edit Single Metadata', value: 'permissionEditMetadata', selected: false, translationKey: 'perms.editSingleMetadata'},
    {label: 'Bulk Auto Fetch Metadata', value: 'permissionBulkAutoFetchMetadata', selected: false, translationKey: 'perms.bulkAutoFetch'},
    {label: 'Bulk Custom Fetch Metadata', value: 'permissionBulkCustomFetchMetadata', selected: false, translationKey: 'perms.bulkCustomFetch'},
    {label: 'Access Bulk Metadata Editor', value: 'permissionBulkEditMetadata', selected: false, translationKey: 'perms.accessBulkEditor'},
    {label: 'Bulk Regenerate Cover', value: 'permissionBulkRegenerateCover', selected: false, translationKey: 'perms.bulkRegenerateCover'},
    {label: 'Move/Organize Files', value: 'permissionMoveOrganizeFiles', selected: false, translationKey: 'perms.moveOrganize'},
    {label: 'Bulk Lock/Unlock Metadata', value: 'permissionBulkLockUnlockMetadata', selected: false, translationKey: 'perms.bulkLockUnlock'},
    {label: 'KOReader Sync', value: 'permissionSyncKoreader', selected: false, translationKey: 'perms.koreaderSync'},
    {label: 'Kobo Sync', value: 'permissionSyncKobo', selected: false, translationKey: 'perms.koboSync'},
    {label: 'OPDS Feed Access', value: 'permissionAccessOpds', selected: false, translationKey: 'perms.opdsFeedAccess'},
    {label: 'Bulk Reset Grimmory Read Progress', value: 'permissionBulkResetBookloreReadProgress', selected: false, translationKey: 'perms.bulkResetGrimmory'},
    {label: 'Bulk Reset KoReader Read Progress', value: 'permissionBulkResetKoReaderReadProgress', selected: false, translationKey: 'perms.bulkResetKoreader'},
    {label: 'Bulk Reset Book Read Status', value: 'permissionBulkResetBookReadStatus', selected: false, translationKey: 'perms.bulkResetReadStatus'},
  ];

  internalAuthEnabled = true;
  autoUserProvisioningEnabled = false;
  allowLocalAccountLinking = true;
  selectedPermissions: string[] = [];
  oidcEnabled = false;

  editingLibraryIds: number[] = [];
  sessionDurationHours: number | null = null;
  backchannelLogoutUri = `${API_CONFIG.BASE_URL}/api/v1/auth/oidc/backchannel-logout`;
  oidcForceOnlyMode = false;

  infoItems = [
    {labelKey: 'infoPanel.redirectUri', value: `${API_CONFIG.BASE_URL}/oauth2-callback`},
    {labelKey: 'infoPanel.postLogoutRedirectUri', value: `${API_CONFIG.BASE_URL}/login`},
    {labelKey: 'infoPanel.backChannelLogoutUri', value: `${API_CONFIG.BASE_URL}/api/v1/auth/oidc/backchannel-logout`},
    {labelKey: 'infoPanel.requiredScopes', value: 'openid profile email groups offline_access'},
    {labelKey: 'infoPanel.pkceMethod', value: 'S256'},
    {labelKey: 'infoPanel.grantType', value: 'Authorization Code'},
  ];

  // Test connection
  isTestingConnection = false;
  testConnectionResult: OidcTestResult | null = null;
  showTestDetails = false;

  // Group mapping
  groupSyncMode: string = 'DISABLED';
  groupSyncModeOptions = [
    {label: 'Disabled', value: 'DISABLED'},
    {label: 'On Login (Replace)', value: 'ON_LOGIN'},
    {label: 'On Login (Additive)', value: 'ON_LOGIN_ADDITIVE'}
  ];
  groupMappings: OidcGroupMapping[] = [];
  showGroupMappingDialog = false;
  editingGroupMapping: OidcGroupMapping = this.emptyGroupMapping();
  editingGroupMappingPerms: {label: string; value: string; selected: boolean; translationKey: string}[] = [];
  editingGroupMappingLibraryIds: number[] = [];

  oidcProvider: OidcProviderDetails = {
    providerName: '',
    clientId: '',
    clientSecret: '',
    issuerUri: '',
    scopes: '',
    claimMapping: {
      username: '',
      email: '',
      name: '',
      groups: ''
    }
  };

  private appSettingsService = inject(AppSettingsService);
  private messageService = inject(MessageService);
  private libraryService = inject(LibraryService);
  private groupMappingService = inject(OidcGroupMappingService);
  private t = inject(TranslocoService);
  get allLibraries() { return this.libraryService.libraries(); }

  private readonly loadSettingsEffect = effect(() => {
    const settings = this.appSettingsService.appSettings();
    if (settings) {
      this.loadSettings(settings);
    }
  });

  loadSettings(settings: AppSettings): void {
    this.oidcEnabled = settings.oidcEnabled;

    const details = settings.oidcAutoProvisionDetails;

    this.autoUserProvisioningEnabled = details?.enableAutoProvisioning ?? false;
    this.allowLocalAccountLinking = details?.allowLocalAccountLinking ?? true;
    this.selectedPermissions = details?.defaultPermissions ?? [];
    this.editingLibraryIds = details?.defaultLibraryIds ?? [];

    const defaultClaimMapping = {
      username: 'preferred_username',
      email: 'email',
      name: 'given_name',
      groups: ''
    };

    this.sessionDurationHours = settings.oidcSessionDurationHours ?? null;
    this.groupSyncMode = settings.oidcGroupSyncMode ?? 'DISABLED';
    this.oidcForceOnlyMode = settings.oidcForceOnlyMode ?? false;

    this.oidcProvider = {
      providerName: settings.oidcProviderDetails?.providerName || '',
      clientId: settings.oidcProviderDetails?.clientId || '',
      issuerUri: settings.oidcProviderDetails?.issuerUri || '',
      scopes: settings.oidcProviderDetails?.scopes || '',
      claimMapping: settings.oidcProviderDetails?.claimMapping || defaultClaimMapping
    };

    this.availablePermissions.forEach(perm => {
      perm.selected = this.selectedPermissions.includes(perm.value);
    });

    if (this.oidcEnabled) {
      this.loadGroupMappings();
    }
  }

  isOidcFormComplete(): boolean {
    const p = this.oidcProvider;
    return !!(p.providerName && p.clientId && p.issuerUri && p.claimMapping.name && p.claimMapping.email && p.claimMapping.username);
  }

  toggleOidcEnabled(): void {
    if (!this.isOidcFormComplete()) return;
    this.appSettingsService.toggleOidcEnabled(this.oidcEnabled).subscribe({
      next: () => this.messageService.add({
        severity: 'success',
        summary: this.t.translate('settingsAuth.toast.saved'),
        detail: this.t.translate('settingsAuth.toast.oidcUpdated')
      }),
      error: () => this.messageService.add({
        severity: 'error',
        summary: this.t.translate('common.error'),
        detail: this.t.translate('settingsAuth.toast.oidcError')
      })
    });
  }

  saveOidcProvider(): void {
    const payload: {key: AppSettingKey; newValue: unknown}[] = [
      {
        key: AppSettingKey.OIDC_PROVIDER_DETAILS,
        newValue: this.oidcProvider
      }
    ];
    if (this.oidcEnabled) {
      payload.push({
        key: AppSettingKey.OIDC_SESSION_DURATION_HOURS,
        newValue: this.sessionDurationHours
      });
    }
    this.appSettingsService.saveSettings(payload).subscribe({
      next: () => this.messageService.add({
        severity: 'success',
        summary: this.t.translate('settingsAuth.toast.saved'),
        detail: this.t.translate('settingsAuth.toast.providerSaved')
      }),
      error: () => this.messageService.add({
        severity: 'error',
        summary: this.t.translate('common.error'),
        detail: this.t.translate('settingsAuth.toast.providerError')
      })
    });
  }

  copyBackchannelUri(): void {
    this.copyToClipboard(this.backchannelLogoutUri);
  }

  copyToClipboard(value: string): void {
    navigator.clipboard.writeText(value).then(() => {
      this.messageService.add({
        severity: 'success',
        summary: this.t.translate('settingsAuth.toast.saved'),
        detail: this.t.translate('settingsAuth.toast.copiedToClipboard')
      });
    });
  }

  saveSessionDuration(): void {
    const payload = [
      {
        key: AppSettingKey.OIDC_SESSION_DURATION_HOURS,
        newValue: this.sessionDurationHours
      }
    ];
    this.appSettingsService.saveSettings(payload).subscribe({
      next: () => this.messageService.add({
        severity: 'success',
        summary: this.t.translate('settingsAuth.toast.saved'),
        detail: this.t.translate('settingsAuth.toast.sessionDurationSaved')
      }),
      error: () => this.messageService.add({
        severity: 'error',
        summary: this.t.translate('common.error'),
        detail: this.t.translate('settingsAuth.toast.sessionDurationError')
      })
    });
  }

  saveOidcAutoProvisionSettings(): void {
    const provisionDetails = {
      enableAutoProvisioning: this.autoUserProvisioningEnabled,
      allowLocalAccountLinking: this.allowLocalAccountLinking,
      defaultPermissions: [
        'permissionRead',
        ...this.availablePermissions.filter(p => p.selected).map(p => p.value)
      ],
      defaultLibraryIds: this.editingLibraryIds
    };

    const payload = [
      {
        key: AppSettingKey.OIDC_AUTO_PROVISION_DETAILS,
        newValue: provisionDetails
      }
    ];

    this.appSettingsService.saveSettings(payload).subscribe({
      next: () => this.messageService.add({
        severity: 'success',
        summary: this.t.translate('settingsAuth.toast.saved'),
        detail: this.t.translate('settingsAuth.toast.provisionSaved')
      }),
      error: () => this.messageService.add({
        severity: 'error',
        summary: this.t.translate('common.error'),
        detail: this.t.translate('settingsAuth.toast.provisionError')
      })
    });
  }

  // Group mapping methods
  loadGroupMappings(): void {
    this.groupMappingService.getAll().subscribe(mappings => this.groupMappings = mappings);
  }

  saveGroupSyncMode(): void {
    const payload = [
      {
        key: AppSettingKey.OIDC_GROUP_SYNC_MODE,
        newValue: this.groupSyncMode
      }
    ];
    this.appSettingsService.saveSettings(payload).subscribe({
      next: () => this.messageService.add({
        severity: 'success',
        summary: this.t.translate('settingsAuth.toast.saved'),
        detail: this.t.translate('settingsAuth.toast.syncModeSaved')
      }),
      error: () => this.messageService.add({
        severity: 'error',
        summary: this.t.translate('common.error'),
        detail: this.t.translate('settingsAuth.toast.syncModeError')
      })
    });
  }

  openNewGroupMapping(): void {
    this.editingGroupMapping = this.emptyGroupMapping();
    this.initGroupMappingPerms([]);
    this.editingGroupMappingLibraryIds = [];
    this.showGroupMappingDialog = true;
  }

  openEditGroupMapping(mapping: OidcGroupMapping): void {
    this.editingGroupMapping = {...mapping};
    this.initGroupMappingPerms(mapping.permissions);
    this.editingGroupMappingLibraryIds = [...mapping.libraryIds];
    this.showGroupMappingDialog = true;
  }

  private initGroupMappingPerms(selectedPerms: string[]): void {
    this.editingGroupMappingPerms = this.availablePermissions.map(p => ({
      ...p,
      selected: selectedPerms.includes(p.value)
    }));
  }

  saveGroupMapping(): void {
    const mapping: OidcGroupMapping = {
      ...this.editingGroupMapping,
      permissions: [
        'permissionRead',
        ...this.editingGroupMappingPerms.filter(p => p.selected).map(p => p.value)
      ],
      libraryIds: this.editingGroupMappingLibraryIds
    };

    const obs = mapping.id
      ? this.groupMappingService.update(mapping.id, mapping)
      : this.groupMappingService.create(mapping);

    obs.subscribe({
      next: () => {
        this.showGroupMappingDialog = false;
        this.loadGroupMappings();
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('settingsAuth.toast.saved'),
          detail: this.t.translate('settingsAuth.toast.groupMappingSaved')
        });
      },
      error: () => this.messageService.add({
        severity: 'error',
        summary: this.t.translate('common.error'),
        detail: this.t.translate('settingsAuth.toast.groupMappingError')
      })
    });
  }

  deleteGroupMapping(mapping: OidcGroupMapping): void {
    if (!mapping.id) return;
    this.groupMappingService.delete(mapping.id).subscribe({
      next: () => {
        this.loadGroupMappings();
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('settingsAuth.toast.saved'),
          detail: this.t.translate('settingsAuth.toast.groupMappingDeleted')
        });
      },
      error: () => this.messageService.add({
        severity: 'error',
        summary: this.t.translate('common.error'),
        detail: this.t.translate('settingsAuth.toast.groupMappingError')
      })
    });
  }

  getLibraryName(id: number): string {
    return this.allLibraries.find(l => l.id === id)?.name ?? `#${id}`;
  }

  testConnection(): void {
    this.isTestingConnection = true;
    this.testConnectionResult = null;
    this.appSettingsService.testOidcConnection(this.oidcProvider).subscribe({
      next: (result) => {
        this.testConnectionResult = result;
        this.showTestDetails = true;
        this.isTestingConnection = false;
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: this.t.translate('settingsAuth.testConnection.error')
        });
        this.isTestingConnection = false;
      }
    });
  }

  toggleOidcForceOnlyMode(): void {
    const payload = [
      {
        key: AppSettingKey.OIDC_FORCE_ONLY_MODE,
        newValue: this.oidcForceOnlyMode
      }
    ];
    this.appSettingsService.saveSettings(payload).subscribe({
      next: () => this.messageService.add({
        severity: 'success',
        summary: this.t.translate('settingsAuth.toast.saved'),
        detail: this.t.translate('settingsAuth.oidcOnly.saved')
      }),
      error: (err) => {
        this.oidcForceOnlyMode = !this.oidcForceOnlyMode;
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: err?.error?.message || this.t.translate('settingsAuth.oidcOnly.error')
        });
      }
    });
  }

  onAdminCheckboxChange(): void {
    if (this.editingGroupMapping.isAdmin) {
      this.editingGroupMappingPerms = this.availablePermissions.map(p => ({
        ...p,
        selected: true,
      }));
    }
  }

  private emptyGroupMapping(): OidcGroupMapping {
    return {oidcGroupClaim: '', isAdmin: false, permissions: [], libraryIds: [], description: ''};
  }
}
