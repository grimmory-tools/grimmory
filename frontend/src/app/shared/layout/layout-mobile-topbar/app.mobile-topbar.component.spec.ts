import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { getTranslocoModule } from '../../../core/testing/transloco-testing';
import { CommandPaletteService } from '../../../features/command-palette/command-palette.service';
import { LayoutService } from '../layout.service';
import { AppMobileTopbarComponent } from './app.mobile-topbar.component';

describe('AppMobileTopbarComponent', () => {
  let fixture: ComponentFixture<AppMobileTopbarComponent>;
  let component: AppMobileTopbarComponent;
  let layoutService: {
    closeMobileSidebar: ReturnType<typeof vi.fn>;
    onMenuToggle: ReturnType<typeof vi.fn>;
  };
  let commandPaletteService: {
    open: ReturnType<typeof vi.fn>;
    hide: ReturnType<typeof vi.fn>;
    toggle: ReturnType<typeof vi.fn>;
    isOpen: ReturnType<typeof signal>;
    query: ReturnType<typeof signal>;
    visibleItems: ReturnType<typeof signal>;
    groups: ReturnType<typeof signal>;
  };

  beforeEach(() => {
    layoutService = {
      closeMobileSidebar: vi.fn(),
      onMenuToggle: vi.fn(),
    };
    commandPaletteService = {
      open: vi.fn(),
      hide: vi.fn(),
      toggle: vi.fn(),
      isOpen: signal(false),
      query: signal(''),
      visibleItems: signal([]),
      groups: signal([]),
    };

    TestBed.configureTestingModule({
      imports: [AppMobileTopbarComponent, getTranslocoModule()],
      providers: [
        { provide: LayoutService, useValue: layoutService },
        { provide: CommandPaletteService, useValue: commandPaletteService },
      ],
    });

    fixture = TestBed.createComponent(AppMobileTopbarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('opens the command palette and closes the mobile sidebar when search is pressed', () => {
    component.openSearch();

    expect(layoutService.closeMobileSidebar).toHaveBeenCalledOnce();
    expect(commandPaletteService.open).toHaveBeenCalledOnce();
  });

  it('shows the search icon when the palette is closed', () => {
    commandPaletteService.isOpen.set(false);
    fixture.detectChanges();

    const icon = fixture.nativeElement.querySelector('.mobile-topbar button:last-of-type i');
    expect(icon?.classList.contains('pi-search')).toBe(true);
  });

  it('swaps to a close button that hides the palette when it is open', () => {
    commandPaletteService.isOpen.set(true);
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('.mobile-topbar button:last-of-type') as HTMLButtonElement;
    const icon = button.querySelector('i');
    expect(icon?.classList.contains('pi-times')).toBe(true);

    button.click();
    expect(commandPaletteService.hide).toHaveBeenCalledOnce();
  });
});
