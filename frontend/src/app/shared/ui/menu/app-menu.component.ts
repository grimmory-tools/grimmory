import {
  afterNextRender,
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  computed,
  contentChild,
  contentChildren,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  Injector,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import {
  type ConnectedPosition,
  type FlexibleConnectedPositionStrategy,
  type FlexibleConnectedPositionStrategyOrigin,
  Overlay,
  type OverlayRef,
} from '@angular/cdk/overlay';
import { DomPortal } from '@angular/cdk/portal';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationStart, Router } from '@angular/router';
import { Menu, MenuItem } from '@angular/aria/menu';
import { LucideChevronLeft } from '@lucide/angular';
import { filter, type Subscription } from 'rxjs';

import { cn } from '../cn';
import {
  connectedOverlayPanelClass,
  connectedOverlayPositions,
  connectedOverlayScrollStrategy,
} from '../connected-overlay';
import { scrollLockStrategy } from '../scroll-lock';
import { LayoutService } from '../../layout/layout.service';
import {
  appMenuBackHeaderClass,
  appMenuBackLabelClass,
  appMenuBackRowClass,
  appMenuIconClass,
  appMenuPanelClass,
  appMenuScrollRegionClass,
  appMenuSeparatorClass,
  appMenuSheetPanelClass,
  appMenuSheetPaneClass,
} from './menu.styles';
import { AppMenuAriaItemDirective } from './menu-aria-item.directive';
import { AppMenuContentDirective } from './app-menu-content.directive';

const menuByElement = new WeakMap<Element, AppMenuComponent>();

export interface ContextMenuRequest {
  origin: HTMLElement;
  cursor: {x: number; y: number} | null;
  contextmenu: boolean;
}

export function contextMenuRequest(event: MouseEvent): ContextMenuRequest {
  const contextmenu = event.type === 'contextmenu';
  return {
    origin: event.currentTarget as HTMLElement,
    cursor: contextmenu && (event.clientX !== 0 || event.clientY !== 0)
      ? {x: event.clientX, y: event.clientY}
      : null,
    contextmenu,
  };
}

type MenuPresentation = 'popup' | 'sheet' | 'push';

const SUBMENU_GAP = 4;

const submenuOverlayPositions: ConnectedPosition[] = [
  { originX: 'end', originY: 'top', overlayX: 'start', overlayY: 'top', offsetX: SUBMENU_GAP },
  { originX: 'start', originY: 'top', overlayX: 'end', overlayY: 'top', offsetX: -SUBMENU_GAP },
  { originX: 'end', originY: 'bottom', overlayX: 'start', overlayY: 'bottom', offsetX: SUBMENU_GAP },
  { originX: 'start', originY: 'bottom', overlayX: 'end', overlayY: 'bottom', offsetX: -SUBMENU_GAP },
];

