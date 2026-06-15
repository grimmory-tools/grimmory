import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  input,
  model,
  signal,
  untracked,
  viewChild,
  viewChildren,
} from '@angular/core';
import { Tab as NgTab, TabList, Tabs } from '@angular/aria/tabs';
import { AppSelectComponent } from '../select/app-select.component';
import { type SelectOption } from '../select/app-select.options';
import { cn } from '../cn';
import {
  appTabsListVariants,
  appTabsRootVariants,
  appTabVariants,
  type TabsCollapse,
  type TabsPlacement,
  type TabsSize,
  type TabsVariant,
} from './app-tabs.variants';

export interface TabItem {
  id: string;
  label: string;
  icon?: string;
}

const COLLAPSE_HYSTERESIS = 8;

@Component({
  selector: 'app-tabs',
  standalone: true,
  imports: [Tabs, TabList, NgTab, AppSelectComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'relative block min-w-0' },
  template: `
    @if (tabs().length) {
      @if (collapsed()) {
        <app-select
          class="block w-full"
          [options]="collapsedOptions()"
          [value]="activeTabId() ?? null"
          (valueChange)="selectTab($event)"
          [ariaLabel]="ariaLabel()" />
      }
      <div
        data-tabs-row
        [class]="rowClass()"
        [attr.aria-hidden]="collapsed() ? 'true' : null"
        [attr.inert]="collapsed() ? '' : null">
        <div ngTabs [class]="rootClass()">
          <div
            #tabList
            ngTabList
            [class]="listClass()"
            [selectedTab]="activeTabId()"
            (selectedTabChange)="selectTab($event)"
            [attr.aria-label]="ariaLabel()">
            <span
              aria-hidden="true"
              [class]="indicatorClass()"
              [style.transform]="'translateX(' + indicatorLeft() + 'px)'"
              [style.width.px]="indicatorWidth()"></span>
            @for (tab of tabs(); track tab.id) {
              <button ngTab type="button" [value]="tab.id" [class]="tabClass()">
                @if (tab.icon) {
                  <i [class]="tab.icon + ' shrink-0 text-[0.875em] leading-none'" aria-hidden="true"></i>
                }
                <span class="leading-none">{{ tab.label }}</span>
              </button>
            }
          </div>
        </div>
      </div>
    }
  `,
})
export class AppTabsComponent {
  readonly tabs = input.required<readonly TabItem[]>();
  readonly variant = input<TabsVariant>('underline');
  readonly size = input<TabsSize>('md');
  readonly placement = input<TabsPlacement>('inline');
  readonly collapse = input<TabsCollapse>('auto');
  readonly ariaLabel = input.required<string>();
  readonly selectedTabId = model<string | undefined>(undefined);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly tabDirectives = viewChildren(NgTab);
  private readonly tabListRef = viewChild<unknown, ElementRef<HTMLElement>>('tabList', { read: ElementRef });

  protected readonly activeTabId = computed(() => this.resolveTabId(this.selectedTabId()));
  protected readonly collapsedOptions = computed<SelectOption<string>[]>(() =>
    this.tabs().map((tab) => ({ label: tab.label, value: tab.id })),
  );
  protected readonly collapsed = signal(false);
  protected readonly indicatorLeft = signal(0);
  protected readonly indicatorWidth = signal(0);
  private readonly indicatorReady = signal(false);

  protected readonly rowClass = computed(() =>
    this.collapsed() ? 'pointer-events-none invisible absolute left-0 top-0 w-max max-w-full overflow-hidden' : 'block',
  );
  protected readonly rootClass = computed(() => appTabsRootVariants({ placement: this.placement() }));
  protected readonly listClass = computed(() => appTabsListVariants({ variant: this.variant() }));
  protected readonly tabClass = computed(() => appTabVariants({ variant: this.variant(), size: this.size() }));
  protected readonly indicatorClass = computed(() => {
    const animated = this.indicatorReady() && 'transition-[transform,width] duration-200 ease-out';
    return this.variant() === 'segmented'
      ? cn(
          'pointer-events-none absolute inset-y-1 left-0 z-0 rounded-md border border-primary/15 ' +
            'bg-primary/10 shadow-control dark:border-primary/30',
          animated,
        )
      : cn('pointer-events-none absolute bottom-0 left-0 h-0.5 rounded-t-[2px] bg-primary', animated);
  });

  constructor() {
    let resizeObserver: ResizeObserver | null = null;

    afterRenderEffect(() => {
      this.tabs();
      this.variant();
      this.size();
      this.placement();
      this.collapse();
      this.activeTabId();
      this.measureLayout();

      if (typeof ResizeObserver === 'undefined') return;
      resizeObserver ??= new ResizeObserver(() => this.measureLayout());
      resizeObserver.disconnect();
      resizeObserver.observe(this.host.nativeElement);
      for (const tab of this.tabDirectives()) {
        resizeObserver.observe(tab.element);
      }
    });

    this.destroyRef.onDestroy(() => resizeObserver?.disconnect());
  }

  protected selectTab(value: string | null | undefined): void {
    this.selectedTabId.set(this.resolveTabId(value ?? undefined));
  }

  private measureLayout(): void {
    this.measure();
    this.measureFit();
  }

  private measureFit(): void {
    const mode = this.collapse();
    if (mode !== 'auto') {
      this.collapsed.set(mode === 'always');
      return;
    }
    const list = this.tabListRef()?.nativeElement;
    if (!list) return;
    const available = this.host.nativeElement.clientWidth;
    const content = list.scrollWidth;
    const collapsed = untracked(this.collapsed);
    if (!collapsed) {
      if (content > available + 1) this.collapsed.set(true);
    } else if (content <= available - COLLAPSE_HYSTERESIS) {
      this.collapsed.set(false);
    }
  }

  private measure(): void {
    const active = this.tabDirectives().find((tab) => tab.value() === this.activeTabId());
    if (!active) return;
    this.indicatorLeft.set(active.element.offsetLeft);
    this.indicatorWidth.set(active.element.offsetWidth);
    if (!this.indicatorReady() && typeof requestAnimationFrame !== 'undefined') {
      requestAnimationFrame(() => this.indicatorReady.set(true));
    }
  }

  private resolveTabId(value: string | undefined): string | undefined {
    const tabs = this.tabs();
    return tabs.some((tab) => tab.id === value) ? value : tabs[0]?.id;
  }
}
