import { beforeEach, describe, expect, it, vi } from 'vitest';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppMenuitemComponent } from './app.menuitem.component';
import { MenuService } from './app.menu.service';
import { DialogLauncherService } from '../../services/dialog-launcher.service';
import { BookDialogHelperService } from '../../../features/book/components/book-browser/book-dialog-helper.service';
import { UserService } from '../../../features/settings/user-management/user.service';
import {BookShelfDragDropService} from '../../../features/book/components/book-browser/book-shelf-drag-drop.service';

describe('AppMenuitemComponent', () => {
  let fixture: ComponentFixture<AppMenuitemComponent>;
  let component: AppMenuitemComponent;

  const menuService = {
    currentPath: signal('/'),
  };

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [AppMenuitemComponent],
      providers: [
        { provide: MenuService, useValue: menuService },
        {
          provide: DialogLauncherService,
          useValue: {
            openLibraryCreateDialog: vi.fn(),
            openMagicShelfCreateDialog: vi.fn(),
          },
        },
        {
          provide: BookDialogHelperService,
          useValue: {
            openShelfCreatorDialog: vi.fn(),
          },
        },
        {
          provide: UserService,
          useValue: {
            currentUser: signal({
              permissions: {
                canManageLibrary: true,
                admin: true,
              },
            }),
          },
        },
        {
          provide: BookShelfDragDropService,
          useValue: {
            onShelfDragEnter: vi.fn(),
            onShelfDragOver: vi.fn(),
            onShelfDragLeave: vi.fn(),
            dropOnShelf: vi.fn(),
          },
        }
      ],
    });

    TestBed.overrideComponent(AppMenuitemComponent, {
      set: {
        template: '',
      },
    });

    fixture = TestBed.createComponent(AppMenuitemComponent);
    component = fixture.componentInstance;
    component.index = 0;
  });

  it('passes context menu items through to the template', () => {
    const editCommand = vi.fn();
    const nestedCommand = vi.fn();

    component.item = {
      label: 'Shelf A',
      type: 'shelf',
      contextMenuItems: [
        { label: 'Edit', command: editCommand },
        {
          label: 'More',
          items: [{ label: 'Delete', command: nestedCommand }],
        },
      ],
    };

    fixture.detectChanges();

    const items = component.item.contextMenuItems!;
    expect(items[0].label).toBe('Edit');
    items[0].command?.({} as never);
    expect(editCommand).toHaveBeenCalled();

    expect(items[1].items?.[0].label).toBe('Delete');
    items[1].items?.[0].command?.({} as never);
    expect(nestedCommand).toHaveBeenCalled();
  });

  it('hides the context menu button for items without context menu actions', () => {
    component.item = {
      label: 'Unshelved',
      type: 'shelf',
    };

    fixture.detectChanges();

    expect(component.shouldShowContextMenuButton()).toBe(false);
  });

  it('exposes admin and canManipulateLibrary as computed signals from UserService', () => {
    component.item = { label: 'Test' };
    fixture.detectChanges();

    expect(component.admin()).toBe(true);
    expect(component.canManipulateLibrary()).toBe(true);
  });

  it('reports route as active when currentPath matches item routerLink', () => {
    component.item = {
      label: 'Dashboard',
      routerLink: ['/dashboard'],
    };

    menuService.currentPath.set('/dashboard');
    fixture.detectChanges();

    expect(component.isRouteActive()).toBe(true);
  });

  it('reports route as inactive when currentPath does not match', () => {
    component.item = {
      label: 'Dashboard',
      routerLink: ['/dashboard'],
    };

    menuService.currentPath.set('/library/1/books');
    fixture.detectChanges();

    expect(component.isRouteActive()).toBe(false);
  });

  it('wires drag handling through the shared shelf drag-drop service', () => {
    const dnd = TestBed.inject(BookShelfDragDropService) as unknown as {
      onShelfDragEnter: ReturnType<typeof vi.fn>;
    };
    component.item = {
      label: 'Shelf A',
      entityId: 12,
      dropEnabled: true,
      type: 'shelf',
    };

    fixture.detectChanges();
    const nativeElement = document.createElement('div');
    component.dropTarget = {
      nativeElement
    } as never;

    component.ngAfterViewInit();
    nativeElement.dispatchEvent(new Event('dragenter'));

    expect(dnd.onShelfDragEnter).toHaveBeenCalledWith(nativeElement, 12);
  });
});
