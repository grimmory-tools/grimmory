import { Component, signal, viewChild } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';

import { LayoutService } from '../../layout/layout.service';
import { AppMenuComponent } from './app-menu.component';
import { AppMenuContentDirective } from './app-menu-content.directive';
import { AppMenuItemComponent } from './app-menu-item.component';
import { AppMenuCheckboxComponent } from './app-menu-checkbox.component';
import { AppMenuRadioComponent } from './app-menu-radio.component';
import { AppMenuRadioGroupComponent } from './app-menu-radio-group.component';
import { AppContextMenuDirective, AppMenuTriggerDirective } from './app-menu-trigger.directive';

@Component({
  standalone: true,
  imports: [
    AppMenuComponent,
    AppMenuItemComponent,
    AppMenuCheckboxComponent,
    AppMenuRadioGroupComponent,
    AppMenuRadioComponent,
    AppMenuContentDirective,
    AppMenuTriggerDirective,
    AppContextMenuDirective,
  ],
  template: `
    <button #trigger [appMenuTriggerFor]="menu">Open</button>
    <button class="second" (click)="openSecondFrom($event)">Second opener</button>
    <div class="zone" [appContextMenuFor]="menu">right-click</div>

    <app-menu #menu ariaLabel="Actions">
      <app-menu-item value="download" (selected)="onDownload()">Download</app-menu-item>
      <app-menu-item value="disabled" [disabled]="true" (selected)="onDisabled()">Disabled</app-menu-item>
      <app-menu-checkbox value="favourite" [(checked)]="fav" [(mixed)]="favMixed" (selected)="onFav($event)">Favourite</app-menu-checkbox>
      <app-menu-radio-group [(value)]="status">
        <app-menu-radio [value]="'read'">Read</app-menu-radio>
        <app-menu-radio [value]="'reading'">Reading</app-menu-radio>
      </app-menu-radio-group>
      <app-menu-item value="send" [submenu]="sub" (selected)="onSend()">Send</app-menu-item>
      <app-menu-item value="working" [loading]="busy()" (selected)="onBusy()">Working</app-menu-item>
      <app-menu-item value="keep-open" [closeOnSelect]="false" (selected)="onKeepOpen()">{{ keepOpenLabel() }}</app-menu-item>
      <app-menu-item value="books" [link]="'/books'" (selected)="onLink()">Go to books</app-menu-item>
    </app-menu>

    <app-menu #sub="ngMenu" ariaLabel="Send options">
      <ng-template appMenuContent>
        <app-menu-item value="quick-send" (selected)="onQuick()">Quick send</app-menu-item>
      </ng-template>
    </app-menu>

    <button class="lazy-trigger" [appMenuTriggerFor]="lazyMenu">Open lazy menu</button>
    <app-menu #lazyMenu ariaLabel="Lazy actions">
      <ng-template appMenuContent>
        <app-menu-item value="lazy-first">Lazy first</app-menu-item>
        <app-menu-item value="lazy-last">Lazy last</app-menu-item>
      </ng-template>
    </app-menu>
  `,
})
class HostComponent {
  readonly menuRef = viewChild.required(AppMenuComponent);
  readonly lazyMenuRef = viewChild.required<AppMenuComponent>('lazyMenu');

  openSecondFrom(event: MouseEvent): void {
    this.menuRef().open(event.currentTarget as HTMLElement);
  }
  readonly fav = signal(false);
  readonly favMixed = signal(false);
  readonly busy = signal(true);
  readonly keepOpenLabel = signal('Keep open');
  readonly status = signal<string | null>(null);
  readonly onDownload = vi.fn();
  readonly onDisabled = vi.fn();
  readonly onFav = vi.fn();
  readonly onSend = vi.fn();
  readonly onQuick = vi.fn();
  readonly onBusy = vi.fn();
  readonly onKeepOpen = vi.fn();
  readonly onLink = vi.fn();
}

function setup(isDesktop = signal(true)) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([{ path: 'books', children: [] }]),
      { provide: LayoutService, useValue: { isDesktop } },
    ],
  });
  const fixture = TestBed.createComponent(HostComponent);
  fixture.detectChanges();
  const host = fixture.nativeElement as HTMLElement;
  return { fixture, host };
}

function openMenu(isDesktop = signal(true)) {
  const { fixture, host } = setup(isDesktop);
  (host.querySelector('button') as HTMLButtonElement).click();
  fixture.detectChanges();
  return fixture;
}

