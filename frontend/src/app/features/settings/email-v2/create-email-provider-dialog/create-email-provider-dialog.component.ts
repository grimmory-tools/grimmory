import {Component, inject, OnInit} from '@angular/core';
import {Button} from 'primeng/button';
import {Checkbox} from 'primeng/checkbox';
import {InputText} from 'primeng/inputtext';

import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {MessageService} from 'primeng/api';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {EmailV2ProviderService} from '../email-v2-provider/email-v2-provider.service';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-create-email-provider-dialog',
  imports: [
    Button,
    Checkbox,
    InputText,
    ReactiveFormsModule,
    TranslocoDirective,
    TranslocoPipe
  ],
  templateUrl: './create-email-provider-dialog.component.html',
  styleUrl: './create-email-provider-dialog.component.scss'
})
export class CreateEmailProviderDialogComponent implements OnInit {
  emailProviderForm!: FormGroup;

  private fb = inject(FormBuilder);
  private emailProviderService = inject(EmailV2ProviderService);
  private messageService = inject(MessageService);
  private ref = inject(DynamicDialogRef);
  private readonly t = inject(TranslocoService);

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
        summary: this.t.translate('settingsEmail.provider.create.validationError'),
        detail: this.t.translate('settingsEmail.provider.create.validationErrorDetail')
      });
      return;
    }

    const emailProviderData = this.emailProviderForm.value;

    this.emailProviderService.createEmailProvider(emailProviderData).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('settingsEmail.provider.create.success'),
          detail: this.t.translate('settingsEmail.provider.create.successDetail')
        });
        this.ref.close(true);
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('settingsEmail.provider.create.failed'),
          detail: err?.error?.message
            ? this.t.translate('settingsEmail.provider.create.failedDetail', {message: err.error.message})
            : this.t.translate('settingsEmail.provider.create.failedDefault')
        });
      }
    });
  }

  closeDialog(): void {
    this.ref.close();
  }
}
