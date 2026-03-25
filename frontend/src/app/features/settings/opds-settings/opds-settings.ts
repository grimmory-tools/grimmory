import {Component, effect, inject, OnDestroy, OnInit} from '@angular/core';

import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {API_CONFIG} from '../../../core/config/api-config';
import {Tooltip} from 'primeng/tooltip';
import {TableModule} from 'primeng/table';
import {Dialog} from 'primeng/dialog';
import {FormsModule} from '@angular/forms';
import {ConfirmDialog} from 'primeng/confirmdialog';
import {ConfirmationService, MessageService} from 'primeng/api';
import {OpdsService, OpdsSortOrder, OpdsUserV2, OpdsUserV2CreateRequest} from './opds.service';
import {catchError, takeUntil} from 'rxjs/operators';
import {UserService} from '../user-management/user.service';
import {of, Subject} from 'rxjs';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {AppSettingKey} from '../../../shared/model/app-settings.model';
import {ExternalDocLinkComponent} from '../../../shared/components/external-doc-link/external-doc-link.component';
import {Select} from 'primeng/select';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-opds-settings',
  imports: [
    Button,
    InputText,
    Tooltip,
    Dialog,
    FormsModule,
    ConfirmDialog,
    TableModule,
    ToggleSwitch,
    ExternalDocLinkComponent,
    Select,
    TranslocoDirective,
    TranslocoPipe
  ],
  providers: [ConfirmationService],
  templateUrl: './opds-settings.html',
  styleUrl: './opds-settings.scss'
})
export class OpdsSettings implements OnInit, OnDestroy {

  opdsEndpoint = `${API_CONFIG.BASE_URL}/api/v1/opds`;
  komgaEndpoint = `${API_CONFIG.BASE_URL}/komga`;
  opdsEnabled = false;
  komgaApiEnabled = false;
  komgaGroupUnknown = true;

  private opdsService = inject(OpdsService);
  private confirmationService = inject(ConfirmationService);
  private messageService = inject(MessageService);
  private userService = inject(UserService);
  private appSettingsService = inject(AppSettingsService);
  private t = inject(TranslocoService);

  users: OpdsUserV2[] = [];
  loading = false;
  showCreateUserDialog = false;
  newUser: OpdsUserV2CreateRequest = {username: '', password: '', sortOrder: 'RECENT'};
  passwordVisibility: boolean[] = [];
  hasPermission = false;

  editingUserId: number | null = null;
  editingSortOrder: OpdsSortOrder | null = null;

  private readonly destroy$ = new Subject<void>();
  dummyPassword: string = "***********************";

  sortOrderOptions = [
    {label: 'Recently Added', value: 'RECENT' as OpdsSortOrder, translationKey: 'settingsOpds.sortOrders.recent'},
    {label: 'Title (A-Z)', value: 'TITLE_ASC' as OpdsSortOrder, translationKey: 'settingsOpds.sortOrders.titleAsc'},
    {label: 'Title (Z-A)', value: 'TITLE_DESC' as OpdsSortOrder, translationKey: 'settingsOpds.sortOrders.titleDesc'},
    {label: 'Author (A-Z)', value: 'AUTHOR_ASC' as OpdsSortOrder, translationKey: 'settingsOpds.sortOrders.authorAsc'},
    {label: 'Author (Z-A)', value: 'AUTHOR_DESC' as OpdsSortOrder, translationKey: 'settingsOpds.sortOrders.authorDesc'},
    {label: 'Series (A-Z)', value: 'SERIES_ASC' as OpdsSortOrder, translationKey: 'settingsOpds.sortOrders.seriesAsc'},
    {label: 'Series (Z-A)', value: 'SERIES_DESC' as OpdsSortOrder, translationKey: 'settingsOpds.sortOrders.seriesDesc'},
    {label: 'Rating (Low to High)', value: 'RATING_ASC' as OpdsSortOrder, translationKey: 'settingsOpds.sortOrders.ratingAsc'},
    {label: 'Rating (High to Low)', value: 'RATING_DESC' as OpdsSortOrder, translationKey: 'settingsOpds.sortOrders.ratingDesc'}
  ];