@Component({
  selector: 'app-menu',
  standalone: true,
  imports: [LucideChevronLeft],
  hostDirectives: [{ directive: Menu, inputs: ['wrap', 'typeaheadDelay', 'expansionDelay'] }],
  host: {
    '[class]': 'panelClass()',
    '[class.hidden]': 'concealed()',
    '[attr.aria-label]': 'ariaLabel()',
    '(keydown.escape)': 'onEscape($event)',
    '(focusout)': 'onFocusOut($event)',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (presentation() === 'push') {
      <div [class]="backHeaderClass">
        <button type="button" [class]="backRowClass" (click)="popPush($event)" (keydown)="onBackKeydown($event)">
          <svg lucideChevronLeft [class]="backIconClass" aria-hidden="true"></svg>
          <span [class]="backLabelClass">{{ parentLabel() }}</span>
        </button>
        <div role="separator" [class]="backSeparatorClass"></div>
      </div>
    }
    <div [class]="scrollRegionClass"><ng-content /></div>
  `,
})
export class AppMenuComponent {
  readonly ariaLabel = input.required<string>();
  readonly menuClass = input('');

  readonly opened = output<void>();
  readonly closed = output<void>();

  readonly menu = inject(Menu);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly overlay = inject(Overlay);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly layout = inject(LayoutService);
  private readonly lazyContent = contentChild(AppMenuContentDirective);
  private readonly items = contentChildren(AppMenuAriaItemDirective, { descendants: true });

  readonly opensUpward = signal(false);
  readonly hasItems = computed(() => this.lazyContent() !== undefined || this.ownItems().length > 0);

  private readonly openState = signal(false);
  private readonly opener = signal<HTMLElement | null>(null);
  private readonly pushedChild = signal<AppMenuComponent | null>(null);
  private anchor: FlexibleConnectedPositionStrategyOrigin | null = null;
  private contextZone: HTMLElement | null = null;
  private focusReturn: HTMLElement | null = null;
  private pendingFocus: 'first' | 'last' = 'first';
  private pendingContextGestureRelease = false;
  private closingHadFocus = false;

  private overlayRef: OverlayRef | null = null;
  private overlayIsSheet = false;
  private attachedAsPush = false;
  private pushOwner: AppMenuComponent | null = null;
  private pushReturnParent: Node | null = null;
  private pushReturnNext: Node | null = null;
  private portal: DomPortal | null = null;
  private outsideSub: Subscription | null = null;
  private positionSub: Subscription | null = null;

  private readonly isSubmenu = computed(() => this.menu.parent() instanceof MenuItem);
  protected readonly rendered = computed(() =>
    this.isSubmenu()
      ? this.menu.visible() && (this.parentPanel()?.isOpen() ?? false)
      : this.openState(),
  );

  private readonly isMobileShell = computed(() => !this.layout.isDesktop());
  protected readonly presentation = computed<MenuPresentation>(() => {
    if (!this.isMobileShell()) return 'popup';
    return this.isSubmenu() ? 'push' : 'sheet';
  });

  protected readonly concealed = computed(() => !this.rendered() || this.pushedChild() !== null);

  protected readonly panelClass = computed(() =>
    cn(appMenuPanelClass, this.menuClass(), this.presentation() !== 'popup' && appMenuSheetPanelClass),
  );

  protected readonly parentLabel = computed(() => {
    const parent = this.menu.parent();
    return parent instanceof MenuItem ? parent.searchTerm() : '';
  });

  protected readonly backHeaderClass = appMenuBackHeaderClass;
  protected readonly backRowClass = appMenuBackRowClass;
  protected readonly backIconClass = appMenuIconClass;
  protected readonly backLabelClass = appMenuBackLabelClass;
  protected readonly backSeparatorClass = appMenuSeparatorClass;
  protected readonly scrollRegionClass = appMenuScrollRegionClass;

  constructor() {
    menuByElement.set(this.host.nativeElement, this);

    inject(Router).events.pipe(
      filter((event): event is NavigationStart => event instanceof NavigationStart),
      takeUntilDestroyed(),
    ).subscribe(() => {
      if (!this.isSubmenu() && this.openState()) this.close();
    });

    this.installHostGuards();

    let wasOpen = false;
    effect(() => {
      const open = this.rendered();
      if (open === wasOpen) return;
      wasOpen = open;
      untracked(() => (open ? this.attachOverlay() : this.detachOverlay()));
    });

    effect(() => {
      const wantsSheet = this.presentation() === 'sheet';
      if (this.openState() && this.overlayRef?.hasAttached() && wantsSheet !== this.overlayIsSheet) {
        untracked(() => this.close());
      }
    });

    afterRenderEffect({
      mixedReadWrite: () => {
        this.items();
        if (this.rendered() && this.overlayRef?.hasAttached()) {
          this.overlayRef.updatePosition();
        }
      },
    });

    this.destroyRef.onDestroy(() => {
      this.outsideSub?.unsubscribe();
      this.positionSub?.unsubscribe();
      this.pushOwner?.pushedChild.set(null);
      this.overlayRef?.dispose();
    });
  }

  open(origin: HTMLElement, focus: 'first' | 'last' = 'first'): void {
    this.pendingFocus = focus;
    this.focusReturn = null;
    this.opener.set(origin);
    this.anchor = origin;
    this.contextZone = null;
    this.show();
  }

  openFrom(request: ContextMenuRequest): void {
    if (request.cursor) {
      this.openAt(request.cursor.x, request.cursor.y, request.origin);
    } else {
      this.open(request.origin);
    }
  }

  openAt(x: number, y: number, contextZone?: HTMLElement, fromPointer = true): void {
    this.pendingFocus = 'first';
    if (!this.openState()) {
      this.focusReturn = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    }
    this.opener.set(null);
    this.anchor = { x, y };
    this.contextZone = contextZone ?? null;
    this.pendingContextGestureRelease = fromPointer;
    this.show();
  }

  private show(): void {
    this.collapseChildren();
    this.openState.set(true);
    this.moveToAnchor();
  }

  private moveToAnchor(): void {
    const ref = this.overlayRef;
    if (!ref?.hasAttached() || !this.anchor || this.isSubmenu()) return;
    if (this.overlayIsSheet) return;
    (ref.getConfig().positionStrategy as FlexibleConnectedPositionStrategy).setOrigin(this.anchor);
    ref.updatePosition();
  }

  close(): void {
    if (this.isSubmenu()) {
      this.menu.parent()?.close?.();
      return;
    }
    this.closingHadFocus = this.chainHasFocus();
    this.collapseChildren();
    this.openState.set(false);
    if (!this.overlayRef?.hasAttached()) this.clearLogicalState();
  }

  isOpen(): boolean {
    return this.rendered();
  }

  openerElement(): HTMLElement | null {
    return this.opener();
  }

  protected onEscape(event: Event): void {
    if (!this.isSubmenu() && this.openState()) {
      event.stopPropagation();
      this.close();
    }
  }

  protected onFocusOut(event: FocusEvent): void {
    const next = event.relatedTarget;
    if (!(next instanceof Element) || !this.rendered()) return;
    if (this.host.nativeElement.contains(next)) return;
    if (this.opener()?.contains(next)) return;
    const menuEl = next.closest('app-menu');
    if (menuEl) {
      const other = menuByElement.get(menuEl);
      if (other && (this.containsMenu(other) || other.containsMenu(this))) return;
    }
    this.closeChain();
  }

  closeChain(): void {
    const parent = this.parentPanel();
    if (parent) {
      parent.closeChain();
    } else {
      this.close();
    }
  }

  protected popPush(event: Event): void {
    event.stopPropagation();
    this.close();
  }

  protected onBackKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ' ') event.stopPropagation();
  }

  private parentPanel(): AppMenuComponent | undefined {
    const parentItem: unknown = this.menu.parent();
    return parentItem instanceof AppMenuAriaItemDirective ? parentItem.owner : undefined;
  }

  private rootPanel(): AppMenuComponent {
    const parent = this.parentPanel();
    return parent ? parent.rootPanel() : this;
  }

  private sheetPane(): HTMLElement | null {
    return this.overlayIsSheet && this.overlayRef?.hasAttached() ? this.overlayRef.overlayElement : null;
  }

  private ownItems(): readonly AppMenuAriaItemDirective[] {
    return this.items().filter((item) => item.parent === this.menu);
  }

  private collapseChildren(): void {
    for (const item of this.ownItems()) {
      if (item.expanded()) item.close();
    }
  }

  private attachOverlay(): void {
    this.lazyContent()?.render();
    const origin = this.isSubmenu() ? (this.menu.parent() as MenuItem<unknown>).element : this.anchor;
    if (!origin) return;

    if (this.presentation() === 'push') {
      this.attachToSheetPane();
      return;
    }

    const sheet = this.presentation() === 'sheet';
    if (this.overlayRef && this.overlayIsSheet !== sheet) {
      this.positionSub?.unsubscribe();
      this.positionSub = null;
      this.overlayRef.dispose();
      this.overlayRef = null;
    }
    this.overlayIsSheet = sheet;
    this.overlayRef ??= sheet ? this.createSheetOverlay() : this.createPopupOverlay(origin);

    if (sheet) {
      this.opensUpward.set(true);
    } else {
      const strategy = this.overlayRef.getConfig().positionStrategy as FlexibleConnectedPositionStrategy;
      strategy.setOrigin(origin);
      this.positionSub ??= strategy.positionChanges.subscribe(change => {
        this.opensUpward.set(change.connectionPair.overlayY === 'bottom');
      });
    }

    this.portal ??= new DomPortal(this.host);
    if (!this.overlayRef.hasAttached()) this.overlayRef.attach(this.portal);
    this.overlayRef.updatePosition();

    if (!this.isSubmenu()) {
      this.outsideSub = sheet
        ? this.overlayRef.backdropClick().subscribe((event) => this.onOutsidePointer(event))
        : this.overlayRef.outsidePointerEvents().subscribe((event) => this.onOutsidePointer(event));
      afterNextRender(() => this.focusActiveItem(), { injector: this.injector });
    }
    this.opened.emit();
  }

  private attachToSheetPane(): void {
    const pane = this.rootPanel().sheetPane();
    if (!pane) return;
    const el = this.host.nativeElement;
    this.pushReturnParent = el.parentNode;
    this.pushReturnNext = el.nextSibling;
    pane.appendChild(el);
    this.pushOwner = this.parentPanel() ?? null;
    this.pushOwner?.pushedChild.set(this);
    this.attachedAsPush = true;
    this.opensUpward.set(true);
    afterNextRender(() => this.focusActiveItem(), { injector: this.injector });
    this.opened.emit();
  }

  private restorePushHost(): void {
    const parent = this.pushReturnParent;
    const next = this.pushReturnNext;
    this.pushReturnParent = null;
    this.pushReturnNext = null;
    if (!parent) return;
    if (next && next.parentNode === parent) {
      parent.insertBefore(this.host.nativeElement, next);
    } else {
      parent.appendChild(this.host.nativeElement);
    }
  }

  private createSheetOverlay(): OverlayRef {
    return this.overlay.create({
      scrollStrategy: scrollLockStrategy(),
      hasBackdrop: true,
      backdropClass: 'app-menu-sheet-backdrop',
      width: '100%',
      panelClass: appMenuSheetPaneClass.split(' '),
      positionStrategy: this.overlay.position().global().bottom('0'),
    });
  }

  private createPopupOverlay(origin: FlexibleConnectedPositionStrategyOrigin): OverlayRef {
    return this.overlay.create({
      scrollStrategy: connectedOverlayScrollStrategy(this.overlay),
      panelClass: connectedOverlayPanelClass.split(' '),
      usePopover: this.isSubmenu(),
      positionStrategy: this.overlay
        .position()
        .flexibleConnectedTo(origin)
        .withFlexibleDimensions(false)
        .withPopoverLocation(this.isSubmenu() ? 'inline' : 'global')
        .withPositions(this.isSubmenu() ? submenuOverlayPositions : connectedOverlayPositions),
    });
  }

  private clearLogicalState(): void {
    this.opener.set(null);
    this.contextZone = null;
    this.pendingContextGestureRelease = false;
    this.focusReturn = null;
  }

  private chainHasFocus(): boolean {
    const active = document.activeElement;
    if (!(active instanceof Element)) return false;
    if (this.host.nativeElement.contains(active)) return true;
    const menuEl = active.closest('app-menu');
    const other = menuEl ? menuByElement.get(menuEl) : undefined;
    return !!other && this.containsMenu(other);
  }

  private detachOverlay(): void {
    this.outsideSub?.unsubscribe();
    this.outsideSub = null;

    const hadFocus = this.chainHasFocus();
    this.overlayRef?.detach();

    if (this.attachedAsPush) {
      this.attachedAsPush = false;
      this.restorePushHost();
      this.pushOwner?.pushedChild.set(null);
      this.pushOwner = null;
      const parent = this.menu.parent();
      if (hadFocus && parent instanceof MenuItem) {
        const parentEl = parent.element;
        afterNextRender(() => {
          if (parentEl.getBoundingClientRect().height > 0) parentEl.focus();
        }, { injector: this.injector });
      }
    }

    if (!this.isSubmenu()) {
      const opener = this.opener();
      const focusReturn = this.focusReturn;
      const restoreFocus = this.closingHadFocus;
      this.clearLogicalState();
      if (restoreFocus && opener) {
        (opener.querySelector<HTMLElement>('button') ?? opener).focus();
      } else if (restoreFocus) {
        focusReturn?.focus();
      }
    }
    this.closed.emit();
  }

  private onOutsidePointer(event: MouseEvent): void {
    const target = event.target;
    if (!(target instanceof Element)) return;
    if (this.opener()?.contains(target)) return;
    const isContextGestureEvent =
      event.type === 'auxclick' ||
      event.type === 'contextmenu' ||
      (event.type === 'click' && event.ctrlKey);
    if (isContextGestureEvent && this.pendingContextGestureRelease) {
      this.pendingContextGestureRelease = false;
      return;
    }
    if (isContextGestureEvent && this.contextZone?.contains(target)) {
      return;
    }
    const menuEl = target.closest('app-menu');
    if (menuEl) {
      const inChain = menuByElement.get(menuEl);
      if (inChain && this.containsMenu(inChain)) return;
    }
    this.close();
  }

  private containsMenu(other: AppMenuComponent): boolean {
    let current: AppMenuComponent | undefined = other;
    while (current) {
      if (current === this) return true;
      current = current.parentPanel();
    }
    return false;
  }

  private focusActiveItem(): void {
    const active = this.host.nativeElement.querySelector<HTMLElement>('[tabindex="0"]');
    (active ?? this.host.nativeElement).focus({ preventScroll: true });
    const key = this.pendingFocus === 'last' ? 'End' : 'Home';
    this.host.nativeElement.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
    this.pendingFocus = 'first';
  }

  private installHostGuards(): void {
    const el = this.host.nativeElement;
    const keydownGuard = (event: KeyboardEvent) => {
      if ((event.key === 'Enter' || event.key === ' ') && event.target === el) event.stopPropagation();
    };
    const hoverGuard = (event: Event) => {
      if (this.isMobileShell()) event.stopPropagation();
    };
    el.addEventListener('keydown', keydownGuard, true);
    el.addEventListener('mouseover', hoverGuard, true);
    el.addEventListener('mouseout', hoverGuard, true);
    this.destroyRef.onDestroy(() => {
      el.removeEventListener('keydown', keydownGuard, true);
      el.removeEventListener('mouseover', hoverGuard, true);
      el.removeEventListener('mouseout', hoverGuard, true);
    });
  }
}