function rootMenuEl(): HTMLElement {
  return document.querySelector('app-menu[aria-label="Actions"]') as HTMLElement;
}
function subMenuEl(): HTMLElement {
  return document.querySelector('app-menu[aria-label="Send options"]') as HTMLElement;
}
function isOpen(menu: HTMLElement): boolean {
  return !menu.classList.contains('hidden');
}
function itemByText(text: string): HTMLElement {
  const items = Array.from(document.querySelectorAll('app-menu-item'));
  return items.find((item) => item.textContent?.includes(text)) as HTMLElement;
}

describe('AppMenu', () => {
  it('creates opt-in lazy content on first open and keeps it for later opens', async () => {
    const { fixture, host } = setup();
    const trigger = host.querySelector('.lazy-trigger') as HTMLButtonElement;

    expect(fixture.componentInstance.lazyMenuRef().hasItems()).toBe(true);
    expect(document.querySelector('app-menu[aria-label="Lazy actions"] app-menu-item')).toBeNull();

    trigger.click();
    await fixture.whenStable();
    expect(itemByText('Lazy first')).not.toBeNull();

    trigger.click();
    await fixture.whenStable();
    expect(itemByText('Lazy first')).not.toBeNull();
  });

  it('focuses the requested lazy item after rendering', async () => {
    const { fixture, host } = setup();
    const trigger = host.querySelector('.lazy-trigger') as HTMLButtonElement;

    trigger.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true }));
    await fixture.whenStable();

    expect(itemByText('Lazy last').getAttribute('data-active')).toBe('true');
    expect(document.activeElement).toBe(itemByText('Lazy last'));
  });

  it('opens and closes via the trigger', () => {
    const { fixture, host } = setup();
    const trigger = host.querySelector('button') as HTMLButtonElement;

    expect(isOpen(rootMenuEl())).toBe(false);
    trigger.click();
    fixture.detectChanges();
    expect(isOpen(rootMenuEl())).toBe(true);
    expect(trigger.getAttribute('aria-expanded')).toBe('true');
    expect(trigger.getAttribute('aria-haspopup')).toBe('menu');

    trigger.click();
    fixture.detectChanges();
    expect(isOpen(rootMenuEl())).toBe(false);
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
  });

  it('emits selected once and closes on a regular item (click)', () => {
    const fixture = openMenu();
    const cmp = fixture.componentInstance;

    itemByText('Download').click();
    fixture.detectChanges();

    expect(cmp.onDownload).toHaveBeenCalledTimes(1);
    expect(isOpen(rootMenuEl())).toBe(false);
  });

  it('keeps disabled and loading items inert', () => {
    const fixture = openMenu();
    const cmp = fixture.componentInstance;

    const disabled = itemByText('Disabled');
    expect(disabled.getAttribute('aria-disabled')).toBe('true');
    disabled.click();
    itemByText('Working').click();
    fixture.detectChanges();

    expect(cmp.onDisabled).not.toHaveBeenCalled();
    expect(cmp.onBusy).not.toHaveBeenCalled();
    expect(isOpen(rootMenuEl())).toBe(true);
  });

  it('keeps the menu open for a closeOnSelect=false item', () => {
    const fixture = openMenu();
    const cmp = fixture.componentInstance;

    itemByText('Keep open').click();
    fixture.detectChanges();
    expect(cmp.onKeepOpen).toHaveBeenCalledTimes(1);
    expect(isOpen(rootMenuEl())).toBe(true);
  });

  it('renders a link item as a routerLink anchor and emits once on keyboard', async () => {
    const fixture = openMenu();
    const cmp = fixture.componentInstance;

    const linkItem = itemByText('Go to books');
    const anchor = linkItem.querySelector('a') as HTMLAnchorElement;
    expect(anchor.getAttribute('href')).toBe('/books');

    linkItem.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(cmp.onLink).toHaveBeenCalledTimes(1);
    expect(TestBed.inject(Router).url).toBe('/books');
    expect(isOpen(rootMenuEl())).toBe(false);
  });

  it('toggles a checkbox, keeps aria-checked and the menu open', () => {
    const fixture = openMenu();
    const cmp = fixture.componentInstance;

    const checkbox = document.querySelector('app-menu-checkbox') as HTMLElement;
    expect(checkbox.getAttribute('role')).toBe('menuitemcheckbox');
    expect(checkbox.getAttribute('aria-checked')).toBe('false');
    checkbox.click();
    fixture.detectChanges();

    expect(cmp.fav()).toBe(true);
    expect(cmp.onFav).toHaveBeenCalledWith(true);
    expect(checkbox.getAttribute('aria-checked')).toBe('true');
    expect(isOpen(rootMenuEl())).toBe(true);
  });

  it('clears the mixed state and becomes checked when activated', () => {
    const fixture = openMenu();
    const cmp = fixture.componentInstance;
    cmp.favMixed.set(true);
    fixture.detectChanges();

    const checkbox = document.querySelector('app-menu-checkbox') as HTMLElement;
    expect(checkbox.getAttribute('aria-checked')).toBe('mixed');
    checkbox.click();
    fixture.detectChanges();

    expect(cmp.favMixed()).toBe(false);
    expect(cmp.fav()).toBe(true);
    expect(checkbox.getAttribute('aria-checked')).toBe('true');
  });

  it('selects a radio, single-select, and closes', () => {
    const fixture = openMenu();
    const cmp = fixture.componentInstance;
    cmp.status.set('read');
    fixture.detectChanges();

    const radios = document.querySelectorAll('app-menu-radio');
    expect(radios[0].getAttribute('role')).toBe('menuitemradio');
    expect(radios[0].getAttribute('aria-checked')).toBe('true');
    (radios[1] as HTMLElement).click();
    fixture.detectChanges();

    expect(cmp.status()).toBe('reading');
    expect(radios[1].getAttribute('aria-checked')).toBe('true');
    expect(radios[0].getAttribute('aria-checked')).toBe('false');
    expect(isOpen(rootMenuEl())).toBe(false);
  });

  it('opens a submenu from its parent item and does not close the chain', () => {
    const fixture = openMenu();
    const cmp = fixture.componentInstance;

    const sendItem = itemByText('Send');
    expect(subMenuEl().querySelector('app-menu-item')).toBeNull();
    expect(sendItem.getAttribute('aria-haspopup')).toBe('true');
    sendItem.click();
    fixture.detectChanges();

    expect(itemByText('Quick send')).not.toBeUndefined();
    expect(isOpen(subMenuEl())).toBe(true);
    expect(cmp.onSend).not.toHaveBeenCalled();
    expect(isOpen(rootMenuEl())).toBe(true);
  });

  it('closes the submenu chain and restores focus after keyboard selection', () => {
    const { fixture, host } = setup();
    const cmp = fixture.componentInstance;
    const trigger = host.querySelector('button') as HTMLButtonElement;
    trigger.click();
    fixture.detectChanges();
    itemByText('Send').click();
    fixture.detectChanges();

    const leaf = itemByText('Quick send');
    leaf.focus();
    leaf.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    fixture.detectChanges();

    expect(cmp.onQuick).toHaveBeenCalledTimes(1);
    expect(isOpen(rootMenuEl())).toBe(false);
    expect(isOpen(subMenuEl())).toBe(false);
    expect(document.activeElement).toBe(trigger);
  });

  it('opens a submenu via keyboard ArrowRight', () => {
    const fixture = openMenu();
    const menuEl = rootMenuEl();
    for (let i = 0; i < 5; i++) {
      menuEl.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    }
    fixture.detectChanges();
    expect(itemByText('Send').getAttribute('data-active')).toBe('true');
    menuEl.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    expect(isOpen(subMenuEl())).toBe(true);
  });

  it('updates typeahead when projected label text changes', async () => {
    const fixture = openMenu();
    fixture.componentInstance.keepOpenLabel.set('Archive');
    await fixture.whenStable();

    rootMenuEl().dispatchEvent(new KeyboardEvent('keydown', { key: 'a', bubbles: true }));
    await fixture.whenStable();

    expect(itemByText('Archive').getAttribute('data-active')).toBe('true');
  });

  it('opens at coordinates and survives the auxclick fired when the right button releases', () => {
    const { fixture, host } = setup();
    const zone = host.querySelector('.zone') as HTMLElement;
    zone.dispatchEvent(new MouseEvent('contextmenu', { bubbles: true, clientX: 10, clientY: 20 }));
    fixture.detectChanges();
    expect(isOpen(rootMenuEl())).toBe(true);

    zone.dispatchEvent(new MouseEvent('auxclick', { bubbles: true, button: 2 }));
    fixture.detectChanges();
    expect(isOpen(rootMenuEl())).toBe(true);

    zone.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();
    expect(isOpen(rootMenuEl())).toBe(false);
  });

  it('moves to a second opener clicked while already open', () => {
    const { fixture, host } = setup();
    const trigger = host.querySelector('button') as HTMLButtonElement;
    trigger.click();
    fixture.detectChanges();
    expect(isOpen(rootMenuEl())).toBe(true);

    const second = host.querySelector('.second') as HTMLButtonElement;
    second.click();
    fixture.detectChanges();

    expect(isOpen(rootMenuEl())).toBe(true);
    expect(fixture.componentInstance.menuRef().openerElement()).toBe(second);
  });

  it('closes when focus moves outside the menu', () => {
    const fixture = openMenu();
    expect(isOpen(rootMenuEl())).toBe(true);

    const outside = document.querySelector('.second') as HTMLButtonElement;
    rootMenuEl().dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: outside }));
    fixture.detectChanges();
    expect(isOpen(rootMenuEl())).toBe(false);
  });

  it('closes on a trigger click even after focus moves back to the trigger', () => {
    const { fixture, host } = setup();
    const trigger = host.querySelector('button') as HTMLButtonElement;
    trigger.click();
    fixture.detectChanges();
    expect(isOpen(rootMenuEl())).toBe(true);

    rootMenuEl().dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: trigger }));
    fixture.detectChanges();
    trigger.click();
    fixture.detectChanges();
    expect(isOpen(rootMenuEl())).toBe(false);
  });

  it('closes when a router navigation starts', async () => {
    const fixture = openMenu();
    expect(isOpen(rootMenuEl())).toBe(true);

    await TestBed.inject(Router).navigateByUrl('/books');
    fixture.detectChanges();
    expect(isOpen(rootMenuEl())).toBe(false);
  });

  describe('sheet presentation (mobile shell)', () => {
    it('presents as a full-width bottom sheet with a scrim, dismissed by scrim tap', () => {
      const fixture = openMenu(signal(false));

      expect(isOpen(rootMenuEl())).toBe(true);
      expect(rootMenuEl().classList.contains('w-full')).toBe(true);
      const backdrop = document.querySelector('.app-menu-sheet-backdrop') as HTMLElement;
      expect(backdrop).not.toBeNull();

      backdrop.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      fixture.detectChanges();
      expect(isOpen(rootMenuEl())).toBe(false);
    });

    it('pushes a submenu over its parent and returns through the back row', () => {
      const fixture = openMenu(signal(false));

      itemByText('Send').click();
      fixture.detectChanges();
      fixture.detectChanges();

      const sub = subMenuEl();
      const root = rootMenuEl();
      expect(isOpen(sub)).toBe(true);
      expect(sub.parentElement).toBe(root.parentElement);
      expect(root.classList.contains('hidden')).toBe(true);
      const back = sub.querySelector('button') as HTMLButtonElement;
      expect(back.textContent).toContain('Send');

      back.click();
      fixture.detectChanges();
      fixture.detectChanges();

      expect(isOpen(subMenuEl())).toBe(false);
      expect(isOpen(rootMenuEl())).toBe(true);
      expect(rootMenuEl().classList.contains('hidden')).toBe(false);
    });

    it('selecting a pushed submenu leaf closes the whole sheet chain', () => {
      const fixture = openMenu(signal(false));
      const cmp = fixture.componentInstance;
      itemByText('Send').click();
      fixture.detectChanges();

      itemByText('Quick send').click();
      fixture.detectChanges();

      expect(cmp.onQuick).toHaveBeenCalledTimes(1);
      expect(isOpen(rootMenuEl())).toBe(false);
      expect(isOpen(subMenuEl())).toBe(false);
    });

    it('closes when the viewport crosses the mobile-shell breakpoint while open', () => {
      const isDesktop = signal(false);
      const fixture = openMenu(isDesktop);
      expect(isOpen(rootMenuEl())).toBe(true);

      isDesktop.set(true);
      fixture.detectChanges();
      expect(isOpen(rootMenuEl())).toBe(false);
    });
  });

  it('restores focus to the trigger when closed from within', () => {
    const { fixture, host } = setup();
    const trigger = host.querySelector('button') as HTMLButtonElement;
    trigger.click();
    fixture.detectChanges();

    const menuEl = rootMenuEl();
    itemByText('Download').focus();
    menuEl.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(isOpen(menuEl)).toBe(false);
    expect(document.activeElement).toBe(trigger);
  });
});
