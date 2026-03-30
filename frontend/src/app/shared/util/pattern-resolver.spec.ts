import {describe, expect, it} from 'vitest';

import {applyModifier, replacePlaceholders} from './pattern-resolver';

describe('pattern-resolver', () => {
  it('returns the original value when the modifier is unknown or the value is empty', () => {
    expect(applyModifier('', 'upper', 'title')).toBe('');
    expect(applyModifier('Alpha', 'unknown', 'title')).toBe('Alpha');
  });

  it('applies the supported value modifiers', () => {
    expect(applyModifier('Ada Lovelace, Grace Hopper', 'first', 'authors')).toBe('Ada Lovelace');
    expect(applyModifier('Ada Lovelace', 'sort', 'authors')).toBe('Lovelace, Ada');
    expect(applyModifier('Ada Lovelace', 'initial', 'authors')).toBe('L');
    expect(applyModifier('Chapter One', 'initial', 'title')).toBe('C');
    expect(applyModifier('Ada', 'upper', 'title')).toBe('ADA');
    expect(applyModifier('ADA', 'lower', 'title')).toBe('ada');
  });

  it('replaces placeholders and respects optional fallback blocks', () => {
    expect(
      replacePlaceholders('<{title}|{fallback}> by {authors:first}', {
        title: 'The Book',
        authors: 'Ada Lovelace, Grace Hopper',
        fallback: 'Fallback Title',
      })
    ).toBe('The Book by Ada Lovelace');

    expect(
      replacePlaceholders('<{title}|{fallback}> by {authors:first}', {
        authors: 'Ada Lovelace, Grace Hopper',
        fallback: 'Fallback Title',
      })
    ).toBe('Fallback Title by Ada Lovelace');
  });

  it('handles nested modifier blocks and trims the final pattern', () => {
    expect(
      replacePlaceholders('  {authors:sort} - {title:upper}  ', {
        authors: 'Ada Lovelace, Grace Hopper',
        title: 'calculus notes',
      })
    ).toBe('Lovelace, Ada - CALCULUS NOTES');
  });
});
