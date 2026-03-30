import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {TranslocoService} from '@jsverse/transloco';
import {LoadingService} from '../../../../../core/services/loading.service';
import {
  createDynamicDialogHarness,
  createMessageServiceProvider,
  createMessageServiceSpy,
} from '../../../../../core/testing/dialog-testing';
import {BookMetadataManageService} from '../../../service/book-metadata-manage.service';
import {LockUnlockMetadataDialogComponent} from './lock-unlock-metadata-dialog.component';

describe('LockUnlockMetadataDialogComponent', () => {
  let toggleFieldLocks: ReturnType<typeof vi.fn>;
  let dialogHarness: ReturnType<typeof createDynamicDialogHarness<{bookIds: Set<number>}>>;
  let messageService: ReturnType<typeof createMessageServiceSpy>;
  let loadingService: {
    show: ReturnType<typeof vi.fn>;
    hide: ReturnType<typeof vi.fn>;
  };
  let translate: TranslocoService['translate'];

  beforeEach(() => {
    toggleFieldLocks = vi.fn(() => of(void 0));
    dialogHarness = createDynamicDialogHarness({bookIds: new Set([7, 9])});
    messageService = createMessageServiceSpy();
    loadingService = {
      show: vi.fn(() => 'loader-token'),
      hide: vi.fn(),
    };
    translate = (<T = string>(key: string) => `translated:${key}` as T) as TranslocoService['translate'];
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  function createComponent() {
    TestBed.configureTestingModule({
      providers: [
        ...dialogHarness.providers,
        {
          provide: BookMetadataManageService,
          useValue: {
            toggleFieldLocks,
          },
        },
        createMessageServiceProvider(messageService),
        {
          provide: LoadingService,
          useValue: loadingService,
        },
        {
          provide: TranslocoService,
          useValue: {
            translate,
          },
        },
      ],
    });

    const component = TestBed.runInInjectionContext(() => new LockUnlockMetadataDialogComponent());
    component.ngOnInit();
    return component;
  }

  it('toggles every lockable field between locked and unlocked states', () => {
    const component = createComponent();

    component.toggleLockAll('LOCK');
    expect(component.lockableFields.every(field => component.fieldLocks[field] === true)).toBe(true);

    component.toggleLockAll('UNLOCK');
    expect(component.lockableFields.every(field => component.fieldLocks[field] === false)).toBe(true);
  });

  it('returns translated labels and icons for unselected, locked, and unlocked states', () => {
    const component = createComponent();

    expect(component.getLockLabel('titleLocked')).toBe('translated:book.lockUnlockDialog.unselected');
    expect(component.getLockIcon('titleLocked')).toBe('');

    component.fieldLocks['titleLocked'] = true;
    expect(component.getLockLabel('titleLocked')).toBe('translated:book.lockUnlockDialog.locked');
    expect(component.getLockIcon('titleLocked')).toBe('pi pi-lock');

    component.fieldLocks['titleLocked'] = false;
    expect(component.getLockLabel('titleLocked')).toBe('translated:book.lockUnlockDialog.unlocked');
    expect(component.getLockIcon('titleLocked')).toBe('pi pi-lock-open');
  });

  it('resets every field lock back to an unselected state', () => {
    const component = createComponent();
    component.toggleLockAll('LOCK');

    component.resetFieldLocks();

    expect(component.lockableFields.every(field => component.fieldLocks[field] === undefined)).toBe(true);
  });

  it('cycles a field through locked, unlocked, and cleared states', () => {
    const component = createComponent();

    component.cycleLockState('titleLocked');
    expect(component.fieldLocks['titleLocked']).toBe(true);

    component.cycleLockState('titleLocked');
    expect(component.fieldLocks['titleLocked']).toBe(false);

    component.cycleLockState('titleLocked');
    expect(component.fieldLocks['titleLocked']).toBeUndefined();
  });

  it('applies selected field locks, shows a success toast, and closes the dialog', () => {
    const component = createComponent();
    component.fieldLocks['titleLocked'] = true;
    component.fieldLocks['authorsLocked'] = false;
    component.fieldLocks['subtitleLocked'] = undefined;

    component.applyFieldLocks();

    expect(toggleFieldLocks).toHaveBeenCalledWith(new Set([7, 9]), {
      titleLocked: 'LOCK',
      authorsLocked: 'UNLOCK',
    });
    expect(loadingService.show).toHaveBeenCalledWith(
      'translated:book.lockUnlockDialog.toast.updatingFieldLocks'
    );
    expect(loadingService.hide).toHaveBeenCalledWith('loader-token');
    expect(component.isSaving).toBe(false);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'translated:book.lockUnlockDialog.toast.updatedSummary',
      detail: 'translated:book.lockUnlockDialog.toast.updatedDetail',
    });
    expect(dialogHarness.dialogRef.close).toHaveBeenCalledWith('fields-updated');
  });

  it('shows an error toast and keeps the dialog open when saving fails', () => {
    toggleFieldLocks.mockReturnValueOnce(throwError(() => new Error('save failed')));
    const component = createComponent();
    component.fieldLocks['titleLocked'] = false;

    component.applyFieldLocks();

    expect(toggleFieldLocks).toHaveBeenCalledWith(new Set([7, 9]), {
      titleLocked: 'UNLOCK',
    });
    expect(loadingService.show).toHaveBeenCalledWith(
      'translated:book.lockUnlockDialog.toast.updatingFieldLocks'
    );
    expect(loadingService.hide).toHaveBeenCalledWith('loader-token');
    expect(component.isSaving).toBe(false);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'translated:book.lockUnlockDialog.toast.failedSummary',
      detail: 'translated:book.lockUnlockDialog.toast.failedDetail',
    });
    expect(dialogHarness.dialogRef.close).not.toHaveBeenCalled();
  });
});
