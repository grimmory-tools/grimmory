import {Component, computed, inject} from '@angular/core';
import {ActivatedRoute} from '@angular/router';

import {createBrowseFilterPage} from '../../../shared/browse/filter-page';
import {BrowseFilterPageComponent} from '../../../shared/browse/filter-page.component';
import {createBrowseUrlState} from '../../../shared/browse/url-state';
import {BookQueryService} from '../data/book-query.service';
import {BOOK_FIELDS} from './book-browse-fields';
import {createBookBrowseLabels} from './book-browse-labels';
import {bookBrowseScope} from './book-browse-scope';

@Component({
  selector: 'app-book-browse-filter-page',
  imports: [BrowseFilterPageComponent],
  template: `
    <app-browse-filter-page
      [page]="filters"
      [title]="labels.title()"
      [searchHint]="labels.searchHintKey()"
      showLabelKey="browse.filterPage.show" />
  `,
})
export class BookBrowseFilterPageComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly bookQuery = inject(BookQueryService);
  private readonly scope = computed(() =>
    bookBrowseScope(this.route.snapshot.paramMap, this.route.snapshot.data),
  );
  protected readonly labels = createBookBrowseLabels(this.scope);
  protected readonly filters = createBrowseFilterPage({
    source: this.bookQuery,
    fields: BOOK_FIELDS,
    urlState: createBrowseUrlState(BOOK_FIELDS),
    facetDefinitions: this.labels.facetDefinitions,
    pinned: this.scope,
  });
}
