import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { AppLayoutComponent } from './app.layout.component';
import { AppMenuComponent } from '../layout-menu/app.menu.component';
import { AppMobileTopbarComponent } from '../layout-mobile-topbar/app.mobile-topbar.component';
import { LocalStorageService } from '../../service/local-storage.service';
import { LayoutService } from '../layout.service';

@Component({
  selector: 'app-menu',
  standalone: true,
  template: '',
})
class StubMenuComponent {}

@Component({
  selector: 'app-mobile-topbar',
  standalone: true,
  template: '',
})
class StubMobileTopbarComponent {}

@Component({
  standalone: true,
  template: '',
})
class DummyRouteComponent {}

describe('AppLayoutComponent', () => {
  let fixture: ComponentFixture<AppLayoutComponent>;
  let router: Router;
  let layoutService: LayoutService;

  const localStorageValues: Record<string, unknown> = {};
  const localStorageService = {
    get: vi.fn((key: string) => localStorageValues[key]),
    set: vi.fn((key: string, value: unknown) => {
      localStorageValues[key] = value;
    }),
  };

  beforeEach(async () => {
    for (const key of Object.keys(localStorageValues)) {
      delete localStorageValues[key];
    }
    localStorageValues['sidebarWidth'] = 225;
    localStorageService.get.mockClear();
    localStorageService.set.mockClear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [
        AppLayoutComponent,
        TranslocoTestingModule.forRoot({ langs: {} }),
      ],
      providers: [
        provideRouter([
          { path: '', component: DummyRouteComponent },
          { path: 'next', component: DummyRouteComponent },
        ]),
        { provide: LocalStorageService, useValue: localStorageService },
      ],
    });

    TestBed.overrideComponent(AppLayoutComponent, {
      remove: {
        imports: [AppMenuComponent, AppMobileTopbarComponent],
      },
      add: {
        imports: [StubMenuComponent, StubMobileTopbarComponent],
      },
    });

    fixture = TestBed.createComponent(AppLayoutComponent);
    router = TestBed.inject(Router);
    layoutService = TestBed.inject(LayoutService);

    fixture.detectChanges();
    await router.navigateByUrl('/');
    fixture.detectChanges();
  });

  afterEach(() => {
    document.body.classList.remove('blocked-scroll');
    fixture?.destroy();
    vi.restoreAllMocks();
  });

  it('closes the mobile sidebar when the mask is clicked', () => {
    layoutService.mobileDrawerOpen.set(true);
    fixture.detectChanges();

    const mask = fixture.nativeElement.querySelector('.layout-mask') as HTMLDivElement;
    mask.click();
    fixture.detectChanges();

    expect(layoutService.mobileDrawerOpen()).toBe(false);
  });

  it('toggles the body scroll lock with mobile sidebar state', () => {
    layoutService.mobileDrawerOpen.set(true);
    fixture.detectChanges();

    expect(document.body.classList.contains('blocked-scroll')).toBe(true);

    layoutService.mobileDrawerOpen.set(false);
    fixture.detectChanges();

    expect(document.body.classList.contains('blocked-scroll')).toBe(false);
  });

  it('closes the mobile sidebar on navigation', async () => {
    layoutService.mobileDrawerOpen.set(true);
    fixture.detectChanges();

    await router.navigateByUrl('/next');
    fixture.detectChanges();

    expect(layoutService.mobileDrawerOpen()).toBe(false);
  });

  it('uses an explicit sidebar-hidden shell state when the desktop sidebar is hidden', () => {
    layoutService.isDesktop.set(true);
    layoutService.sidebarVisible.set(false);
    fixture.detectChanges();

    const wrapper = fixture.nativeElement.querySelector('.layout-wrapper') as HTMLDivElement;
    const sidebar = fixture.nativeElement.querySelector('.layout-sidebar') as HTMLElement;

    expect(wrapper.classList.contains('layout-sidebar-hidden')).toBe(true);
    expect(sidebar.getAttribute('aria-hidden')).toBe('true');
    expect(sidebar.hasAttribute('inert')).toBe(true);
  });

  it('keeps the mobile drawer reachable when the desktop sidebar preference is hidden', () => {
    layoutService.isDesktop.set(false);
    layoutService.sidebarVisible.set(false);
    layoutService.mobileDrawerOpen.set(true);
    fixture.detectChanges();

    const wrapper = fixture.nativeElement.querySelector('.layout-wrapper') as HTMLDivElement;
    const sidebar = fixture.nativeElement.querySelector('.layout-sidebar') as HTMLElement;

    expect(wrapper.classList.contains('layout-sidebar-hidden')).toBe(false);
    expect(sidebar.getAttribute('aria-hidden')).toBeNull();
    expect(sidebar.hasAttribute('inert')).toBe(false);
  });

  it('cleans up an active sidebar resize when the component is destroyed', () => {
    const component = fixture.componentInstance;

    layoutService.sidebarCollapsed.set(false);
    layoutService.isDesktop.set(true);
    component.startResize(new MouseEvent('mousedown', { clientX: 200 }));
    expect(document.body.classList.contains('layout-resizing-cursor')).toBe(true);

    fixture.destroy();
    document.dispatchEvent(new MouseEvent('mousemove', { clientX: 260 }));

    expect(document.body.classList.contains('layout-resizing-cursor')).toBe(false);
    expect(layoutService.sidebarWidth()).toBe(225);
  });

  it('supports keyboard resizing for the desktop sidebar handle', () => {
    layoutService.sidebarCollapsed.set(false);
    layoutService.isDesktop.set(true);
    layoutService.sidebarWidth.set(225);
    fixture.detectChanges();

    const handle = fixture.nativeElement.querySelector('.layout-sidebar-resize-handle') as HTMLDivElement;
    handle.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    fixture.detectChanges();

    expect(layoutService.sidebarWidth()).toBe(241);
    expect(localStorageService.set).not.toHaveBeenCalledWith('sidebarWidth', 241);

    handle.dispatchEvent(new KeyboardEvent('keyup', { key: 'ArrowRight' }));
    fixture.detectChanges();

    expect(localStorageService.set).toHaveBeenCalledWith('sidebarWidth', 241);
    expect(handle.getAttribute('role')).toBe('slider');
    expect(handle.getAttribute('tabindex')).toBe('0');
  });

  it('uses the collapsed rail width while preserving the stored expanded width', () => {
    layoutService.sidebarWidth.set(225);
    layoutService.sidebarCollapsed.set(true);
    layoutService.isDesktop.set(true);
    fixture.detectChanges();

    const wrapper = fixture.nativeElement.querySelector('.layout-wrapper') as HTMLDivElement;

    expect(document.documentElement.style.getPropertyValue('--sidebar-width')).toBe('var(--sidebar-collapsed-width)');
    expect(wrapper.style.getPropertyValue('--sidebar-stored-width')).toBe('225px');
  });
});
