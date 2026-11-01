import {Component, inject} from '@angular/core';

import {createBrowseFilterPage} from '../../../shared/browse/filter-page';
import {BrowseFilterPageComponent} from '../../../shared/browse/filter-page.component';
import {createBrowseUrlState} from '../../../shared/browse/url-state';
import {AuthorQueryService} from '../data/author-query.service';
import {AUTHOR_FIELDS} from './author-browse-fields';
import {createAuthorBrowseLabels} from './author-browse-labels';

@Component({
  selector: 'app-author-browse-filter-page',
  imports: [BrowseFilterPageComponent],
  template: `
    <app-browse-filter-page
      [page]="filters"
      [title]="labels.title()"
      [searchHint]="labels.searchHintKey"
      showLabelKey="browse.filterPage.showAuthors" />
  `,
})
export class AuthorBrowseFilterPageComponent {
  private readonly authorQuery = inject(AuthorQueryService);
  protected readonly labels = createAuthorBrowseLabels();
  protected readonly filters = createBrowseFilterPage({
    source: this.authorQuery,
    fields: AUTHOR_FIELDS,
    urlState: createBrowseUrlState(AUTHOR_FIELDS),
    facetDefinitions: this.labels.facetDefinitions,
  });
}
