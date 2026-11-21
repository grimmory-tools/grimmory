import {describe, expect, it} from 'vitest';
import {findAuthorDuplicates} from './author-duplicates';
import {AuthorSummary} from './model/author.model';

function author(id: number, name: string): AuthorSummary {
  return {id, name, bookCount: 1, hasPhoto: false};
}

describe('findAuthorDuplicates', () => {
  it('finds accents, punctuation, commas and reversed names', () => {
    const groups = findAuthorDuplicates([
      author(1, 'Jean Dupont'),
      author(2, 'Dupont, Jean'),
      author(3, 'JEAN DÚPONT'),
      author(4, 'Victor Hugo'),
    ]);

    expect(groups).toHaveLength(1);
    expect(groups[0].map(item => item.id).sort()).toEqual([1, 2, 3]);
  });

  it('finds close spellings without matching unrelated names', () => {
    const groups = findAuthorDuplicates([
      author(1, 'Agatha Christie'),
      author(2, 'Agatha Christia'),
      author(3, 'Victor Hugo'),
      author(4, 'Émile Zola'),
    ]);

    expect(groups).toHaveLength(1);
    expect(groups[0].map(item => item.id).sort()).toEqual([1, 2]);
  });

  it('does not group blank or unrelated short names', () => {
    expect(findAuthorDuplicates([
      author(1, ' '), author(2, '...'), author(3, 'Li Bo'), author(4, 'Li Po'),
    ])).toEqual([]);
  });
});
