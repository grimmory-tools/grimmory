import type {IconType} from '../../../shared/icons/icon-selection';

export interface CreateShelfVariables {
  readonly name: string;
  readonly icon?: string;
  readonly iconType?: IconType;
  readonly publicShelf: boolean;
}

export interface UpdateShelfVariables {
  readonly shelfId: number;
  readonly shelf: CreateShelfVariables;
}

export interface CreateMagicShelfVariables {
  readonly name: string;
  readonly icon?: string;
  readonly iconType?: IconType;
  readonly filterJson: string;
  readonly isPublic: boolean;
}

export interface UpdateMagicShelfVariables {
  readonly magicShelfId: number;
  readonly magicShelf: CreateMagicShelfVariables;
}

export interface UpdateShelfMembershipVariables {
  readonly bookIds: readonly number[];
  readonly assignShelfIds: readonly number[];
  readonly unassignShelfIds: readonly number[];
}
