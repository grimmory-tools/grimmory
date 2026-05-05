import { beforeEach, describe, expect, it, vi } from 'vitest';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AppMenuSectionComponent } from './app.menu-section.component';
import { LayoutService } from '../layout.service';

describe('AppMenuSectionComponent', () => {
  let fixture: ComponentFixture<AppMenuSectionComponent>;
  let component: AppMenuSectionComponent;

  const layoutService = {
    sidebarCollapsed: signal(false),
    isDesktop: signal(true),
    sidebarExpandedState: signal<Readonly<Record<string, boolean>>>({}),
    isSidebarExpanded: vi.fn((key: string, defaultExpanded: boolean) => {
      const value = layoutService.sidebarExpandedState()[key];
      return value === undefined ? defaultExpanded : value;
    }),
    setSidebarExpanded: vi.fn((key: string, expanded: boolean) => {
      layoutService.sidebarExpandedState.set({
        ...layoutService.sidebarExpandedState(),
        [key]: expanded,
      });
    }),
  };

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [AppMenuSectionComponent],
      providers: [{ provide: LayoutService, useValue: layoutService }],
    });

    TestBed.overrideComponent(AppMenuSectionComponent, { set: { template: '' } });

    fixture = TestBed.createComponent(AppMenuSectionComponent);
    component = fixture.componentInstance;

    layoutService.sidebarExpandedState.set({});
    layoutService.sidebarCollapsed.set(false);
    layoutService.isDesktop.set(true);
    layoutService.isSidebarExpanded.mockClear();
    layoutService.setSidebarExpanded.mockClear();
  });

  it('treats expandable sections as default-expanded', () => {
    fixture.componentRef.setInput('item', {
      id: 'libraries',
      menuKey: 'library',
      label: 'Libraries',
      expandable: true,
      items: [{ id: 'library-1', label: 'Library A', routerLink: ['/library/1/books'] }],
    });

    fixture.detectChanges();

    expect(component.isExpandable).toBe(true);
    expect(component.expanded).toBe(true);
  });

  it('persists expanded state through LayoutService when toggled', () => {
    fixture.componentRef.setInput('item', {
      id: 'libraries',
      menuKey: 'library',
      label: 'Libraries',
      expandable: true,
      items: [{ id: 'library-1', label: 'Library A' }],
    });

    fixture.detectChanges();
    component.toggleExpand();

    expect(layoutService.setSidebarExpanded).toHaveBeenCalledWith('library', false);
  });

  it('keeps children available while the sidebar is collapsed even if the saved section state is closed', () => {
    fixture.componentRef.setInput('item', {
      id: 'libraries',
      menuKey: 'library',
      label: 'Libraries',
      expandable: true,
      items: [{ id: 'library-1', label: 'Library A', routerLink: ['/library/1/books'] }],
    });

    layoutService.sidebarExpandedState.set({ 'library': false });
    layoutService.sidebarCollapsed.set(true);
    fixture.detectChanges();

    expect(component.expanded).toBe(false);
    expect(component.submenuVisible).toBe(true);
  });
});
