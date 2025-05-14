import {Component, inject, OnInit} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {InputText} from 'primeng/inputtext';
import {Button} from 'primeng/button';
import {DropdownModule} from 'primeng/dropdown';
import {NgForOf, NgIf} from '@angular/common';
import {Checkbox} from 'primeng/checkbox';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {MessageService} from 'primeng/api';
import {AppSettingsService} from '../core/service/app-settings.service';
import {Observable} from 'rxjs';
import {AppSettings, OidcProviderDetails} from '../core/model/app-settings.model';
import {filter, take} from 'rxjs/operators';
import {Divider} from 'primeng/divider';
import {MultiSelect} from 'primeng/multiselect';
import {Library} from '../book/model/library.model';
import {LibraryService} from '../book/service/library.service';

@Component({
  selector: 'app-authentication-settings',
  templateUrl: './authentication-settings.component.html',
  standalone: true,
  imports: [
    FormsModule,
    InputText,
    DropdownModule,
    NgIf,
    NgForOf,
    Checkbox,
    ToggleSwitch,
    Divider,
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
    {label: 'Email Book', value: 'permissionEmailBook', selected: false}
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
    jwksUrl: '',
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

    this.libraryService.getAllLibrariesFromAPI().subscribe({
      next: (libraries) => {
        this.allLibraries = libraries;
      },
      error: () => {
        this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to load libraries'});
      },
    });
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
      jwksUrl: settings.oidcProviderDetails?.jwksUrl || '',
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

  saveAutoProvision(): void {
    const payload = [
      {key: 'auto_provision_oidc_users', newValue: this.autoUserProvisioningEnabled}
    ];
    this.appSettingsService.saveSettings(payload).subscribe({
      next: () => this.messageService.add({
        severity: 'success',
        summary: 'Saved',
        detail: 'Auto-provision setting saved.'
      }),
      error: () => this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to save auto-provision setting.'
      })
    });
  }

  onPermissionToggle(): void {
    this.selectedPermissions = [
      'permissionRead',
      ...this.availablePermissions.filter(p => p.selected).map(p => p.value)
    ];

    const payload = [
      {
        key: 'default_oidc_user_permissions',
        newValue: this.selectedPermissions
      }
    ];

    this.appSettingsService.saveSettings(payload).subscribe({
      next: () => this.messageService.add({
        severity: 'success',
        summary: 'Saved',
        detail: 'Default permissions updated.'
      }),
      error: () => this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to update default permissions.'
      })
    });
  }

  toggleOidcEnabled(): void {
    if (!this.isOidcFormComplete()) return;
    const payload = [
      {
        key: 'oidc_enabled',
        newValue: this.oidcEnabled
      }
    ];
    this.appSettingsService.saveSettings(payload).subscribe({
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
        key: 'oidc_provider_details',
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


  saveAutoProvisioningSettings(): void {
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
        key: 'oidc_auto_provision_details',
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
