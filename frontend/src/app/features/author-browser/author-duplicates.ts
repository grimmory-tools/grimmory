import {AuthorSummary} from './model/author.model';

function normalizedName(name: string): string {
  return name.normalize('NFKD').replace(/\p{M}/gu, '').toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, ' ').trim().split(/\s+/).sort().join(' ');
}

export function findAuthorDuplicates(authors: AuthorSummary[]): AuthorSummary[][] {
  const parents = authors.map((_, index) => index);
  const signatures = new Map<string, number>();

  function root(index: number): number {
    while (parents[index] !== index) {
      parents[index] = parents[parents[index]];
      index = parents[index];
    }
    return index;
  }

  for (let index = 0; index < authors.length; index++) {
    const name = normalizedName(authors[index].name);
    if (!name) continue;

    const keys = new Set([name]);
    if (name.length >= 8 && name.includes(' ')) {
      const characters = Array.from(name);
      for (let position = 0; position < characters.length; position++) {
        if (characters[position] !== ' ') {
          keys.add(characters.slice(0, position).join('') + characters.slice(position + 1).join(''));
        }
      }
    }
    for (const key of keys) {
      const previous = signatures.get(key);
      if (previous === undefined) {
        signatures.set(key, index);
      } else {
        parents[root(index)] = root(previous);
      }
    }
  }

  const groups = new Map<number, AuthorSummary[]>();
  for (let index = 0; index < authors.length; index++) {
    const parent = root(index);
    const group = groups.get(parent) ?? [];
    group.push(authors[index]);
    groups.set(parent, group);
  }
  return [...groups.values()]
    .filter(group => group.length > 1)
    .map(group => group.sort((a, b) => a.name.localeCompare(b.name) || a.id - b.id))
    .sort((a, b) => a[0].name.localeCompare(b[0].name));
}
