export interface AuthorIdsVariables {
  readonly authorIds: readonly number[];
}

export function requireAuthorIds(authorIds: readonly number[]): readonly number[] {
  if (authorIds.length === 0) {
    throw new Error('At least one author ID is required.');
  }
  return authorIds;
}
