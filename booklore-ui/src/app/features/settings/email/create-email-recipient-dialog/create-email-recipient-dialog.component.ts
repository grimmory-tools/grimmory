import {Component, inject} from '@angular/core';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {MessageService} from 'primeng/api';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {Checkbox} from 'primeng/checkbox';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {EmailV2RecipientService} from '../../email-v2/email-v2-recipient/email-v2-recipient.service';

@Component({
  selector: 'app-create-email-recipient-dialog',
  imports: [
    Checkbox,
    ReactiveFormsModule,
    Button,
    InputText
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

  constructor() {
    this.emailRecipientForm = this.fb.group({
      name: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      defaultRecipient: [false]
    });
  }

  createEmailRecipient(): void {
    if (this.emailRecipientForm.invalid) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Validation Error',
        detail: 'Please correct errors before submitting.'
      });
      return;
    }

    const emailRecipientData = this.emailRecipientForm.value;

    this.emailRecipientService.createRecipient(emailRecipientData).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Recipient Added',
          detail: `${emailRecipientData.name} has been successfully added.`
        });
        this.ref.close(true);
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Recipient Creation Failed',
          detail: err?.error?.message
            ? `Unable to create recipient: ${err.error.message}`
            : 'An unexpected error occurred while adding the recipient. Please try again later.'
        });
      }
    });
  }
}
