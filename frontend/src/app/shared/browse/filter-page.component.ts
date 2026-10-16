import {Component, computed, inject, input} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {ActivatedRoute} from '@angular/router';
import {TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {LucideEllipsis} from '@lucide/angular';

import {AppPageHeaderComponent} from '../layout/page-header/app.page-header.component';
import {type PageHeader} from '../layout/page-header/page-header.service';
import {AppButtonComponent} from '../ui/button/app-button.component';
import {AppMenuComponent} from '../ui/menu/app-menu.component';
import {AppMenuTriggerDirective} from '../ui/menu/app-menu-trigger.directive';
import {BrowseFilterMenuItemsComponent} from './filter-menu-items.component';
import {type BrowseFilterPage} from './filter-page';
import {BrowseFilterRailComponent} from './filter-rail/filter-rail.component';
import {BrowseSearchInputComponent} from './search-input/search-input.component';

@Component({
  selector: 'app-browse-filter-page',
  imports: [
    TranslocoPipe,
    LucideEllipsis,
    AppButtonComponent,
    AppMenuComponent,
    AppMenuTriggerDirective,
    AppPageHeaderComponent,
    BrowseFilterMenuItemsComponent,
    BrowseFilterRailComponent,
    BrowseSearchInputComponent,
  ],
  template: `
    <div class="app-page pb-0!">
      <app-page-header [pageHeader]="pageHeader()">
        <div class="flex w-full items-center gap-2">
          <app-browse-search-input
            class="min-w-0 flex-1"
            [value]="page().stagedQuery()"
            [placeholder]="searchHint() | transloco"
            [ariaLabel]="searchHint() | transloco"
            [clearLabel]="'browse.rail.clearSearch' | transloco"
            (valueChange)="page().stagedQuery.set($event)"
            (cleared)="page().stagedQuery.set('')"
            (entered)="page().commit()" />
          <app-button
            variant="ghost"
            iconOnly
            [ariaLabel]="'browse.moreActions' | transloco"
            [appMenuTriggerFor]="moreMenu">
            <svg lucideEllipsis aria-hidden="true"></svg>
          </app-button>
          <app-menu #moreMenu [ariaLabel]="'browse.moreActions' | transloco">
            <app-browse-filter-menu-items
              [matchAll]="page().matchAll()"
              [excludeOnTick]="page().filterSettings.excludeOnTick()"
              (matchAllChange)="page().setMatchAll($event)"
              (excludeOnTickChange)="page().filterSettings.setExcludeOnTick($event)" />
          </app-menu>
        </div>
      </app-page-header>

      <div class="-mx-1.5 pb-28">
        @if (page().queries.ready()) {
          <app-browse-filter-rail
            alwaysShowBoxes
            [groups]="page().queries.railGroups()"
            [openKeys]="page().queries.openKeys()"
            [searchTerms]="page().queries.searchTerms()"
            [excludable]="page().filterSettings.excludeOnTick()"
            (toggleValue)="page().toggle($event)"
            (commitRange)="page().commitRange($event)"
            (openChange)="page().queries.setOpen($event)"
            (searchChange)="page().queries.setSearch($event)" />
        }
      </div>

      <div
        class="fixed inset-x-0 bottom-0 z-20 flex items-center gap-3 border-t border-border bg-page px-4 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
        <app-button
          variant="ghost"
          [label]="'browse.clearFilters' | transloco"
          [disabled]="!page().hasActiveFilters()"
          (clicked)="page().clear()" />
        <app-button
          tone="primary"
          fluid
          [label]="showLabelKey() | transloco: {count: page().previewTotal() ?? '…'}"
          [loading]="page().previewLoading()"
          (clicked)="page().commit()" />
      </div>
    </div>
  `,
})
export class BrowseFilterPageComponent<Item extends {id: number}, FacetKey extends string, SortKey extends string> {
  private readonly route = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);
  private readonly activeLang = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });

  readonly page = input.required<BrowseFilterPage<Item, FacetKey, SortKey>>();
  readonly title = input.required<string>();
  readonly searchHint = input.required<string>();
  readonly showLabelKey = input.required<string>();

  protected readonly pageHeader = computed<PageHeader>(() => {
    this.activeLang();
    return {
      title: this.transloco.translate('browse.filter'),
      breadcrumbs: [
        {
          label: this.title(),
          commands: ['/', ...this.route.snapshot.pathFromRoot.slice(0, -1).flatMap(route => route.url).map(segment => segment.path)],
          queryParamsHandling: 'preserve',
        },
        {label: this.transloco.translate('browse.filter')},
      ],
    };
  });
}
