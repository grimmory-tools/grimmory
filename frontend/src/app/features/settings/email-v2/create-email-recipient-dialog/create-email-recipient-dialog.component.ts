import {Component, inject} from '@angular/core';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {MessageService} from 'primeng/api';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {Checkbox} from 'primeng/checkbox';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {EmailV2RecipientService} from '../email-v2-recipient/email-v2-recipient.service';
import {Tooltip} from 'primeng/tooltip';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-create-email-recipient-dialog',
  imports: [
    Checkbox,
    ReactiveFormsModule,
    Button,
    InputText,
    Tooltip,
    TranslocoDirective,
    TranslocoPipe
  ],
  templateUrl: './create-email-recipient-dialog.component.html',
  styleUrls: ['./create-email-recipient-dialog.component.scss']
})
export class CreateEmailRecipientDialogComponent {
  emailRecipientForm: FormGroup;
  private fb = inject(FormBuilder);
  private emailRecipientService = inject(EmailV2RecipientService);
  private messageService = inject(MessageService);
  private ref = inject(DynamicDialogRef);
  private readonly t = inject(TranslocoService);

  constructor() {
    this.emailRecipientForm = this.fb.group({
      name: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      defaultRecipient: [false]
    });
  }

  closeDialog(): void {
    this.ref.close();
  }

  createEmailRecipient(): void {
    if (this.emailRecipientForm.invalid) {
      this.messageService.add({
        severity: 'warn',
        summary: this.t.translate('settingsEmail.recipient.create.validationError'),
        detail: this.t.translate('settingsEmail.recipient.create.validationErrorDetail')
      });
      return;
    }

    const emailRecipientData = this.emailRecipientForm.value;

    this.emailRecipientService.createRecipient(emailRecipientData).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('settingsEmail.recipient.create.success'),
          detail: this.t.translate('settingsEmail.recipient.create.successDetail', {name: emailRecipientData.name})
        });
        this.ref.close(true);
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('settingsEmail.recipient.create.failed'),
          detail: err?.error?.message
            ? this.t.translate('settingsEmail.recipient.create.failedDetail', {message: err.error.message})
            : this.t.translate('settingsEmail.recipient.create.failedDefault')
        });
      }
    });
  }
}
