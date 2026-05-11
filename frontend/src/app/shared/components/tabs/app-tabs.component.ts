import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  model,
  signal,
  viewChildren,
} from '@angular/core';
import {Tab as NgTab, TabList, Tabs} from '@angular/aria/tabs';

export interface TabItem {
  id: string;
  label: string;
  icon?: string;
}

export type TabsVariant = 'underline' | 'segmented';
export type TabsPlacement = 'inline' | 'below';

@Component({
  selector: 'app-tabs',
  standalone: true,
  imports: [Tabs, TabList, NgTab],
  templateUrl: './app-tabs.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class.variant-underline]': 'variant() === "underline"',
    '[class.variant-segmented]': 'variant() === "segmented"',
    '[class.placement-inline]': 'placement() === "inline"',
    '[class.placement-below]': 'placement() === "below"',
  },
})
export class AppTabsComponent {
  readonly tabs = input.required<TabItem[]>();
  readonly variant = input<TabsVariant>('underline');
  readonly placement = input<TabsPlacement>('below');
  readonly ariaLabel = input.required<string>();
  readonly selectedTabId = model<string | undefined>(undefined);

  private readonly destroyRef = inject(DestroyRef);
  private readonly tabDirectives = viewChildren(NgTab);

  protected readonly activeTabId = computed(() => this.selectedTabId() ?? this.tabs()[0]?.id);
  protected readonly indicatorLeft = signal(0);
  protected readonly indicatorWidth = signal(0);
  protected readonly indicatorVisible = signal(false);
  protected readonly indicatorAnimated = signal(false);

  private pendingAnimationFrame: number | null = null;
  private initialRevealScheduled = false;

  constructor() {
    let resizeObserver: ResizeObserver | null = null;

    afterRenderEffect(() => {
      this.tabs();
      this.variant();
      this.placement();
      this.activeTabId();
      this.scheduleMeasure();

      const tabs = this.tabDirectives();
      if (tabs.length && typeof ResizeObserver !== 'undefined') {
        resizeObserver ??= new ResizeObserver(() => this.scheduleMeasure());
        resizeObserver.disconnect();
        for (const tab of tabs) {
          resizeObserver.observe(tab.element);
        }
      }
    });

    this.destroyRef.onDestroy(() => {
      resizeObserver?.disconnect();
      if (this.pendingAnimationFrame !== null) {
        cancelAnimationFrame(this.pendingAnimationFrame);
      }
    });
  }

  private measure(): void {
    const activeTabId = this.activeTabId();
    const active = this.tabDirectives().find(tab => tab.value() === activeTabId);
    if (!active) {
      this.indicatorVisible.set(false);
      return;
    }
    this.indicatorLeft.set(active.element.offsetLeft);
    this.indicatorWidth.set(active.element.offsetWidth);
    if (!this.indicatorVisible()) {
      this.scheduleInitialReveal();
      return;
    }
    this.indicatorAnimated.set(true);
  }

  private scheduleMeasure(): void {
    if (this.pendingAnimationFrame !== null) {
      cancelAnimationFrame(this.pendingAnimationFrame);
    }

    this.pendingAnimationFrame = requestAnimationFrame(() => {
      this.pendingAnimationFrame = null;
      this.measure();
    });
  }

  private scheduleInitialReveal(): void {
    if (this.initialRevealScheduled) return;
    this.initialRevealScheduled = true;

    void document.fonts.ready.then(() => {
      if (this.pendingAnimationFrame !== null) {
        cancelAnimationFrame(this.pendingAnimationFrame);
      }
      this.pendingAnimationFrame = requestAnimationFrame(() => {
        this.measure();
        this.indicatorVisible.set(true);
        this.pendingAnimationFrame = requestAnimationFrame(() => {
          this.pendingAnimationFrame = null;
          this.indicatorAnimated.set(true);
        });
      });
    });
  }
}
