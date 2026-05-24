import {ComponentFixture, TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';

import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {AuthService} from '../../service/auth.service';
import {ChangePasswordComponent} from './change-password.component';
import {UserService} from '../../../features/settings/user-management/user.service';

describe('ChangePasswordComponent', () => {
  let fixture: ComponentFixture<ChangePasswordComponent>;
  let component: ChangePasswordComponent;
  let userService: {changePassword: ReturnType<typeof vi.fn>};
  let authService: {logout: ReturnType<typeof vi.fn>};
  let messageService: {add: ReturnType<typeof vi.fn>};
  let translocoService: TranslocoService;

  beforeEach(async () => {
    userService = {
      changePassword: vi.fn(() => of(void 0)),
    };
    authService = {
      logout: vi.fn(),
    };
    messageService = {
      add: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [ChangePasswordComponent, getTranslocoModule()],
      providers: [
        {provide: UserService, useValue: userService},
        {provide: AuthService, useValue: authService},
        {provide: MessageService, useValue: messageService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ChangePasswordComponent);
    component = fixture.componentInstance;
    translocoService = TestBed.inject(TranslocoService);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('tracks whether the new passwords match', () => {
    component.newPassword = 'new-password';
    component.confirmNewPassword = 'new-password';
    expect(component.passwordsMatch).toBe(true);

    component.confirmNewPassword = 'different';
    expect(component.passwordsMatch).toBe(false);
  });

  it('rejects submissions with missing fields before calling the service', () => {
    component.currentPassword = 'current';
    component.newPassword = '';
    component.confirmNewPassword = 'new-password';

    component.changePassword();

    expect(component.errorMessage).toBe(
      translocoService.translate('shared.changePassword.validation.allFieldsRequired')
    );
    expect(userService.changePassword).not.toHaveBeenCalled();
  });

  it('rejects submissions when the new passwords do not match', () => {
    component.currentPassword = 'current';
    component.newPassword = 'new-password';
    component.confirmNewPassword = 'different';

    component.changePassword();

    expect(component.errorMessage).toBe(
      translocoService.translate('shared.changePassword.validation.passwordsDoNotMatch')
    );
    expect(userService.changePassword).not.toHaveBeenCalled();
  });

  it('rejects submissions when the new password matches the current password', () => {
    component.currentPassword = 'same-password';
    component.newPassword = 'same-password';
    component.confirmNewPassword = 'same-password';

    component.changePassword();

    expect(component.errorMessage).toBe(
      translocoService.translate('shared.changePassword.validation.sameAsCurrentPassword')
    );
    expect(userService.changePassword).not.toHaveBeenCalled();
  });

  it('changes the password and logs the user out on success', () => {
    component.currentPassword = 'current-password';
    component.newPassword = 'new-password';
    component.confirmNewPassword = 'new-password';

    component.changePassword();

    expect(userService.changePassword).toHaveBeenCalledWith('current-password', 'new-password');
    expect(component.successMessage).toBe(
      translocoService.translate('shared.changePassword.toast.success')
    );
    expect(authService.logout).toHaveBeenCalled();
    expect(messageService.add).not.toHaveBeenCalled();
  });

  it('surfaces backend failures through the error message and toast service', () => {
    userService.changePassword.mockReturnValueOnce(
      throwError(() => ({message: 'Password update failed'}))
    );
    component.currentPassword = 'current-password';
    component.newPassword = 'new-password';
    component.confirmNewPassword = 'new-password';

    component.changePassword();

    expect(component.errorMessage).toBe('Password update failed');
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: translocoService.translate('shared.changePassword.toast.failedSummary'),
      detail: 'Password update failed',
    });
    expect(authService.logout).not.toHaveBeenCalled();
  });
});
