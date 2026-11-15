import type {IconType} from '../../../shared/icons/icon-selection';

export interface Shelf {
  readonly id: number;
  readonly name: string;
  readonly icon?: string;
  readonly iconType?: IconType;
  readonly userId: number;
  readonly publicShelf: boolean;
}

export interface MagicShelf {
  readonly id: number;
  readonly name: string;
  readonly icon: string | null;
  readonly iconType: IconType | null;
  readonly filterJson: string;
  readonly isPublic: boolean;
}
