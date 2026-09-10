import {BrowseFacetGroup, BrowsePage} from '../../../core/data/browse.models';
import {BookSummary} from './book-response.models';

export type BookPage = BrowsePage<BookSummary>;
export type BookFacetGroup = BrowseFacetGroup;
