import {IconType} from '../../../shared/icons/icon-selection';

export interface ShelfDefinition {
  readonly id: number;
  readonly userId: number;
  readonly name: string;
  readonly icon?: string;
  readonly iconType?: IconType;
  readonly publicShelf: boolean;
  readonly bookCount: number;
}
