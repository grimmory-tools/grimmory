import {Component, computed, inject, viewChild} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {LucideEllipsis, LucideFunnel} from '@lucide/angular';

import {createBrowsePage} from '../../../shared/browse/browse-page';
import {
  BrowsePageComponent,
  BrowsePageToolbarDef,
  type BrowsePageMessages,
} from '../../../shared/browse/browse-page.component';
import {BrowseDensityMenuItemsComponent} from '../../../shared/browse/density-menu-items.component';
import {BrowseFilterMenuItemsComponent} from '../../../shared/browse/filter-menu-items.component';
import {BrowseGridItemDef, BrowseGridSkeletonDef} from '../../../shared/browse/grid/grid.directives';
import {BrowseSortControlComponent} from '../../../shared/browse/sort-control.component';
import {createBrowseUrlState} from '../../../shared/browse/url-state';
import {LocalStorageService} from '../../../shared/service/local-storage.service';
import {UrlHelperService} from '../../../shared/service/url-helper.service';
import {AppButtonComponent} from '../../../shared/ui/button/app-button.component';
import {AppMenuCheckboxComponent} from '../../../shared/ui/menu/app-menu-checkbox.component';
import {AppMenuSectionComponent} from '../../../shared/ui/menu/app-menu-section.component';
import {AppMenuSeparatorComponent} from '../../../shared/ui/menu/app-menu-separator.component';
import {AppMenuTriggerDirective} from '../../../shared/ui/menu/app-menu-trigger.directive';
import {AppMenuComponent} from '../../../shared/ui/menu/app-menu.component';
import {ScalePreference} from '../../../shared/util/scale-preference.util';
import {AuthorCardSkeletonComponent} from '../components/author-card-skeleton.component';
import {AuthorCardComponent, authorCardPhotoSrc} from '../components/author-card.component';
import {AuthorMenuComponent} from '../components/author-menu.component';
import {authorCardHeightForWidth} from '../components/author-card.layout';
import {
  DEFAULT_AUTHOR_SORT_TERMS,
  type AuthorQueryFacetKey,
  type AuthorQuerySortKey,
} from '../data/author-query-params';
import {AuthorQueryService} from '../data/author-query.service';
import {type AuthorSummary} from '../data/author-response.models';
import {AuthorBrowseBulkBarComponent} from './author-browse-bulk-bar.component';
import {AUTHOR_FIELDS} from './author-browse-fields';
import {createAuthorBrowseLabels} from './author-browse-labels';

const AUTHOR_BROWSE_MESSAGES: BrowsePageMessages = {
  empty: 'browse.author.empty',
  filteredEmpty: 'browse.author.filteredEmpty',
  loadError: 'browse.author.loadError',
  pageError: 'browse.author.pageError',
  searchPlaceholder: 'browse.rail.searchAuthors',
};

@Component({
  selector: 'app-author-browse-page',
  imports: [
    TranslocoPipe,
    LucideEllipsis,
    LucideFunnel,
    AppButtonComponent,
    AppMenuComponent,
    AppMenuCheckboxComponent,
    AppMenuSectionComponent,
    AppMenuSeparatorComponent,
    AppMenuTriggerDirective,
    BrowsePageComponent,
    BrowsePageToolbarDef,
    BrowseGridItemDef,
    BrowseGridSkeletonDef,
    BrowseSortControlComponent,
    BrowseDensityMenuItemsComponent,
    BrowseFilterMenuItemsComponent,
    AuthorCardComponent,
    AuthorCardSkeletonComponent,
    AuthorBrowseBulkBarComponent,
    AuthorMenuComponent,
  ],
  templateUrl: './author-browse-page.component.html',
})
export class AuthorBrowsePageComponent {
  private readonly authorQuery = inject(AuthorQueryService);
  private readonly urlHelper = inject(UrlHelperService);
  private readonly localStorage = inject(LocalStorageService);

  protected readonly messages = AUTHOR_BROWSE_MESSAGES;
  protected readonly sortField = AUTHOR_FIELDS.sortField;
  protected readonly estimateItemHeight = authorCardHeightForWidth;

  protected readonly authorMenu = viewChild(AuthorMenuComponent);
  protected readonly menuOpenAuthorId = computed(() => this.authorMenu()?.openAuthorId() ?? null);
  protected readonly labels = createAuthorBrowseLabels();
  protected readonly page = createBrowsePage<AuthorSummary, AuthorQueryFacetKey, AuthorQuerySortKey>({
    source: this.authorQuery,
    fields: AUTHOR_FIELDS,
    urlState: createBrowseUrlState(AUTHOR_FIELDS),
    facetDefinitions: this.labels.facetDefinitions,
    defaultSortTerms: () => DEFAULT_AUTHOR_SORT_TERMS,
    artworkUrls: (authors, lastVisibleIndex) => authors.slice(0, lastVisibleIndex + 1).flatMap(author => {
      const url = authorCardPhotoSrc(author, this.urlHelper);
      return url ? [url] : [];
    }),
    clickAwayExempt: 'app-author-card',
    selectionSuspended: () => this.menuOpenAuthorId() !== null,
    density: {
      baseWidth: 165,
      scale: new ScalePreference(this.localStorage, {
        storageKey: 'authorScalePreference',
        minScale: 0.7,
        maxScale: 1.3,
      }),
      mobileColumnsStorageKey: 'authorMobileColumnsPreference',
    },
  });

}
