import {SortOption} from './sort.model';
import {IconType} from '../../../shared/icons/icon-selection';

export type ShelfSystemKey = 'kobo';

export interface Shelf {
  id?: number;
  name: string;
  icon?: string | null;
  iconType?: IconType | null;
  sort?: SortOption;
  publicShelf?: boolean;
  userId?: number;
  bookCount?: number;
  systemKey?: ShelfSystemKey | null;
}
