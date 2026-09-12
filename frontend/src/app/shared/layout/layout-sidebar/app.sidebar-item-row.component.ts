import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Tooltip } from '@openng/optimus-ui/tooltip';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideEllipsisVertical } from '@lucide/angular';
import {injectQuery} from '@tanstack/angular-query-experimental';
import {scopedFacetSelection} from '../../../features/book/browse/book-browse-scope';
import {BookQueryService} from '../../../features/book/data/book-query.service';
import {EMPTY_FACET_SELECTION, type FacetValueMap} from '../../../features/book/data/book-query-params';
import {AuthService} from '../../service/auth.service';
import { IconSelection, toIconSelection } from '../../icons/icon-selection';

import { IconDisplayComponent } from '../../components/icon-display/icon-display.component';
import { LayoutService } from '../layout.service';
import { SidebarLeaf } from '../navigation/nav-item.model';
import {LibraryShelfMenuComponent} from '../../../features/book/components/library-shelf-menu/library-shelf-menu.component';
import {AppMenuTriggerDirective} from '../../ui/menu/app-menu-trigger.directive';

@Component({
  // Attribute selector so the row renders into its host <li> inside a <ul>.
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: '[appSidebarItemRow]',
  templateUrl: './app.sidebar-item-row.component.html',
  styleUrls: ['./app.sidebar-item-row.component.scss'],
  imports: [
    RouterLink,
    LibraryShelfMenuComponent,
    AppMenuTriggerDirective,
    IconDisplayComponent,
    Tooltip,
    TranslocoPipe,
    LucideEllipsisVertical,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppSidebarItemRowComponent {
  readonly item = input.required<SidebarLeaf>();
  readonly index = input.required<number>();
  readonly parentKey = input.required<string>();

  readonly menuOpen = signal(false);
  readonly key = computed(() => `${this.parentKey()}-${this.index()}`);

  readonly layoutService = inject(LayoutService);
  private readonly bookQuery = inject(BookQueryService);
  private readonly authService = inject(AuthService);
  private readonly countFacets = computed<FacetValueMap | undefined>(() => {
    const scope = this.item().bookScope;
    return scope === undefined ? undefined : scopedFacetSelection(EMPTY_FACET_SELECTION, scope);
  });
  private readonly bookCountQuery = injectQuery(() => ({
    ...this.bookQuery.page({facets: this.countFacets() ?? {}, facetLogic: 'or', sort: [], size: 1}),
    enabled: this.countFacets() !== undefined && this.authService.isAuthenticated()
      && this.layoutService.areSidebarCountsVisible(this.parentKey()),
  }));
  readonly displayCount = computed(() => this.countFacets() === undefined
    ? this.item().bookCount
    : this.bookCountQuery.data()?.page.totalElements);

  readonly isRouteActive = computed(() => {
    const route = this.item().routerLink?.[0];
    if (!route) return false;
    return this.layoutService.currentPath() === route;
  });

  runAction(event: Event): void {
    event.preventDefault();
    this.item().action?.();
  }

  markContextMenuOpen(): void {
    this.menuOpen.set(true);
  }

  closeContextMenu(): void {
    this.menuOpen.set(false);
  }

  getIconSelection(): IconSelection | null {
    const item = this.item();
    if (!item.icon) return null;
    return toIconSelection(item.icon, item.iconType);
  }

  formatCount(count: number | null | undefined): string {
    if (!count) return '';
    if (count >= 1000) return Math.floor(count / 100) / 10 + 'K';
    return count.toString();
  }
}
