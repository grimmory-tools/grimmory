import { Component, inject, OnInit } from '@angular/core';
import { Button } from 'primeng/button';
import { Checkbox } from 'primeng/checkbox';
import { InputText } from 'primeng/inputtext';
import { NgIf } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmailProviderService } from '../email-provider/email-provider.service';
import { MessageService } from 'primeng/api';
import { DynamicDialogRef } from 'primeng/dynamicdialog';

@Component({
  selector: 'app-create-email-provider-dialog',
  imports: [
    Button,
    Checkbox,
    InputText,
    NgIf,
    ReactiveFormsModule
  ],
  templateUrl: './create-email-provider-dialog.component.html',
  styleUrl: './create-email-provider-dialog.component.scss'
})
export class CreateEmailProviderDialogComponent implements OnInit {
  emailProviderForm!: FormGroup;

  private fb = inject(FormBuilder);
  private emailProviderService = inject(EmailProviderService);
  private messageService = inject(MessageService);
  private ref = inject(DynamicDialogRef);

  ngOnInit() {
    this.emailProviderForm = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(3)]],
      host: ['', Validators.required],
      port: [null, [Validators.required, Validators.min(1)]],
      username: [''],
      password: [''],
      fromAddress: ['', [Validators.email]],
      auth: [false],
      startTls: [false]
    });
  }

  createEmailProvider() {
    if (this.emailProviderForm.invalid) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Validation Error',
        detail: 'Please correct errors before submitting.'
      });
      return;
    }

    const emailProviderData = this.emailProviderForm.value;

    this.emailProviderService.createEmailProvider(emailProviderData).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Email Provider Created',
          detail: 'The email provider has been successfully created.'
        });
        this.ref.close(true);
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Email Provider Creation Failed',
          detail: err?.error?.message
            ? `Unable to create email provider: ${err.error.message}`
            : 'An unexpected error occurred while creating the email provider. Please try again later.'
        });
      }
    });
  }
}
