import {ComponentFixture, TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TranslocoService} from '@jsverse/transloco';

import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {SetupComponent} from './setup.component';
import {SetupService} from './setup.service';

describe('SetupComponent', () => {
  let fixture: ComponentFixture<SetupComponent>;
  let component: SetupComponent;
  let setupService: {createAdmin: ReturnType<typeof vi.fn>};
  let router: {navigate: ReturnType<typeof vi.fn>};
  let translocoService: TranslocoService;

  beforeEach(async () => {
    setupService = {
      createAdmin: vi.fn(() => of(void 0)),
    };
    router = {
      navigate: vi.fn(() => Promise.resolve(true)),
    };

    vi.useFakeTimers();

    await TestBed.configureTestingModule({
      imports: [SetupComponent, getTranslocoModule()],
      providers: [
        {provide: SetupService, useValue: setupService},
        {provide: Router, useValue: router},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SetupComponent);
    component = fixture.componentInstance;
    translocoService = TestBed.inject(TranslocoService);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  function fillValidForm() {
    component.setupForm.setValue({
      name: 'Admin User',
      username: 'admin',
      email: 'admin@example.com',
      password: 'supersecure',
      confirmPassword: 'supersecure',
    });
  }

  it('refuses to submit when the form is invalid', () => {
    component.setupForm.patchValue({
      name: 'Admin User',
      username: 'admin',
    });

    component.onSubmit();

    expect(setupService.createAdmin).not.toHaveBeenCalled();
    expect(component.loading()).toBe(false);
  });

  it('submits the admin payload without confirmPassword', () => {
    fillValidForm();

    component.onSubmit();

    expect(component.loading()).toBe(true);
    expect(component.error).toBe(null);
    expect(setupService.createAdmin).toHaveBeenCalledWith({
      name: 'Admin User',
      username: 'admin',
      email: 'admin@example.com',
      password: 'supersecure',
    });
  });

  it('marks success and redirects to login after the delay', async () => {
    fillValidForm();

    component.onSubmit();

    expect(component.success).toBe(true);
    expect(component.loading()).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1500);
    await Promise.resolve();

    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('surfaces backend error messages and clears loading', () => {
    fillValidForm();
    setupService.createAdmin.mockReturnValueOnce(
      throwError(() => ({error: {message: 'Setup failed'}}))
    );

    component.onSubmit();

    expect(component.loading()).toBe(false);
    expect(component.error).toBe('Setup failed');
    expect(component.success).toBe(false);
  });

  it('uses the translated fallback error when the backend does not provide one', () => {
    fillValidForm();
    setupService.createAdmin.mockReturnValueOnce(throwError(() => ({error: {}})));

    component.onSubmit();

    expect(component.error).toBe(
      translocoService.translate('shared.setup.toast.createFailedDefault')
    );
  });
});
