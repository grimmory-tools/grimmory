import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  computed,
  contentChild,
  DestroyRef,
  ElementRef,
  inject,
  input,
  output,
  signal,
  TemplateRef,
  viewChild,
} from '@angular/core';
import {NgTemplateOutlet} from '@angular/common';
import {TranslocoPipe} from '@jsverse/transloco';
import {cn} from '../../ui/cn';
import {AppTabsComponent} from '../../ui/tabs/app-tabs.component';
import {AppPageHeaderBreadcrumbsComponent} from './app.page-header-breadcrumbs.component';
import {PageHeaderService, type PageHeader} from './page-header.service';
import {LayoutService} from '../layout.service';

@Component({
  selector: 'app-page-header',
  standalone: true,
  imports: [
    AppPageHeaderBreadcrumbsComponent,
    AppTabsComponent,
    NgTemplateOutlet,
    TranslocoPipe,
  ],
  templateUrl: './app.page-header.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {class: 'contents'},
})
export class AppPageHeaderComponent {
  private readonly layoutService = inject(LayoutService);
  private readonly pageHeaderService = inject(PageHeaderService);
  private readonly destroyRef = inject(DestroyRef);

  readonly isMobile = computed(() => !this.layoutService.isDesktop());
  private readonly stuck = signal(false);

  readonly pageHeader = input<PageHeader | null>(null);
  readonly tabChange = output<string>();

  readonly leadMedia = contentChild<TemplateRef<unknown>>('pageHeaderLead');
  readonly meta = contentChild<TemplateRef<unknown>>('pageHeaderMeta');
  readonly breadcrumbs = computed(() => this.pageHeader()?.breadcrumbs ?? []);
  readonly hasTitleBlock = computed(
    () => !!this.pageHeader()?.title || !!this.leadMedia() || !!this.meta(),
  );
  readonly leadVariant = computed<'none' | 'trail' | 'title' | 'stacked'>(() => {
    const trail = this.breadcrumbs().length > 0;
    const title = this.hasTitleBlock();
    if (trail && title) return 'stacked';
    if (trail) return 'trail';
    if (title) return 'title';
    return 'none';
  });
  readonly leadInBar = computed(() => this.leadVariant() !== 'none' && !this.isMobile());
  readonly leadAboveBar = computed(() => this.leadVariant() !== 'none' && this.isMobile());
  readonly trailOwnRow = computed(() => this.leadInBar() && this.leadVariant() === 'stacked');
  readonly hasTabs = computed(() => (this.pageHeader()?.tabs?.length ?? 0) > 0);
  readonly barCollapsible = computed(() => !this.leadInBar() && !this.hasTabs());
  readonly activeTabId = computed(() => this.pageHeader()?.selectedTabId);

  readonly leadAboveClass = computed(() =>
    cn(
      this.leadVariant() === 'trail' && 'flex items-center',
      this.leadVariant() === 'stacked' && 'flex flex-col gap-0.5',
      this.leadVariant() === 'title' ? 'pt-5' : 'pt-3',
    ),
  );
  readonly leadPrincipalClass = computed(() =>
    cn(
      'grow-[999] basis-0',
      this.leadVariant() === 'trail' ? 'flex items-center min-w-[14rem]' : 'min-w-[20rem]',
    ),
  );
  readonly rowClass = computed(() => {
    const collapseWhenEmpty = this.leadInBar() ? null : 'has-[>.app-page-header-controls:empty]:hidden';
    if (this.isMobile()) return cn('flex min-w-0 items-center py-2.5', collapseWhenEmpty);
    const pbForTabs = this.hasTabs() ? 'pb-2' : null;
    switch (this.leadVariant()) {
      case 'trail':
        return cn('flex flex-wrap items-center gap-x-6 gap-y-2 py-5', pbForTabs, collapseWhenEmpty);
      case 'stacked':
        return cn('flex flex-wrap items-start gap-x-6 gap-y-4 pt-0.5 pb-5', pbForTabs, collapseWhenEmpty);
      default:
        return cn('flex flex-wrap items-start gap-x-6 gap-y-4 pt-7', pbForTabs ?? 'pb-6', collapseWhenEmpty);
    }
  });
  readonly stickyRegionClass = computed(() =>
    cn(
      'app-page-header-sticky-region sticky z-30 bg-page',
      !this.hasTabs() && this.stuck() && 'border-b border-border/70',
      this.isMobile() ? 'top-[var(--mobile-topbar-height)]' : 'top-0',
      this.barCollapsible() && 'has-[.app-page-header-controls:empty]:hidden',
    ),
  );

  private readonly leadAboveRef = viewChild<ElementRef<HTMLElement>>('leadAbove');
  private readonly stickyRegionRef = viewChild<ElementRef<HTMLElement>>('stickyRegion');
  private readonly stuckSentinelRef = viewChild<ElementRef<HTMLElement>>('stuckSentinel');

  constructor() {
    const handle = this.pageHeaderService.registerRenderedHeader(this.pageHeader);
    this.destroyRef.onDestroy(() => this.pageHeaderService.release(handle));

    let leadObserver: IntersectionObserver | null = null;
    let stuckObserver: IntersectionObserver | null = null;

    afterRenderEffect(() => {
      this.isMobile();
      const lead = this.leadAboveRef()?.nativeElement;
      const sentinel = this.stuckSentinelRef()?.nativeElement;
      leadObserver?.disconnect();
      stuckObserver?.disconnect();

      if (typeof IntersectionObserver === 'undefined') {
        this.pageHeaderService.setLeadVisible(true);
        this.stuck.set(false);
        return;
      }

      const regionTop = this.stickyRegionRef() ? getComputedStyle(this.stickyRegionRef()!.nativeElement).top : '';
      const topbarOffset = /^\d+(\.\d+)?px$/.test(regionTop) ? regionTop : '0px';
      const rootMargin = `-${topbarOffset} 0px 0px 0px`;

      if (lead) {
        leadObserver = new IntersectionObserver(
          ([entry]) => this.pageHeaderService.setLeadVisible(entry.isIntersecting),
          {rootMargin},
        );
        leadObserver.observe(lead);
      } else {
        this.pageHeaderService.setLeadVisible(true);
      }

      if (sentinel) {
        stuckObserver = new IntersectionObserver(
          ([entry]) => this.stuck.set(!entry.isIntersecting),
          {rootMargin},
        );
        stuckObserver.observe(sentinel);
      } else {
        this.stuck.set(false);
      }
    });

    this.destroyRef.onDestroy(() => {
      leadObserver?.disconnect();
      stuckObserver?.disconnect();
    });

    let sizeObserver: ResizeObserver | null = null;
    afterRenderEffect(() => {
      this.isMobile();
      const region = this.stickyRegionRef()?.nativeElement;
      sizeObserver?.disconnect();
      if (!region || typeof ResizeObserver === 'undefined') return;
      const target = region.closest<HTMLElement>('.app-page') ?? region;
      const publish = () => {
        const top = parseFloat(getComputedStyle(region).top) || 0;
        target.style.setProperty('--page-stuck-offset', `${top + region.offsetHeight}px`);
      };
      sizeObserver = new ResizeObserver(publish);
      sizeObserver.observe(region, {box: 'border-box'});
      publish();
    });
    this.destroyRef.onDestroy(() => sizeObserver?.disconnect());
  }

  onSelectedTabChange(tabId: string | undefined): void {
    const header = this.pageHeader();
    if (!header || tabId === undefined || tabId === header.selectedTabId) return;
    this.tabChange.emit(tabId);
  }
}
