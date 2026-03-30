import { beforeEach, describe, expect, it, vi } from 'vitest';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { AppMenuitemComponent } from './app.menuitem.component';
import { MenuService } from './service/app.menu.service';
import { DialogLauncherService } from '../../../services/dialog-launcher.service';
import { BookDialogHelperService } from '../../../../features/book/components/book-browser/book-dialog-helper.service';
import { UserService } from '../../../../features/settings/user-management/user.service';

describe('AppMenuitemComponent', () => {
  let fixture: ComponentFixture<AppMenuitemComponent>;
  let component: AppMenuitemComponent;

  const routerEvents = new Subject<unknown>();
  const menuSource = new Subject<{ key: string; routeEvent?: boolean }>();
  const menuReset = new Subject<void>();

  const router = {
    url: '/',
    events: routerEvents.asObservable(),
    isActive: vi.fn(() => false),
  };

  const menuService = {
    menuSource$: menuSource.asObservable(),
    resetSource$: menuReset.asObservable(),
    onMenuStateChange: vi.fn(),
  };

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [AppMenuitemComponent],
      providers: [
        { provide: Router, useValue: router },
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

  it('adapts app-owned context menu actions to Prime menu items', () => {
    const editAction = vi.fn();
    const nestedAction = vi.fn();

    component.item = {
      label: 'Shelf A',
      type: 'Shelf',
      contextMenuActions: [
        { label: 'Edit', action: editAction },
        {
          label: 'More',
          items: [{ label: 'Delete', action: nestedAction }],
        },
      ],
    };

    fixture.detectChanges();

    const items = component.contextMenuItems;
    expect(items[0].label).toBe('Edit');
    items[0].command?.({} as never);
    expect(editAction).toHaveBeenCalled();

    expect(items[1].items?.[0].label).toBe('Delete');
    items[1].items?.[0].command?.({} as never);
    expect(nestedAction).toHaveBeenCalled();
  });

  it('hides the context menu button for unshelved items', () => {
    component.item = {
      label: 'Unshelved',
      type: 'Shelf',
      contextMenuActions: [{ label: 'Edit' }],
    };

    fixture.detectChanges();

    expect(component.shouldShowContextMenuButton()).toBe(false);
  });
});