  private prevHasPermission = false;

  private readonly syncPermissionEffect = effect(() => {
    const user = this.userService.currentUser();
    if (!user) return;

    this.hasPermission = !!(user.permissions.canAccessOpds || user.permissions.admin);
    if (this.hasPermission && !this.prevHasPermission) {
      this.loadAppSettings();
    }
    this.prevHasPermission = this.hasPermission;
  });

  ngOnInit(): void {
    this.loading = true;
  }

  private loadAppSettings(): void {
    const settings = this.appSettingsService.appSettings();
    if (settings) {
      this.opdsEnabled = settings.opdsServerEnabled ?? false;
      this.komgaApiEnabled = settings.komgaApiEnabled ?? false;
      this.komgaGroupUnknown = settings.komgaGroupUnknown ?? true;
      if (this.opdsEnabled || this.komgaApiEnabled) {
        this.loadUsers();
      } else {
        this.loading = false;
      }
    }
  }

  private loadUsers(): void {
    this.opdsService.getUser().pipe(
      takeUntil(this.destroy$),
      catchError(err => {
        console.error('Error loading users:', err);
        this.showMessage('error', this.t.translate('common.error'), this.t.translate('settingsOpds.loadError'));
        return of([]);
      })
    ).subscribe(users => {
      this.users = users;
      this.passwordVisibility = new Array(users.length).fill(false);
      this.loading = false;
    });
  }

