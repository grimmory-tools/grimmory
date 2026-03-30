import {TestBed} from '@angular/core/testing';
import {ReactiveFormsModule} from '@angular/forms';
import {TranslocoService} from '@jsverse/transloco';
import {of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {
  createDynamicDialogHarness,
  createMessageServiceProvider,
  createMessageServiceSpy,
} from '../../../../core/testing/dialog-testing';
import {EmailV2ProviderService} from '../email-v2-provider/email-v2-provider.service';
import {CreateEmailProviderDialogComponent} from './create-email-provider-dialog.component';

describe('CreateEmailProviderDialogComponent', () => {
  let createEmailProvider: ReturnType<typeof vi.fn>;
  let dialogHarness: ReturnType<typeof createDynamicDialogHarness<null>>;
  let messageService: ReturnType<typeof createMessageServiceSpy>;
  let translate: TranslocoService['translate'];

  beforeEach(() => {
    createEmailProvider = vi.fn(() => of({
      id: 9,
      name: 'Primary SMTP',
      host: 'smtp.example.com',
      port: 587,
      username: 'mailer',
      password: 'secret',
      fromAddress: 'mailer@example.com',
      auth: true,
      startTls: true,
    }));
    dialogHarness = createDynamicDialogHarness<null>(null);
    messageService = createMessageServiceSpy();
    translate = (<T = string>(key: string, params?: Record<string, unknown>) => {
      if (!params) {
        return `translated:${key}` as T;
      }

      return `translated:${key}:${JSON.stringify(params)}` as T;
    }) as TranslocoService['translate'];
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  function createComponent() {
    TestBed.configureTestingModule({
      imports: [ReactiveFormsModule],
      providers: [
        ...dialogHarness.providers,
        {
          provide: EmailV2ProviderService,
          useValue: {
            createEmailProvider,
          },
        },
        createMessageServiceProvider(messageService),
        {
          provide: TranslocoService,
          useValue: {
            translate,
          },
        },
      ],
    });

    const component = TestBed.runInInjectionContext(() => new CreateEmailProviderDialogComponent());
    component.ngOnInit();
    return component;
  }

  it('initializes the reactive form with the expected defaults and validators', () => {
    const component = createComponent();

    expect(component.emailProviderForm.getRawValue()).toEqual({
      name: '',
      host: '',
      port: null,
      username: '',
      password: '',
      fromAddress: '',
      auth: false,
      startTls: false,
    });

    component.emailProviderForm.patchValue({
      name: 'ab',
      host: '',
      port: 0,
      fromAddress: 'not-an-email',
    });

    expect(component.emailProviderForm.invalid).toBe(true);
    expect(component.emailProviderForm.controls['name'].hasError('minlength')).toBe(true);
    expect(component.emailProviderForm.controls['host'].hasError('required')).toBe(true);
    expect(component.emailProviderForm.controls['port'].hasError('min')).toBe(true);
    expect(component.emailProviderForm.controls['fromAddress'].hasError('email')).toBe(true);
  });

  it('shows a warning toast and skips the mutation when the form is invalid', () => {
    const component = createComponent();
    component.emailProviderForm.patchValue({
      name: 'ab',
      host: 'smtp.example.com',
      port: 587,
    });

    component.createEmailProvider();

    expect(createEmailProvider).not.toHaveBeenCalled();
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'warn',
      summary: 'translated:settingsEmail.provider.create.validationError',
      detail: 'translated:settingsEmail.provider.create.validationErrorDetail',
    });
    expect(dialogHarness.dialogRef.close).not.toHaveBeenCalled();
  });

  it('submits the form payload, shows a success toast, and closes the dialog on success', () => {
    const component = createComponent();
    component.emailProviderForm.setValue({
      name: 'Primary SMTP',
      host: 'smtp.example.com',
      port: 587,
      username: 'mailer',
      password: 'secret',
      fromAddress: 'mailer@example.com',
      auth: true,
      startTls: true,
    });

    component.createEmailProvider();

    expect(createEmailProvider).toHaveBeenCalledWith({
      name: 'Primary SMTP',
      host: 'smtp.example.com',
      port: 587,
      username: 'mailer',
      password: 'secret',
      fromAddress: 'mailer@example.com',
      auth: true,
      startTls: true,
    });
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'translated:settingsEmail.provider.create.success',
      detail: 'translated:settingsEmail.provider.create.successDetail',
    });
    expect(dialogHarness.dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('shows the translated backend error detail and keeps the dialog open when creation fails with a message', () => {
    createEmailProvider.mockReturnValueOnce(throwError(() => ({
      error: {
        message: 'SMTP rejected credentials',
      },
    })));
    const component = createComponent();
    component.emailProviderForm.setValue({
      name: 'Broken SMTP',
      host: 'smtp.example.com',
      port: 465,
      username: 'mailer',
      password: 'bad-secret',
      fromAddress: 'mailer@example.com',
      auth: true,
      startTls: false,
    });

    component.createEmailProvider();

    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'translated:settingsEmail.provider.create.failed',
      detail: 'translated:settingsEmail.provider.create.failedDetail:{"message":"SMTP rejected credentials"}',
    });
    expect(dialogHarness.dialogRef.close).not.toHaveBeenCalled();
  });

  it('falls back to the default failure detail when the backend does not return a message', () => {
    createEmailProvider.mockReturnValueOnce(throwError(() => new Error('network down')));
    const component = createComponent();
    component.emailProviderForm.setValue({
      name: 'Fallback SMTP',
      host: 'smtp.example.com',
      port: 2525,
      username: '',
      password: '',
      fromAddress: '',
      auth: false,
      startTls: false,
    });

    component.createEmailProvider();

    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'translated:settingsEmail.provider.create.failed',
      detail: 'translated:settingsEmail.provider.create.failedDefault',
    });
    expect(dialogHarness.dialogRef.close).not.toHaveBeenCalled();
  });

  it('closes the dialog without a result when dismissed explicitly', () => {
    const component = createComponent();

    component.closeDialog();

    expect(dialogHarness.dialogRef.close).toHaveBeenCalledOnce();
    expect(dialogHarness.dialogRef.close).toHaveBeenCalledWith();
  });
});
