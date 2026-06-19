import { FocusMonitor } from '@angular/cdk/a11y';
import { Overlay, type OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import {
  booleanAttribute,
  DestroyRef,
  Directive,
  effect,
  ElementRef,
  inject,
  input,
  numberAttribute,
  type ComponentRef,
  ViewContainerRef,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { connectedOverlayScrollStrategy, refreshOverlayRefPosition } from '../connected-overlay';
import { AppTooltipContainerComponent } from './app-tooltip.container';
import { type AppTooltipSide, tooltipPositions } from './app-tooltip.positions';

let nextId = 0;

@Directive({
  selector: '[appTooltip]',
  standalone: true,
  host: {
    '(mouseenter)': 'show()',
    '(mouseleave)': 'hide()',
    '(keydown.escape)': 'hide()',
  },
})
export class AppTooltipDirective {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly overlay = inject(Overlay);
  private readonly vcr = inject(ViewContainerRef);
  private readonly focusMonitor = inject(FocusMonitor);

  readonly text = input('', { alias: 'appTooltip' });
  readonly position = input<AppTooltipSide>('top', { alias: 'appTooltipPosition' });
  readonly disabled = input(false, { alias: 'appTooltipDisabled', transform: booleanAttribute });
  readonly showDelay = input(0, { alias: 'appTooltipShowDelay', transform: numberAttribute });
  readonly hideDelay = input(0, { alias: 'appTooltipHideDelay', transform: numberAttribute });

  private readonly id = `app-tooltip-${nextId++}`;
  private readonly portal = new ComponentPortal(AppTooltipContainerComponent, this.vcr);

  private overlayRef: OverlayRef | null = null;
  private containerRef: ComponentRef<AppTooltipContainerComponent> | null = null;
  private previousDescribedBy: string | null = null;
  private showTimer: ReturnType<typeof setTimeout> | null = null;
  private hideTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.focusMonitor
      .monitor(this.host)
      .pipe(takeUntilDestroyed())
      .subscribe((origin) => {
        if (origin === 'keyboard' || origin === 'program') this.show();
        else if (!origin) this.hide();
      });

    effect(() => {
      const text = this.text();
      if (this.disabled() || !text.trim()) {
        this.hide();
        return;
      }
      this.containerRef?.setInput('text', text);
    });

    inject(DestroyRef).onDestroy(() => {
      this.clearShowTimer();
      this.clearHideTimer();
      this.focusMonitor.stopMonitoring(this.host);
      this.detach();
    });
  }

  protected show(): void {
    if (this.disabled() || !this.text().trim()) return;
    this.clearHideTimer();
    if (this.containerRef || this.showTimer) return;
    const delay = this.showDelay();
    if (delay <= 0) {
      this.attach();
      return;
    }
    this.showTimer = setTimeout(() => {
      this.showTimer = null;
      this.attach();
    }, delay);
  }

  protected hide(): void {
    this.clearShowTimer();
    if (!this.containerRef || this.hideTimer) return;
    const delay = this.hideDelay();
    if (delay <= 0) {
      this.detach();
      return;
    }
    this.hideTimer = setTimeout(() => {
      this.hideTimer = null;
      this.detach();
    }, delay);
  }

  private attach(): void {
    const overlayRef = this.ensureOverlay();
    const container = overlayRef.attach(this.portal);
    this.containerRef = container;
    container.setInput('id', this.id);
    container.setInput('text', this.text());
    this.previousDescribedBy = this.host.nativeElement.getAttribute('aria-describedby');
    this.host.nativeElement.setAttribute('aria-describedby', this.describedByWithTooltip());

    container.changeDetectorRef.detectChanges();
    refreshOverlayRefPosition(overlayRef);
  }

  private ensureOverlay(): OverlayRef {
    if (this.overlayRef) return this.overlayRef;

    const positionStrategy = this.overlay
      .position()
      .flexibleConnectedTo(this.host)
      .withPositions(tooltipPositions(this.position()))
      .withPush(true)
      .withFlexibleDimensions(false)
      .withViewportMargin(8);

    this.overlayRef = this.overlay.create({
      positionStrategy,
      scrollStrategy: connectedOverlayScrollStrategy(this.overlay),
      panelClass: 'app-tooltip-overlay',
    });
    return this.overlayRef;
  }

  private detach(): void {
    if (this.containerRef) {
      this.containerRef = null;
      this.removeTooltipDescription();
      this.previousDescribedBy = null;
    }

    this.overlayRef?.dispose();
    this.overlayRef = null;
  }

  private describedByWithTooltip(): string {
    const ids = new Set((this.previousDescribedBy ?? '').split(/\s+/).filter(Boolean));
    ids.add(this.id);
    return Array.from(ids).join(' ');
  }

  private removeTooltipDescription(): void {
    const ids = (this.host.nativeElement.getAttribute('aria-describedby') ?? '')
      .split(/\s+/)
      .filter((id) => id && id !== this.id);

    if (ids.length) {
      this.host.nativeElement.setAttribute('aria-describedby', ids.join(' '));
      return;
    }

    this.host.nativeElement.removeAttribute('aria-describedby');
  }

  private clearShowTimer(): void {
    if (!this.showTimer) return;
    clearTimeout(this.showTimer);
    this.showTimer = null;
  }

  private clearHideTimer(): void {
    if (!this.hideTimer) return;
    clearTimeout(this.hideTimer);
    this.hideTimer = null;
  }
}
