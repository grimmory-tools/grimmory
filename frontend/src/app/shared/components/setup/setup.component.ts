import {Component, inject} from '@angular/core';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {Router} from '@angular/router';
import {SetupService} from './setup.service';
import {InputText} from 'primeng/inputtext';
import {Button} from 'primeng/button';
import {Message} from 'primeng/message';
import {passwordMatchValidator} from '../../validators/password-match.validator';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-setup',
  templateUrl: './setup.component.html',
  styleUrls: ['./setup.component.scss'],
  standalone: true,
  imports: [
    ReactiveFormsModule,
    InputText,
    Button,
    Message,
    TranslocoDirective
  ]
})
export class SetupComponent {
  setupForm: FormGroup;
  loading = false;
  error: string | null = null;
  success = false;
  private readonly t = inject(TranslocoService);

  constructor(
    private fb: FormBuilder,
    private setupService: SetupService,
    private router: Router
  ) {
    this.setupForm = this.fb.group({
      name: ['', [Validators.required]],
      username: ['', [Validators.required]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required]],
    }, {validators: [passwordMatchValidator('password', 'confirmPassword')]});
  }

  onSubmit(): void {
    if (this.setupForm.invalid) return;

    this.loading = true;
    this.error = null;
    // Remove confirm password from the payload, as it does not need to be sent to backend
    const { confirmPassword, ...payload } = this.setupForm.value;
    void confirmPassword;
    this.setupService.createAdmin(payload).subscribe({
      next: () => {
        this.success = true;
        setTimeout(() => this.router.navigate(['/login']), 1500);
      },
      error: (err) => {
        this.loading = false;
        this.error =
          err?.error?.message || this.t.translate('shared.setup.toast.createFailedDefault');
      },
    });
  }
}
