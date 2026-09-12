export interface UpdateBookShelfMembershipVariables {
  readonly bookIds: readonly number[];
  readonly assignShelfIds: readonly number[];
  readonly unassignShelfIds: readonly number[];
}

export interface UpdateBookShelfMembershipResult {
  readonly confirmedBookIds: readonly number[];
}
