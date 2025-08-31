import {Component, inject, OnInit} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {InputText} from 'primeng/inputtext';
import {Button} from 'primeng/button';

import {Checkbox} from 'primeng/checkbox';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {MessageService} from 'primeng/api';
import {AppSettingsService} from '../../service/app-settings.service';
import {Observable} from 'rxjs';
import {AppSettingKey, AppSettings, OidcProviderDetails} from '../../model/app-settings.model';
import {filter, take} from 'rxjs/operators';
import {MultiSelect} from 'primeng/multiselect';
import {Library} from '../../../book/model/library.model';
import {LibraryService} from '../../../book/service/library.service';

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
    ReactiveFormsModule
  ],
  styleUrls: ['./authentication-settings.component.scss']
})
export class AuthenticationSettingsComponent implements OnInit {
  availablePermissions = [
    {label: 'Upload Books', value: 'permissionUpload', selected: false},
    {label: 'Download Books', value: 'permissionDownload', selected: false},
    {label: 'Edit Book Metadata', value: 'permissionEditMetadata', selected: false},
    {label: 'Manage Library', value: 'permissionManipulateLibrary', selected: false},
    {label: 'Email Book', value: 'permissionEmailBook', selected: false},
    {label: 'Delete Book', value: 'permissionDeleteBook', selected: false},
    {label: 'KOReader Sync', value: 'permissionSyncKoreader', selected: false},
    {label: 'Kobo Sync', value: 'permissionSyncKobo', selected: false},
    {label: 'Access OPDS', value: 'permissionAccessOpds', selected: false}
  ];

  internalAuthEnabled = true;
  autoUserProvisioningEnabled = false;
  selectedPermissions: string[] = [];
  oidcEnabled = false;
  allLibraries: Library[] = [];
  editingLibraryIds: number[] = [];

  oidcProvider: OidcProviderDetails = {
    providerName: '',
    clientId: '',
    issuerUri: '',
    claimMapping: {
      username: '',
      email: '',
      name: ''
    }
  };

  private appSettingsService = inject(AppSettingsService);
  private messageService = inject(MessageService);
  private libraryService = inject(LibraryService);

  appSettings$: Observable<AppSettings | null> = this.appSettingsService.appSettings$;

  ngOnInit(): void {
    this.appSettings$.pipe(
      filter((settings): settings is AppSettings => settings != null),
      take(1)
    ).subscribe(settings => this.loadSettings(settings));

    this.libraryService.libraryState$
      .pipe(
        filter(state => !!state?.loaded),
        take(1)
      ).subscribe(state => this.allLibraries = state.libraries ?? []);
  }

  loadSettings(settings: AppSettings): void {
    this.oidcEnabled = settings.oidcEnabled;

    const details = settings.oidcAutoProvisionDetails;

    this.autoUserProvisioningEnabled = details?.enableAutoProvisioning ?? false;
    this.selectedPermissions = details?.defaultPermissions ?? [];
    this.editingLibraryIds = details?.defaultLibraryIds ?? [];

    const defaultClaimMapping = {
      username: 'preferred_username',
      email: 'email',
      name: 'given_name'
    };

    this.oidcProvider = {
      providerName: settings.oidcProviderDetails?.providerName || '',
      clientId: settings.oidcProviderDetails?.clientId || '',
      issuerUri: settings.oidcProviderDetails?.issuerUri || '',
      claimMapping: settings.oidcProviderDetails?.claimMapping || defaultClaimMapping
    };

    this.availablePermissions.forEach(perm => {
      perm.selected = this.selectedPermissions.includes(perm.value);
    });
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
        summary: 'Saved',
        detail: 'OIDC setting updated.'
      }),
      error: () => this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to update OIDC setting.'
      })
    });
  }

  saveOidcProvider(): void {
    const payload = [
      {
        key: AppSettingKey.OIDC_PROVIDER_DETAILS,
        newValue: this.oidcProvider
      }
    ];
    this.appSettingsService.saveSettings(payload).subscribe({
      next: () => this.messageService.add({
        severity: 'success',
        summary: 'Saved',
        detail: 'OIDC provider settings saved.'
      }),
      error: () => this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to save OIDC provider settings.'
      })
    });
  }

  saveOidcAutoProvisionSettings(): void {
    const provisionDetails = {
      enableAutoProvisioning: this.autoUserProvisioningEnabled,
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
        summary: 'Saved',
        detail: 'OIDC auto-provisioning settings saved.'
      }),
      error: () => this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to save OIDC auto-provisioning settings.'
      })
    });
  }
}
