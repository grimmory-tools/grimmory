export interface AuthorSummary {
  readonly id: number;
  readonly name: string;
  readonly bookCount: number;
  readonly hasPhoto: boolean;
}

export interface AuthorDetail {
  readonly id: number;
  readonly name: string;
  readonly description?: string;
  readonly asin?: string;
  readonly nameLocked: boolean;
  readonly descriptionLocked: boolean;
  readonly asinLocked: boolean;
  readonly photoLocked: boolean;
}
