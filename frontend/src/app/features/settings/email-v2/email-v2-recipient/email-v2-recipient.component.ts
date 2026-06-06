import {Component, DestroyRef, inject, OnInit, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {finalize} from 'rxjs';
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
import {ProgressSpinner} from 'primeng/progressspinner';

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
    TranslocoPipe,
    ProgressSpinner
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
  private destroyRef = inject(DestroyRef);
  loading = signal(false);
  private loadingRequestSeq = 0;
  private activeLoadingRequestSeq: number | null = null;
  defaultRecipientId: unknown;

  ngOnInit(): void {
    this.loadRecipientEmails();
  }

  loadRecipientEmails(): void {
    const requestSeq = ++this.loadingRequestSeq;
    this.activeLoadingRequestSeq = requestSeq;
    this.loading.set(true);

    this.emailRecipientService.getRecipients().pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => {
        if (this.activeLoadingRequestSeq === requestSeq) {
          this.loading.set(false);
        }
      })
    ).subscribe({
      next: (recipients: EmailRecipient[]) => {
        if (this.activeLoadingRequestSeq !== requestSeq) return;

        this.recipientEmails = recipients.map((recipient) => ({
          ...recipient,
          isEditing: false,
        }));
        const defaultRecipient = recipients.find((recipient) => recipient.defaultRecipient);
        this.defaultRecipientId = defaultRecipient ? defaultRecipient.id : null;
      },
      error: () => {
        if (this.activeLoadingRequestSeq !== requestSeq) return;

        this.recipientEmails = [];
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
    this.emailRecipientService.updateRecipient(recipient).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
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
      this.emailRecipientService.deleteRecipient(recipient.id).pipe(
        takeUntilDestroyed(this.destroyRef)
      ).subscribe({
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
    this.ref?.onClose.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((result) => {
      if (result) {
        this.loadRecipientEmails();
      }
    });
  }

  setDefaultRecipient(recipient: EmailRecipient) {
    this.emailRecipientService.setDefaultRecipient(recipient.id).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.defaultRecipientId = recipient.id;
      this.messageService.add({
        severity: 'success',
        summary: this.t.translate('settingsEmail.recipient.defaultSetSummary'),
        detail: this.t.translate('settingsEmail.recipient.defaultSetDetail', {email: recipient.email}),
      });
    });
  }
}
