import { A11yModule } from '@angular/cdk/a11y';
import { Overlay, OverlayModule, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, ElementRef, inject, signal, TemplateRef, untracked, ViewContainerRef, viewChild } from '@angular/core';
import { NavigationStart, Router } from '@angular/router';
import { filter, take } from 'rxjs/operators';
import { FormsModule } from '@angular/forms';
import { TranslocoDirective, TranslocoPipe } from '@jsverse/transloco';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { CoverPlaceholderComponent } from '../../shared/components/cover-generator/cover-generator.component';
import { IconDisplayComponent } from '../../shared/components/icon-display/icon-display.component';
import { MOBILE_SHELL_ACTIVE_PROPERTY } from '../../shared/layout/layout.service';
import { PaletteItem } from './command-palette.model';
import { CommandPaletteService } from './command-palette.service';

@Component({
  selector: 'app-command-palette',
  standalone: true,
  imports: [A11yModule, OverlayModule, FormsModule, TranslocoDirective, TranslocoPipe, IconDisplayComponent, CoverPlaceholderComponent],
  templateUrl: './command-palette.component.html',
  styleUrl: './command-palette.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandPaletteComponent {
  protected readonly svc = inject(CommandPaletteService);
  private readonly overlay = inject(Overlay);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly vcRef = inject(ViewContainerRef);

  protected readonly paletteTpl = viewChild.required<TemplateRef<unknown>>('paletteTpl');
  protected readonly inputEl = viewChild<ElementRef<HTMLInputElement>>('paletteInput');

  protected readonly activeIndex = signal(0);
  protected readonly availableHeightPx = signal<number | null>(null);
  protected readonly activeItem = computed<PaletteItem | null>(() => {
    const items = this.svc.visibleItems();
    if (items.length === 0) return null;
    const idx = Math.min(this.activeIndex(), items.length - 1);
    return items[Math.max(0, idx)];
  });

  private overlayRef: OverlayRef | undefined;
  private focusAnimationFrameId: number | undefined;
  private focusTimeoutId: number | undefined;
  private viewportAnimationFrameId: number | undefined;
  private readonly mobileStateListener = () => this.scheduleViewportRefresh();
  private readonly visualViewportListener = () => this.scheduleViewportRefresh();

  constructor() {
    const unregisterOverlayController = this.svc.registerOverlayController({
      open: () => this.openOverlay(),
      close: () => this.closeOverlay(),
      focusInput: () => this.focusInputNow(),
    });

    this.destroyRef.onDestroy(() => {
      unregisterOverlayController();
      this.clearPendingFocus();
      this.closeOverlay();
    });

    this.router.events
      .pipe(
        filter((event): event is NavigationStart => event instanceof NavigationStart),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.svc.hide());

    effect(() => {
      this.svc.visibleItems();
      untracked(() => this.activeIndex.set(0));
    });

    effect(() => {
      const item = this.activeItem();
      if (!item) return;
      queueMicrotask(() => {
        const el = document.getElementById(this.itemDomId(item));
        el?.scrollIntoView({ block: 'nearest' });
      });
    });
  }

  protected itemDomId(item: PaletteItem): string {
    return `cp-${item.id.replace(/[^a-zA-Z0-9_-]/g, '_')}`;
  }

  protected onQueryChange(value: string): void {
    this.svc.query.set(value);
  }

  protected onKeydown(event: KeyboardEvent): void {
    const items = this.svc.visibleItems();

    switch (event.key) {
      case 'ArrowDown':
        if (items.length > 0) {
          event.preventDefault();
          this.activeIndex.update((i) => (i + 1) % items.length);
        }
        break;
      case 'ArrowUp':
        if (items.length > 0) {
          event.preventDefault();
          this.activeIndex.update((i) => (i - 1 + items.length) % items.length);
        }
        break;
      case 'Home':
        if (items.length > 0) {
          event.preventDefault();
          this.activeIndex.set(0);
        }
        break;
      case 'End':
        if (items.length > 0) {
          event.preventDefault();
          this.activeIndex.set(items.length - 1);
        }
        break;
      case 'Enter': {
        if (this.isMobileViewport()) {
          event.preventDefault();
          (event.target as HTMLElement | null)?.blur?.();
          break;
        }
        const active = this.activeItem();
        if (active) {
          event.preventDefault();
          this.svc.select(active);
        }
        break;
      }
      case 'Escape':
        event.preventDefault();
        this.svc.hide();
        break;
    }
  }

  protected onItemHover(item: PaletteItem): void {
    const items = this.svc.visibleItems();
    const idx = items.findIndex((candidate) => candidate.id === item.id);
    if (idx >= 0) {
      this.activeIndex.set(idx);
    }
  }

  protected groupLabelKey(kind: string): string {
    return `groups.${kind}`;
  }

  private openOverlay(): void {
    if (this.overlayRef) {
      this.focusInputNow();
      this.scheduleInputFocus();
      return;
    }

    this.bindMobileStateListener();
    this.bindVisualViewportListener();
    this.refreshAvailableHeight();
    this.overlayRef = this.overlay.create({
      positionStrategy: this.buildPositionStrategy(),
      scrollStrategy: this.overlay.scrollStrategies.block(),
      hasBackdrop: true,
      backdropClass: 'command-palette-backdrop',
      panelClass: 'command-palette-panel',
    });
    this.overlayRef.attach(new TemplatePortal(this.paletteTpl(), this.vcRef));
    this.overlayRef.backdropClick().pipe(take(1)).subscribe(() => this.svc.hide());
    this.focusInputNow();
    this.scheduleInputFocus();
  }

  private closeOverlay(): void {
    this.clearPendingFocus();
    this.clearPendingViewportRefresh();
    this.unbindMobileStateListener();
    this.unbindVisualViewportListener();
    this.availableHeightPx.set(null);
    this.overlayRef?.dispose();
    this.overlayRef = undefined;
  }

  private buildPositionStrategy() {
    const position = this.overlay.position().global();
    if (this.isMobileViewport()) {
      return position.left('0').top('0');
    }
    return position.centerHorizontally().top('15vh');
  }

  private applyPositionStrategy(): void {
    if (!this.overlayRef) return;
    this.overlayRef.updatePositionStrategy(this.buildPositionStrategy());
  }

  private isMobileViewport(): boolean {
    if (typeof window === 'undefined') return false;
    return window
      .getComputedStyle(document.documentElement)
      .getPropertyValue(MOBILE_SHELL_ACTIVE_PROPERTY)
      .trim() === '1';
  }

  private bindMobileStateListener(): void {
    if (typeof window === 'undefined') return;
    window.addEventListener('resize', this.mobileStateListener);
  }

  private unbindMobileStateListener(): void {
    if (typeof window === 'undefined') return;
    window.removeEventListener('resize', this.mobileStateListener);
  }

  private bindVisualViewportListener(): void {
    const vv = typeof window !== 'undefined' ? window.visualViewport : null;
    if (!vv) return;
    vv.addEventListener('resize', this.visualViewportListener);
    vv.addEventListener('scroll', this.visualViewportListener);
  }

  private unbindVisualViewportListener(): void {
    const vv = typeof window !== 'undefined' ? window.visualViewport : null;
    if (!vv) return;
    vv.removeEventListener('resize', this.visualViewportListener);
    vv.removeEventListener('scroll', this.visualViewportListener);
  }

  private refreshAvailableHeight(): void {
    // dvh doesn't shrink when the iOS virtual keyboard opens; visualViewport does.
    if (!this.isMobileViewport()) {
      this.availableHeightPx.set(null);
      return;
    }
    const vv = typeof window !== 'undefined' ? window.visualViewport : null;
    if (!vv) {
      this.availableHeightPx.set(null);
      return;
    }
    this.availableHeightPx.set(Math.max(0, Math.round(vv.height)));
  }

  private scheduleViewportRefresh(): void {
    if (typeof window === 'undefined' || this.viewportAnimationFrameId !== undefined) return;

    this.viewportAnimationFrameId = window.requestAnimationFrame(() => {
      this.viewportAnimationFrameId = undefined;
      this.applyPositionStrategy();
      this.refreshAvailableHeight();
    });
  }

  private clearPendingViewportRefresh(): void {
    if (typeof window !== 'undefined' && this.viewportAnimationFrameId !== undefined) {
      window.cancelAnimationFrame(this.viewportAnimationFrameId);
    }
    this.viewportAnimationFrameId = undefined;
  }

  private scheduleInputFocus(): void {
    this.clearPendingFocus();
    if (typeof window === 'undefined') return;

    // iOS Safari only raises the virtual keyboard when focus() lands after layout.
    const focusInput = () => this.focusInputNow();
    this.focusAnimationFrameId = window.requestAnimationFrame(focusInput);
    this.focusTimeoutId = window.setTimeout(focusInput, 60);
  }

  private focusInputNow(): void {
    const input = this.resolveInputElement();
    if (!input) return;

    try {
      input.focus({ preventScroll: true });
    } catch {
      input.focus();
    }

    const cursorPosition = input.value.length;
    input.setSelectionRange(cursorPosition, cursorPosition);
  }

  private resolveInputElement(): HTMLInputElement | null {
    const viewInput = this.inputEl()?.nativeElement;
    if (viewInput) return viewInput;

    const overlayInput = this.overlayRef?.overlayElement.querySelector('.command-palette-input');
    return overlayInput instanceof HTMLInputElement ? overlayInput : null;
  }

  private clearPendingFocus(): void {
    if (typeof window !== 'undefined' && this.focusAnimationFrameId !== undefined) {
      window.cancelAnimationFrame(this.focusAnimationFrameId);
    }
    if (typeof window !== 'undefined' && this.focusTimeoutId !== undefined) {
      window.clearTimeout(this.focusTimeoutId);
    }
    this.focusAnimationFrameId = undefined;
    this.focusTimeoutId = undefined;
  }
}
