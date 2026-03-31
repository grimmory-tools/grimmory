import {TestBed} from '@angular/core/testing';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from 'primeng/api';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {Observable, of, throwError} from 'rxjs';
import {afterEach, describe, expect, it, vi} from 'vitest';

import {IconSelection, IconPickerService} from '../../../../shared/service/icon-picker.service';
import {UserService} from '../../../settings/user-management/user.service';
import {Shelf} from '../../model/shelf.model';
import {ShelfService} from '../../service/shelf.service';
import {ShelfCreatorComponent} from './shelf-creator.component';

describe('ShelfCreatorComponent', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  function createHarness(options: {
    createResult?: Observable<Shelf>;
    iconPickerResult?: IconSelection | null;
    currentUser?: {permissions: {admin: boolean}} | null;
  } = {}) {
    const createShelf = vi.fn(() => options.createResult ?? of({
      name: 'Created Shelf',
      icon: null,
      iconType: null,
      publicShelf: false,
    }));
    const dialogRef = {close: vi.fn()};
    const messageService = {add: vi.fn()};
    const iconPickerService = {
      open: vi.fn(() => of(options.iconPickerResult ?? null)),
    };
    const getCurrentUser = vi.fn(() => options.currentUser ?? {permissions: {admin: false}});
    const translate = vi.fn(<T = string>(key: string, params?: Record<string, unknown>) => {
      if (params?.['name']) {
        return `translated:${key}:${String(params['name'])}` as T;
      }

      return `translated:${key}` as T;
    });

    TestBed.configureTestingModule({
      providers: [
        {provide: ShelfService, useValue: {createShelf}},
        {provide: DynamicDialogRef, useValue: dialogRef},
        {provide: MessageService, useValue: messageService},
        {provide: IconPickerService, useValue: iconPickerService},
        {provide: UserService, useValue: {getCurrentUser}},
        {provide: TranslocoService, useValue: {translate}},
      ],
    });

    const component = TestBed.runInInjectionContext(() => new ShelfCreatorComponent());

    return {
      component,
      createShelf,
      dialogRef,
      messageService,
      iconPickerService,
      translate,
    };
  }

  it('picks an icon through the injected icon picker and ignores empty selections', () => {
    const pickedIcon: IconSelection = {
      type: 'PRIME_NG',
      value: 'pi pi-star',
    };
    const {component, iconPickerService} = createHarness({
      iconPickerResult: pickedIcon,
    });

    component.openIconPicker();

    expect(iconPickerService.open).toHaveBeenCalledOnce();
    expect(component.selectedIcon).toEqual(pickedIcon);

    iconPickerService.open.mockReturnValueOnce(of(null));
    component.openIconPicker();

    expect(component.selectedIcon).toEqual(pickedIcon);
  });

  it('clears the selected icon directly', () => {
    const {component} = createHarness();
    component.selectedIcon = {
      type: 'CUSTOM_SVG',
      value: 'custom-icons/moon.svg',
    };

    component.clearSelectedIcon();

    expect(component.selectedIcon).toBeNull();
  });

  it('closes the dialog without a payload when cancelled', () => {
    const {component, dialogRef} = createHarness();

    component.cancel();

    expect(dialogRef.close).toHaveBeenCalledOnce();
    expect(dialogRef.close).toHaveBeenCalledWith();
  });

  it('creates a shelf with the selected icon, shows a success toast, and closes with true', () => {
    const {component, createShelf, messageService, dialogRef, translate} = createHarness({
      currentUser: {permissions: {admin: true}},
    });

    component.shelfName = 'Weekend Reads';
    component.isPublic = true;
    component.selectedIcon = {
      type: 'PRIME_NG',
      value: 'pi pi-bookmark',
    };

    component.createShelf();

    expect(createShelf).toHaveBeenCalledWith({
      name: 'Weekend Reads',
      icon: 'pi pi-bookmark',
      iconType: 'PRIME_NG',
      publicShelf: true,
    });
    expect(translate).toHaveBeenCalledWith('common.success');
    expect(translate).toHaveBeenCalledWith('book.shelfCreator.toast.createSuccessDetail', {
      name: 'Weekend Reads',
    });
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'info',
      summary: 'translated:common.success',
      detail: 'translated:book.shelfCreator.toast.createSuccessDetail:Weekend Reads',
    });
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('creates a shelf with null icon fields when no icon is selected', () => {
    const {component, createShelf} = createHarness();

    component.shelfName = 'Plain Shelf';
    component.isPublic = false;
    component.selectedIcon = null;

    component.createShelf();

    expect(createShelf).toHaveBeenCalledWith({
      name: 'Plain Shelf',
      icon: null,
      iconType: null,
      publicShelf: false,
    });
  });

  it('shows an error toast and keeps the dialog open when create fails', () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const {component, createShelf, messageService, dialogRef, translate} = createHarness({
      createResult: throwError(() => new Error('create failed')),
    });

    component.shelfName = 'Broken Shelf';
    component.selectedIcon = {
      type: 'CUSTOM_SVG',
      value: 'custom-icons/moon.svg',
    };
    component.isPublic = true;

    component.createShelf();

    expect(createShelf).toHaveBeenCalledWith({
      name: 'Broken Shelf',
      icon: 'custom-icons/moon.svg',
      iconType: 'CUSTOM_SVG',
      publicShelf: true,
    });
    expect(translate).toHaveBeenCalledWith('common.error');
    expect(translate).toHaveBeenCalledWith('book.shelfCreator.toast.createFailedDetail');
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'translated:common.error',
      detail: 'translated:book.shelfCreator.toast.createFailedDetail',
    });
    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(errorSpy).toHaveBeenCalledOnce();
  });
});
