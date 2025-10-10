import {Component, inject, OnInit} from '@angular/core';
import {Button} from 'primeng/button';
import {Checkbox} from 'primeng/checkbox';

import {MessageService, PrimeTemplate} from 'primeng/api';
import {RadioButton} from 'primeng/radiobutton';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {TableModule} from 'primeng/table';
import {Tooltip} from 'primeng/tooltip';
import {EmailProvider} from './email-provider.model';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {EmailProviderService} from './email-provider.service';
import {CreateEmailProviderDialogComponent} from '../create-email-provider-dialog/create-email-provider-dialog.component';
import {ExternalDocLinkComponent} from '../../../shared/components/external-doc-link/external-doc-link.component';

@Component({
  selector: 'app-email-provider',
  imports: [
    Button,
    Checkbox,
    PrimeTemplate,
    RadioButton,
    ReactiveFormsModule,
    TableModule,
    Tooltip,
    FormsModule,
    ExternalDocLinkComponent
  ],
  templateUrl: './email-provider.component.html',
  styleUrl: './email-provider.component.scss'
})
export class EmailProviderComponent implements OnInit {
  emailProviders: EmailProvider[] = [];
  editingProviderIds: number[] = [];
  ref: DynamicDialogRef | undefined;
  private dialogService = inject(DialogService);
  private emailProvidersService = inject(EmailProviderService);
  private messageService = inject(MessageService);
  defaultProviderId: any;

  ngOnInit(): void {
    this.loadEmailProviders();
  }

  loadEmailProviders(): void {
    this.emailProvidersService.getEmailProviders().subscribe({
      next: (emailProviders: EmailProvider[]) => {
        this.emailProviders = emailProviders.map((provider) => ({
          ...provider,
          isEditing: false,
        }));
        const defaultProvider = emailProviders.find((provider) => provider.defaultProvider);
        this.defaultProviderId = defaultProvider ? defaultProvider.id : null;
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to load Email Providers',
        });
      },
    });
  }

  toggleEdit(provider: EmailProvider): void {
    provider.isEditing = !provider.isEditing;
    if (provider.isEditing) {
      this.editingProviderIds.push(provider.id);
    } else {
      this.editingProviderIds = this.editingProviderIds.filter((id) => id !== provider.id);
    }
  }

  saveProvider(provider: EmailProvider): void {
    this.emailProvidersService.updateProvider(provider).subscribe({
      next: () => {
        provider.isEditing = false;
        this.messageService.add({
          severity: 'success',
          summary: 'Success',
          detail: 'Provider updated successfully',
        });
        this.loadEmailProviders();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to update provider',
        });
      },
    });
  }

  deleteProvider(provider: EmailProvider): void {
    if (confirm(`Are you sure you want to delete provider "${provider.name}"?`)) {
      this.emailProvidersService.deleteProvider(provider.id).subscribe({
        next: () => {
          this.messageService.add({
            severity: 'success',
            summary: 'Success',
            detail: `Provider "${provider.name}" deleted successfully`,
          });
          this.loadEmailProviders();
        },
        error: () => {
          this.messageService.add({
            severity: 'error',
            summary: 'Error',
            detail: 'Failed to delete provider',
          });
        },
      });
    }
  }

  openCreateProviderDialog() {
    this.ref = this.dialogService.open(CreateEmailProviderDialogComponent, {
      header: 'Create Email Provider',
      modal: true,
      closable: true,
      style: {position: 'absolute', top: '15%'},
    });
    this.ref.onClose.subscribe((result) => {
      if (result) {
        this.loadEmailProviders();
      }
    });
  }

  setDefaultProvider(provider: EmailProvider) {
    this.emailProvidersService.setDefaultProvider(provider.id).subscribe(() => {
      this.defaultProviderId = provider.id;
      this.messageService.add({
        severity: 'success',
        summary: 'Default Provider Set',
        detail: `${provider.name} is now the default email provider.`
      });
    });
  }
}
