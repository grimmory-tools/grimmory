import {NgTemplateOutlet} from '@angular/common';
import {
  Component,
  computed,
  contentChild,
  Directive,
  effect,
  inject,
  input,
  TemplateRef,
  untracked,
  viewChild,
} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';

import {ArtworkRevealGroupDirective} from '../components/cover/artwork-reveal-group.directive';
import {AppPageHeaderComponent} from '../layout/page-header/app.page-header.component';
import {type PageHeader} from '../layout/page-header/page-header.service';
import {PageTitleService} from '../service/page-title.service';
import {AppButtonComponent} from '../ui/button/app-button.component';
import {cn} from '../ui/cn';
import {type BrowsePage} from './browse-page';
import {BrowseSelectModeControlsComponent} from './bulk-actions/select-mode-controls.component';
import {BrowseFilterChipsComponent} from './filter-chips/filter-chips.component';
import {BrowseFilterRailComponent} from './filter-rail/filter-rail.component';
import {BrowseGridComponent} from './grid/grid.component';
import {BrowseEmptyDef, BrowseGridItemDef, BrowseGridSkeletonDef} from './grid/grid.directives';
import {BrowseSearchInputComponent} from './search-input/search-input.component';
import {type BrowseViewMode} from './url-state';

export interface BrowsePageMessages {
  readonly empty: string;
  readonly filteredEmpty: string;
  readonly loadError: string;
  readonly pageError: string;
  readonly searchPlaceholder: string;
}

export interface BrowsePageTableContext {
  emptyState: TemplateRef<unknown>;
}

@Directive({
  selector: 'ng-template[appBrowsePageToolbar]',
})
export class BrowsePageToolbarDef {
  readonly templateRef = inject<TemplateRef<unknown>>(TemplateRef);
}

@Directive({
  selector: 'ng-template[appBrowsePageTable]',
})
export class BrowsePageTableDef {
  readonly templateRef = inject<TemplateRef<BrowsePageTableContext>>(TemplateRef);

  static ngTemplateContextGuard(_dir: BrowsePageTableDef, _ctx: unknown): _ctx is BrowsePageTableContext {
    return true;
  }
}

@Component({
  selector: 'app-browse-page',
  imports: [
    NgTemplateOutlet,
    TranslocoPipe,
    AppPageHeaderComponent,
    AppButtonComponent,
    BrowseGridComponent,
    BrowseGridItemDef,
    BrowseGridSkeletonDef,
    BrowseEmptyDef,
    BrowseSelectModeControlsComponent,
    BrowseFilterChipsComponent,
    BrowseFilterRailComponent,
    BrowseSearchInputComponent,
  ],
  hostDirectives: [ArtworkRevealGroupDirective],
  templateUrl: './browse-page.component.html',
})
export class BrowsePageComponent<Item extends {id: number}, FacetKey extends string, SortKey extends string> {
  private readonly pageTitle = inject(PageTitleService);

  readonly page = input.required<BrowsePage<Item, FacetKey, SortKey>>();
  readonly title = input.required<string>();
  readonly viewMode = input.required<BrowseViewMode>();
  readonly estimateItemHeight = input.required<(itemWidth: number) => number>();
  readonly messages = input.required<BrowsePageMessages>();
  readonly searchHint = input.required<string>();

  protected readonly toolbarDef = contentChild.required(BrowsePageToolbarDef);
  protected readonly tableDef = contentChild(BrowsePageTableDef);
  protected readonly itemDef = contentChild.required<BrowseGridItemDef<Item>>(BrowseGridItemDef);
  protected readonly skeletonDef = contentChild.required(BrowseGridSkeletonDef);

  private readonly grid = viewChild(BrowseGridComponent);
  private readonly headerRef = viewChild(AppPageHeaderComponent);

  protected readonly itemKey = (item: Item): number => item.id;
  protected readonly header = computed<PageHeader>(() => {
    const total = this.page().results.total();
    return {
      title: this.title(),
      count: total == null ? undefined : total.toLocaleString(),
    };
  });

  protected readonly layoutClasses = computed(() => {
    const page = 'app-page pb-0!';
    const row = 'flex items-start gap-7';
    const column = 'min-w-0 flex-1';
    const mobile = this.page().isMobile();

    if (mobile && this.viewMode() === 'table') {
      return {
        page: cn(page, 'h-[calc(100dvh-var(--mobile-topbar-height))] overflow-hidden'),
        row: cn(row, 'min-h-0 flex-1'),
        column: cn(column, 'flex min-h-0 flex-col h-full'),
      };
    }

    if (mobile) {
      return {page, row, column: cn(column, 'pb-8')};
    }

    return {
      page,
      row,
      column: cn(
        column,
        'flex min-h-0 flex-col h-[calc(100dvh-var(--page-stuck-offset,0px))]',
        this.viewMode() === 'table' && 'pb-4',
      ),
    };
  });
  protected readonly railClass = computed(() =>
    cn(
      'flex h-[calc(100dvh-var(--page-stuck-offset,0px))] w-[236px] shrink-0 flex-col',
      this.viewMode() === 'grid' && 'box-content border-l border-border/70 pl-6',
    ),
  );
  protected readonly gridBoxClass = computed(() =>
    cn('md:pb-8', !this.page().rail.visible() && 'md:-mr-8 md:pr-8'));
  protected readonly chipsBandClass = computed(() =>
    cn(
      'sticky top-[var(--page-stuck-offset,0px)] z-10 -mx-4 bg-page px-4',
      this.page().isMobile() ? 'pb-1.5' : 'pb-3',
      this.headerRef()?.isStuck() &&
        'shadow-[0_1px_0_0_color-mix(in_srgb,var(--color-border)_70%,transparent)]',
    ),
  );
  protected readonly hairlineStripClass = computed(() =>
    cn(
      'sticky top-[var(--page-stuck-offset,0px)] z-10 -mx-4 h-0 border-b',
      this.headerRef()?.isStuck() ? 'border-border/70' : 'border-transparent',
    ),
  );

  constructor() {
    effect(onCleanup => {
      onCleanup(this.page().attachView({
        scrollToTop: () => this.grid()?.scrollToTop(),
        densityGrid: () => this.grid()?.densityGrid() ?? null,
      }));
    });

    effect(() => {
      const range = this.grid()?.renderedRange();
      if (range) {
        untracked(() => this.page().results.onRenderedRange(range));
      }
    });

    effect(() => {
      const title = this.title();
      if (title) {
        this.pageTitle.setPageTitle(title);
      }
    });
  }
}
