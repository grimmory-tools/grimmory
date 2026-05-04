import { beforeEach, describe, expect, it, vi } from 'vitest';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppMenuItemRowComponent } from './app.menu-item-row.component';
import { UserService } from '../../../features/settings/user-management/user.service';
import { LayoutService } from '../layout.service';

describe('AppMenuItemRowComponent', () => {
  let fixture: ComponentFixture<AppMenuItemRowComponent>;
  let component: AppMenuItemRowComponent;

  const layoutService = {
    sidebarCollapsed: signal(false),
    currentPath: signal('/'),
  };

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [AppMenuItemRowComponent],
      providers: [
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
        { provide: LayoutService, useValue: layoutService },
      ],
    });

    TestBed.overrideComponent(AppMenuItemRowComponent, { set: { template: '' } });

    fixture = TestBed.createComponent(AppMenuItemRowComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('index', 0);
    fixture.componentRef.setInput('parentKey', 'home-0');
    layoutService.sidebarCollapsed.set(false);
  });

  function setItem(item: Parameters<ComponentFixture<AppMenuItemRowComponent>['componentRef']['setInput']>[1]): void {
    fixture.componentRef.setInput('item', item);
  }

  it('passes context menu items through to the template', () => {
    const editCommand = vi.fn();
    const nestedCommand = vi.fn();

    const item = {
      id: 'shelf-a',
      label: 'Shelf A',
      type: 'shelf',
      contextMenuItems: [
        { label: 'Edit', command: editCommand },
        { label: 'More', items: [{ label: 'Delete', command: nestedCommand }] },
      ],
    };
    setItem(item);

    fixture.detectChanges();

    const items = component.item().contextMenuItems!;
    expect(items[0].label).toBe('Edit');
    items[0].command?.({} as never);
    expect(editCommand).toHaveBeenCalled();

    expect(items[1].items?.[0].label).toBe('Delete');
    items[1].items?.[0].command?.({} as never);
    expect(nestedCommand).toHaveBeenCalled();
  });

  it('hides the context menu button for items without context menu actions', () => {
    setItem({ id: 'unshelved', label: 'Unshelved', type: 'shelf' });
    fixture.detectChanges();
    expect(component.shouldShowContextMenuButton()).toBe(false);
  });

  it('exposes admin and canManipulateLibrary as computed signals from UserService', () => {
    setItem({ id: 'test', label: 'Test' });
    fixture.detectChanges();
    expect(component.admin()).toBe(true);
    expect(component.canManipulateLibrary()).toBe(true);
  });

  it('reports route as active when currentPath matches item routerLink', () => {
    setItem({ id: 'dashboard', label: 'Dashboard', routerLink: ['/dashboard'] });
    layoutService.currentPath.set('/dashboard');
    fixture.detectChanges();
    expect(component.isRouteActive()).toBe(true);
  });

  it('reports route as inactive when currentPath does not match', () => {
    setItem({ id: 'dashboard', label: 'Dashboard', routerLink: ['/dashboard'] });
    layoutService.currentPath.set('/library/1/books');
    fixture.detectChanges();
    expect(component.isRouteActive()).toBe(false);
  });

});