  createUser(): void {
    if (!this.newUser.username || !this.newUser.password) return;

    this.opdsService.createUser(this.newUser).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: user => {
        this.users.push(user);
        this.resetCreateUserDialog();
        this.showMessage('success', this.t.translate('common.success'), this.t.translate('settingsOpds.createSuccess'));
      },
      error: err => {
        console.error('Error creating user:', err);
        const message = err?.error?.message || this.t.translate('settingsOpds.createError');
        this.showMessage('error', this.t.translate('common.error'), message);
      }
    });
  }

  confirmDelete(user: OpdsUserV2): void {
    this.confirmationService.confirm({
      message: this.t.translate('settingsOpds.deleteConfirm', {username: user.username}),
      header: this.t.translate('settingsOpds.deleteHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.deleteUser(user)
    });
  }

  deleteUser(user: OpdsUserV2): void {
    if (!user.id) return;

    this.opdsService.deleteCredential(user.id).pipe(
      takeUntil(this.destroy$),
      catchError(err => {
        console.error('Error deleting user:', err);
        this.showMessage('error', this.t.translate('common.error'), this.t.translate('settingsOpds.deleteError'));
        return of(null);
      })
    ).subscribe(() => {
      this.users = this.users.filter(u => u.id !== user.id);
      this.showMessage('success', this.t.translate('common.success'), this.t.translate('settingsOpds.deleteSuccess'));
    });
  }

  cancelCreateUser(): void {
    this.resetCreateUserDialog();
  }

  copyEndpoint(): void {
    navigator.clipboard.writeText(this.opdsEndpoint).then(() => {
      this.showMessage('success', this.t.translate('common.success'), this.t.translate('settingsOpds.opdsCopied'));
    });
  }

  toggleOpdsServer(): void {
    this.saveSetting(AppSettingKey.OPDS_SERVER_ENABLED, this.opdsEnabled);
    if (this.opdsEnabled || this.komgaApiEnabled) {
      this.loadUsers();
    } else {
      this.users = [];
    }
  }

  toggleKomgaApi(): void {
    this.saveKomgaSetting(AppSettingKey.KOMGA_API_ENABLED, this.komgaApiEnabled);
    if (this.opdsEnabled || this.komgaApiEnabled) {
      this.loadUsers();
    } else {
      this.users = [];
    }
  }

  copyKomgaEndpoint(): void {
    navigator.clipboard.writeText(this.komgaEndpoint).then(() => {
      this.showMessage('success', this.t.translate('common.success'), this.t.translate('settingsOpds.komgaCopied'));
    });
  }

  toggleKomgaGroupUnknown(): void {
    this.appSettingsService.saveSettings([{key: AppSettingKey.KOMGA_GROUP_UNKNOWN, newValue: this.komgaGroupUnknown}]).subscribe({
      next: () => {
        const successMessage = (this.komgaGroupUnknown === true)
          ? this.t.translate('settingsOpds.groupEnabled')
          : this.t.translate('settingsOpds.groupDisabled');
        this.showMessage('success', this.t.translate('settingsOpds.settingsSaved'), successMessage);
      },
      error: () => {
        this.showMessage('error', this.t.translate('common.error'), this.t.translate('settingsOpds.settingsError'));
      }
    });
  }

  private saveSetting(key: string, value: unknown): void {
    this.appSettingsService.saveSettings([{key, newValue: value}]).subscribe({
      next: () => {
        const successMessage = (value === true)
          ? this.t.translate('settingsOpds.opdsEnabled')
          : this.t.translate('settingsOpds.opdsDisabled');
        this.showMessage('success', this.t.translate('settingsOpds.settingsSaved'), successMessage);
      },
      error: () => {
        this.showMessage('error', this.t.translate('common.error'), this.t.translate('settingsOpds.settingsError'));
      }
    });
  }

  private saveKomgaSetting(key: string, value: unknown): void {
    this.appSettingsService.saveSettings([{key, newValue: value}]).subscribe({
      next: () => {
        const successMessage = (value === true)
          ? this.t.translate('settingsOpds.komgaEnabled')
          : this.t.translate('settingsOpds.komgaDisabled');
        this.showMessage('success', this.t.translate('settingsOpds.settingsSaved'), successMessage);
      },
      error: () => {
        this.showMessage('error', this.t.translate('common.error'), this.t.translate('settingsOpds.settingsError'));
      }
    });
  }

  private resetCreateUserDialog(): void {
    this.showCreateUserDialog = false;
    this.newUser = {username: '', password: '', sortOrder: 'RECENT'};
  }

  private showMessage(severity: string, summary: string, detail: string): void {
    this.messageService.add({severity, summary, detail});
  }

  getSortOrderLabel(sortOrder?: OpdsSortOrder): string {
    if (!sortOrder) return this.t.translate('settingsOpds.sortOrders.recent');
    const option = this.sortOrderOptions.find(o => o.value === sortOrder);
    return option ? this.t.translate(option.translationKey) : this.t.translate('settingsOpds.sortOrders.recent');
  }

  startEdit(user: OpdsUserV2): void {
    this.editingUserId = user.id;
    this.editingSortOrder = user.sortOrder || 'RECENT';
  }

  cancelEdit(): void {
    this.editingUserId = null;
    this.editingSortOrder = null;
  }

  saveSortOrder(user: OpdsUserV2): void {
    if (!this.editingSortOrder || !user.id) return;

    this.opdsService.updateUser(user.id, this.editingSortOrder).pipe(
      takeUntil(this.destroy$),
      catchError(err => {
        console.error('Error updating sort order:', err);
        this.showMessage('error', this.t.translate('common.error'), this.t.translate('settingsOpds.sortUpdateError'));
        return of(null);
      })
    ).subscribe(updatedUser => {
      if (updatedUser) {
        const index = this.users.findIndex(u => u.id === user.id);
        if (index !== -1) {
          this.users[index] = updatedUser;
        }
        this.showMessage('success', this.t.translate('common.success'), this.t.translate('settingsOpds.sortUpdateSuccess'));
      }
      this.cancelEdit();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
