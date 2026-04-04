import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { AppLayoutComponent } from './app.layout.component';
import { AppMenuComponent } from '../layout-menu/app.menu.component';
import { AppTopBarComponent } from '../layout-topbar/app.topbar.component';
import { LocalStorageService } from '../../service/local-storage.service';
import { LayoutService } from '../layout.service';

@Component({
  selector: 'app-menu',
  standalone: true,
  template: '',
})
class StubMenuComponent {}

@Component({
  selector: 'app-topbar',
  standalone: true,
  template: '',
})
class StubTopBarComponent {}

@Component({
  standalone: true,
  template: '',
})
class DummyRouteComponent {}

describe('AppLayoutComponent', () => {
  let fixture: ComponentFixture<AppLayoutComponent>;
  let router: Router;
  let layoutService: LayoutService;

  const localStorageService = {
    get: vi.fn(() => 225),
  };

  beforeEach(async () => {
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
        imports: [AppMenuComponent, AppTopBarComponent],
      },
      add: {
        imports: [StubMenuComponent, StubTopBarComponent],
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
    layoutService.mobileSidebarOpen.set(true);
    fixture.detectChanges();

    const mask = fixture.nativeElement.querySelector('.layout-mask') as HTMLDivElement;
    mask.click();
    fixture.detectChanges();

    expect(layoutService.mobileSidebarOpen()).toBe(false);
  });

  it('toggles the body scroll lock with mobile sidebar state', () => {
    layoutService.mobileSidebarOpen.set(true);
    fixture.detectChanges();

    expect(document.body.classList.contains('blocked-scroll')).toBe(true);

    layoutService.mobileSidebarOpen.set(false);
    fixture.detectChanges();

    expect(document.body.classList.contains('blocked-scroll')).toBe(false);
  });

  it('closes the mobile sidebar on navigation', async () => {
    layoutService.mobileSidebarOpen.set(true);
    fixture.detectChanges();

    await router.navigateByUrl('/next');
    fixture.detectChanges();

    expect(layoutService.mobileSidebarOpen()).toBe(false);
  });
});
