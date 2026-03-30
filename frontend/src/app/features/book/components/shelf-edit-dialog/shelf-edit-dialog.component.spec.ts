import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {MessageService} from 'primeng/api';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {Observable, of, throwError} from 'rxjs';
import {afterEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {IconSelection, IconPickerService} from '../../../../shared/service/icon-picker.service';
import {UserService} from '../../../settings/user-management/user.service';
import {Shelf} from '../../model/shelf.model';
import {ShelfService} from '../../service/shelf.service';
import {ShelfEditDialogComponent} from './shelf-edit-dialog.component';

describe('ShelfEditDialogComponent', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  function createShelf(overrides: Partial<Shelf> = {}): Shelf {
    return {
      id: 7,
      name: 'Favorites',
      icon: null,
      iconType: null,
      publicShelf: false,
      ...overrides,
    };
  }

  function createHarness(options: {
    shelfId?: number;
    shelvesData?: Shelf[];
    updateResult?: Observable<Shelf>;
    iconPickerResult?: IconSelection | null;
    currentUser?: {permissions: {admin: boolean}} | null;
  } = {}) {
    const shelves = signal<Shelf[]>(options.shelvesData ?? []);
    const updateShelf = vi.fn(() => options.updateResult ?? of(createShelf()));
    const dialogRef = {close: vi.fn()};
    const messageService = {add: vi.fn()};
    const iconPickerService = {
      open: vi.fn(() => of(options.iconPickerResult ?? null)),
    };
    const getCurrentUser = vi.fn(() => options.currentUser ?? {permissions: {admin: false}});

    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      providers: [
        {provide: ShelfService, useValue: {shelves, updateShelf}},
        {provide: DynamicDialogConfig, useValue: {data: {shelfId: options.shelfId ?? 7}}},
        {provide: DynamicDialogRef, useValue: dialogRef},
        {provide: MessageService, useValue: messageService},
        {provide: IconPickerService, useValue: iconPickerService},
        {provide: UserService, useValue: {getCurrentUser}},
      ],
    });

    const component = TestBed.runInInjectionContext(() => new ShelfEditDialogComponent());
    component.ngOnInit();
    TestBed.flushEffects();

    return {
      component,
      shelves,
      updateShelf,
      dialogRef,
      messageService,
      iconPickerService,
    };
  }

  it('hydrates the dialog from a matching PrimeNG shelf', () => {
    const shelf = createShelf({
      id: 42,
      name: 'Queue',
      icon: 'bookmark',
      iconType: 'PRIME_NG',
      publicShelf: true,
    });

    const {component} = createHarness({
      shelfId: 42,
      shelvesData: [shelf],
    });

    expect(component.shelf).toEqual(shelf);
    expect(component.shelfName).toBe('Queue');
    expect(component.isPublic).toBe(true);
    expect(component.selectedIcon).toEqual({
      type: 'PRIME_NG',
      value: 'pi pi-bookmark',
    });
  });

  it('hydrates custom SVG icons and only initializes once after the shelf becomes available', () => {
    const {component, shelves} = createHarness({
      shelfId: 11,
      shelvesData: [createShelf({id: 99, name: 'Other Shelf'})],
    });

    expect(component.shelf).toBeUndefined();
    expect(component.shelfName).toBe('');
    expect(component.selectedIcon).toBeNull();

    shelves.set([
      createShelf({
        id: 11,
        name: 'Readable',
        icon: 'custom-icons/moon.svg',
        iconType: 'CUSTOM_SVG',
      }),
    ]);
    TestBed.flushEffects();

    expect(component.shelfName).toBe('Readable');
    expect(component.selectedIcon).toEqual({
      type: 'CUSTOM_SVG',
      value: 'custom-icons/moon.svg',
    });

    shelves.set([
      createShelf({
        id: 11,
        name: 'Should Not Replace',
        icon: 'bookmark',
        iconType: 'PRIME_NG',
        publicShelf: true,
      }),
    ]);
    TestBed.flushEffects();

    expect(component.shelfName).toBe('Readable');
    expect(component.isPublic).toBe(false);
    expect(component.selectedIcon).toEqual({
      type: 'CUSTOM_SVG',
      value: 'custom-icons/moon.svg',
    });
  });

  it('picks and clears icons through the dialog-local icon selection methods', () => {
    const pickedIcon: IconSelection = {
      type: 'PRIME_NG',
      value: 'pi pi-star',
    };
    const {component, iconPickerService} = createHarness({
      shelvesData: [createShelf()],
      iconPickerResult: pickedIcon,
    });

    component.openIconPicker();
    expect(iconPickerService.open).toHaveBeenCalledOnce();
    expect(component.selectedIcon).toEqual(pickedIcon);

    component.clearSelectedIcon();
    expect(component.selectedIcon).toBeNull();
  });

  it('saves the edited shelf, shows a success toast, and closes the dialog', () => {
    const {component, updateShelf, messageService, dialogRef} = createHarness({
      shelfId: 7,
      shelvesData: [createShelf({id: 7})],
    });

    component.shelfName = 'Renamed Shelf';
    component.isPublic = true;
    component.selectedIcon = {
      type: 'PRIME_NG',
      value: 'pi pi-heart',
    };

    component.save();

    expect(updateShelf).toHaveBeenCalledWith({
      name: 'Renamed Shelf',
      icon: 'pi pi-heart',
      iconType: 'PRIME_NG',
      publicShelf: true,
    }, 7);
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'success',
    }));
    expect(dialogRef.close).toHaveBeenCalledOnce();
  });

  it('shows an error toast and keeps the dialog open when save fails', () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const {component, updateShelf, messageService, dialogRef} = createHarness({
      shelfId: 7,
      shelvesData: [createShelf({id: 7})],
      updateResult: throwError(() => new Error('save failed')),
    });

    component.shelfName = 'Broken Shelf';
    component.selectedIcon = null;
    component.isPublic = false;
    component.save();

    expect(updateShelf).toHaveBeenCalledWith({
      name: 'Broken Shelf',
      icon: null,
      iconType: null,
      publicShelf: false,
    }, 7);
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'error',
    }));
    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(errorSpy).toHaveBeenCalledOnce();
  });

  it('closes the dialog without saving when cancelled', () => {
    const {component, dialogRef} = createHarness({
      shelvesData: [createShelf()],
    });

    component.closeDialog();

    expect(dialogRef.close).toHaveBeenCalledOnce();
  });
});
