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

export interface AuthorSearchResult {
  readonly source: 'AUDNEXUS';
  readonly asin: string;
  readonly name: string;
  readonly description: string | null;
  readonly imageUrl: string | null;
}

export interface AuthorPhotoResult {
  readonly url: string;
  readonly width: number;
  readonly height: number;
  readonly index: number;
}
