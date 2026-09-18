import {describe, expect, it} from 'vitest';

import {
  bookCommandChangeSet,
  DeleteBooksPartialError,
  requireBookIds,
} from './book-command.models';

describe('requireBookIds', () => {
  it('rejects an empty selection before any request leaves', () => {
    expect(() => requireBookIds([])).toThrowError();
  });

  it('passes a non-empty selection through', () => {
    expect(requireBookIds([3, 4])).toEqual([3, 4]);
  });
});

describe('bookCommandChangeSet', () => {
  it('maps a success through the command-specific confirmation', () => {
    const changeSet = bookCommandChangeSet(
      {status: 'success', data: [7, 9]},
      [7, 9, 11],
      bookIds => ({changedBookIds: bookIds}),
    );

    expect(changeSet).toEqual({changedBookIds: [7, 9]});
  });

  it('treats an unrecognised failure as touching every requested book', () => {
    const changeSet = bookCommandChangeSet(
      {status: 'failure', error: new Error('boom')},
      [7, 9, 11],
      () => ({changedBookIds: []}),
    );

    expect(changeSet).toEqual({changedBookIds: [7, 9, 11]});
  });

  it('keeps completed chunks and refetches attempted ones on partial failure', () => {
    const error = new DeleteBooksPartialError(
      {removedBookIds: [1, 2], fileCleanupFailedBookIds: []},
      [3, 4],
      new Error('boom'),
    );
    const changeSet = bookCommandChangeSet(
      {status: 'failure', error},
      [1, 2, 3, 4],
      () => ({changedBookIds: []}),
    );

    expect(changeSet).toEqual({deletedBookIds: [1, 2], changedBookIds: [3, 4]});
  });
});
