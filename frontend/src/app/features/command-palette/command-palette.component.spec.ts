import { computed, signal } from '@angular/core';
import { OverlayContainer } from '@angular/cdk/overlay';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NavigationStart, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { getTranslocoModule } from '../../core/testing/transloco-testing';

import type { PaletteGroup, PaletteItem } from './command-palette.model';
import { CommandPaletteComponent } from './command-palette.component';
import { CommandPaletteService } from './command-palette.service';

function createItem(id: string, title: string): PaletteItem {
  return {
    id,
    kind: 'page',
    title,
    searchText: title.toLowerCase(),
  };
}

class MockCommandPaletteService {
  private readonly _isOpen = signal(false);
  private overlayController?: { open(): void; close(): void; focusInput(): void };
  readonly isOpen = this._isOpen.asReadonly();
  readonly query = signal('');
  readonly isSearching = signal(false);
  readonly items = signal<PaletteItem[]>([]);
  readonly groups = computed<PaletteGroup[]>(() =>
    this.query().trim()
      ? [{ kind: 'page', items: this.items() }]
      : []
  );
  readonly visibleItems = computed<PaletteItem[]>(() =>
    this.groups().flatMap((group) => group.items)
  );
  readonly select = vi.fn();
  readonly hide = vi.fn(() => {
    this.overlayController?.close();
    this._isOpen.set(false);
    this.query.set('');
  });

  registerOverlayController(controller: { open(): void; close(): void; focusInput(): void }): () => void {
    this.overlayController = controller;
    return () => {
      if (this.overlayController === controller) {
        this.overlayController = undefined;
      }
    };
  }

  open(): void {
    this.overlayController?.open();
    this._isOpen.set(true);
  }
}

describe('CommandPaletteComponent', () => {
  let fixture: ComponentFixture<CommandPaletteComponent>;
  let overlayContainer: OverlayContainer;
  let overlayRoot: HTMLElement;
  let service: MockCommandPaletteService;
  let routerEvents: Subject<unknown>;
  let originalScrollIntoView: PropertyDescriptor | undefined;
  let originalMatchMedia: PropertyDescriptor | undefined;

  beforeEach(() => {
    originalScrollIntoView = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollIntoView');
    originalMatchMedia = Object.getOwnPropertyDescriptor(window, 'matchMedia');
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
      configurable: true,
      writable: true,
      value: vi.fn(),
    });
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      writable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    });

    routerEvents = new Subject<unknown>();

    TestBed.configureTestingModule({
      imports: [CommandPaletteComponent, getTranslocoModule()],
      providers: [
        { provide: CommandPaletteService, useClass: MockCommandPaletteService },
        { provide: Router, useValue: { events: routerEvents.asObservable() } },
      ],
    });

    fixture = TestBed.createComponent(CommandPaletteComponent);
    overlayContainer = TestBed.inject(OverlayContainer);
    overlayRoot = overlayContainer.getContainerElement();
    service = TestBed.inject(CommandPaletteService) as unknown as MockCommandPaletteService;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture?.destroy();
    overlayRoot?.replaceChildren();
    document.querySelectorAll('.cdk-overlay-container').forEach((el) => el.remove());
    if (originalScrollIntoView) {
      Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', originalScrollIntoView);
    } else {
      delete (HTMLElement.prototype as { scrollIntoView?: unknown }).scrollIntoView;
    }
    if (originalMatchMedia) {
      Object.defineProperty(window, 'matchMedia', originalMatchMedia);
    } else {
      delete (window as { matchMedia?: unknown }).matchMedia;
    }
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('opens the overlay when the service opens and focuses the search input', async () => {
    service.query.set('dash');
    service.items.set([createItem('page:dashboard', 'Dashboard')]);
    service.open();

    await flushPalette(fixture);

    const input = overlayRoot.querySelector('.command-palette-input') as HTMLInputElement | null;
    expect(input).not.toBeNull();
    expect(document.activeElement).toBe(input);
  });

  it('closes the overlay on Escape', async () => {
    service.query.set('dash');
    service.items.set([createItem('page:dashboard', 'Dashboard')]);
    service.open();
    await flushPalette(fixture);

    const input = overlayRoot.querySelector('.command-palette-input') as HTMLInputElement;
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

    await flushPalette(fixture);

    expect(service.hide).toHaveBeenCalled();
    expect(overlayRoot.querySelector('.command-palette-input')).toBeNull();
  });

  it('wraps keyboard navigation through the result list', async () => {
    service.query.set('a');
    service.items.set([
      createItem('page:dashboard', 'Dashboard'),
      createItem('page:authors', 'Authors'),
    ]);
    service.open();
    await flushPalette(fixture);

    const input = overlayRoot.querySelector('.command-palette-input') as HTMLInputElement;
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true }));

    await flushPalette(fixture);

    const items = Array.from(overlayRoot.querySelectorAll('.command-palette-item'));
    expect(items[1].classList.contains('active')).toBe(true);
  });

  it('selects the highlighted item on Enter', async () => {
    const items = [
      createItem('page:dashboard', 'Dashboard'),
      createItem('page:authors', 'Authors'),
    ];
    service.query.set('a');
    service.items.set(items);
    service.open();
    await flushPalette(fixture);

    const input = overlayRoot.querySelector('.command-palette-input') as HTMLInputElement;
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    await flushPalette(fixture);
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));

    expect(service.select).toHaveBeenCalledWith(items[1]);
  });

  it('disposes the overlay when the component is destroyed while open', async () => {
    service.query.set('dash');
    service.items.set([createItem('page:dashboard', 'Dashboard')]);
    service.open();

    await flushPalette(fixture);

    expect(overlayRoot.querySelector('.command-palette-input')).not.toBeNull();

    fixture.destroy();

    expect(overlayRoot.querySelector('.command-palette-input')).toBeNull();
    fixture = null as unknown as ComponentFixture<CommandPaletteComponent>;
  });

  it('closes through the service when navigation starts', async () => {
    service.query.set('dash');
    service.items.set([createItem('page:dashboard', 'Dashboard')]);
    service.open();
    await flushPalette(fixture);

    routerEvents.next(new NavigationStart(1, '/dashboard'));
    await flushPalette(fixture);

    expect(service.hide).toHaveBeenCalled();
    expect(service.isOpen()).toBe(false);
    expect(service.query()).toBe('');
    expect(overlayRoot.querySelector('.command-palette-input')).toBeNull();
  });
});

async function flushPalette(fixture: ComponentFixture<CommandPaletteComponent>): Promise<void> {
  fixture.detectChanges();
  TestBed.flushEffects();
  await Promise.resolve();
  await Promise.resolve();
  fixture.detectChanges();
}
