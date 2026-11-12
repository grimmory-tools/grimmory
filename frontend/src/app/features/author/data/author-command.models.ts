export interface AuthorIdsVariables {
  readonly authorIds: readonly number[];
}

export function requireAuthorIds(authorIds: readonly number[]): readonly number[] {
  if (authorIds.length === 0) {
    throw new Error('At least one author ID is required.');
  }
  return authorIds;
}

interface AuthorUpdateRequest {
  readonly name?: string;
  readonly description?: string;
  readonly asin?: string;
  readonly nameLocked?: boolean;
  readonly descriptionLocked?: boolean;
  readonly asinLocked?: boolean;
  readonly photoLocked?: boolean;
}

export interface UpdateAuthorVariables {
  readonly authorId: number;
  readonly request: AuthorUpdateRequest;
}

interface AuthorMatchRequest {
  readonly source: 'AUDNEXUS';
  readonly asin: string;
  readonly region: string;
}

export interface MatchAuthorVariables {
  readonly authorId: number;
  readonly request: AuthorMatchRequest;
}

export interface UploadAuthorPhotoVariables {
  readonly authorId: number;
  readonly file: File;
}

export interface UploadAuthorPhotoFromUrlVariables {
  readonly authorId: number;
  readonly url: string;
}
