import {Component, inject, OnInit} from '@angular/core';
import {Button} from 'primeng/button';
import {MessageService} from 'primeng/api';
import {RadioButton} from 'primeng/radiobutton';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {TableModule} from 'primeng/table';
import {Tooltip} from 'primeng/tooltip';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {EmailV2RecipientService} from './email-v2-recipient.service';
import {EmailRecipient} from '../email-recipient.model';
import {DialogLauncherService} from '../../../../shared/services/dialog-launcher.service';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-email-v2-recipient',
  imports: [
    Button,
    RadioButton,
    ReactiveFormsModule,
    TableModule,
    Tooltip,
    FormsModule,
    TranslocoDirective,
    TranslocoPipe
  ],
  templateUrl: './email-v2-recipient.component.html',
  styleUrl: './email-v2-recipient.component.scss'
})
export class EmailV2RecipientComponent implements OnInit {
  recipientEmails: EmailRecipient[] = [];
  editingRecipientIds: number[] = [];
  ref: DynamicDialogRef | undefined | null;
  private dialogLauncherService = inject(DialogLauncherService);
  private emailRecipientService = inject(EmailV2RecipientService);
  private messageService = inject(MessageService);
  private t = inject(TranslocoService);
  defaultRecipientId: unknown;

  ngOnInit(): void {
    this.loadRecipientEmails();
  }

  loadRecipientEmails(): void {
    this.emailRecipientService.getRecipients().subscribe({
      next: (recipients: EmailRecipient[]) => {
        this.recipientEmails = recipients.map((recipient) => ({
          ...recipient,
          isEditing: false,
        }));
        const defaultRecipient = recipients.find((recipient) => recipient.defaultRecipient);
        this.defaultRecipientId = defaultRecipient ? defaultRecipient.id : null;
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: this.t.translate('settingsEmail.recipient.loadError'),
        });
      },
    });
  }

  toggleEditRecipient(recipient: EmailRecipient): void {
    recipient.isEditing = !recipient.isEditing;
    if (recipient.isEditing) {
      this.editingRecipientIds.push(recipient.id);
    } else {
      this.editingRecipientIds = this.editingRecipientIds.filter((id) => id !== recipient.id);
    }
  }

  saveRecipient(recipient: EmailRecipient): void {
    this.emailRecipientService.updateRecipient(recipient).subscribe({
      next: () => {
        recipient.isEditing = false;
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('common.success'),
          detail: this.t.translate('settingsEmail.recipient.updateSuccess'),
        });
        this.loadRecipientEmails();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: this.t.translate('settingsEmail.recipient.updateError'),
        });
      },
    });
  }

  deleteRecipient(recipient: EmailRecipient): void {
    if (confirm(this.t.translate('settingsEmail.recipient.deleteConfirm', {email: recipient.email}))) {
      this.emailRecipientService.deleteRecipient(recipient.id).subscribe({
        next: () => {
          this.messageService.add({
            severity: 'success',
            summary: this.t.translate('common.success'),
            detail: this.t.translate('settingsEmail.recipient.deleteSuccess', {email: recipient.email}),
          });
          this.loadRecipientEmails();
        },
        error: () => {
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('common.error'),
            detail: this.t.translate('settingsEmail.recipient.deleteError'),
          });
        },
      });
    }
  }

  openAddRecipientDialog() {
    this.ref = this.dialogLauncherService.openEmailRecipientDialog();
    this.ref?.onClose.subscribe((result) => {
      if (result) {
        this.loadRecipientEmails();
      }
    });
  }

  setDefaultRecipient(recipient: EmailRecipient) {
    this.emailRecipientService.setDefaultRecipient(recipient.id).subscribe(() => {
      this.defaultRecipientId = recipient.id;
      this.messageService.add({
        severity: 'success',
        summary: this.t.translate('settingsEmail.recipient.defaultSetSummary'),
        detail: this.t.translate('settingsEmail.recipient.defaultSetDetail', {email: recipient.email}),
      });
    });
  }
}
