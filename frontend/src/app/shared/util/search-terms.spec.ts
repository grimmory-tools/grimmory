import {describe, expect, it} from 'vitest';


import {normalizeLocalSearchTerm, normalizeRemoteSearchTerm} from './search-terms';

describe('normalizeRemoteSearchTerm', () => {
  it('strips punctuation and collapses whitespace without changing letters', () => {
    expect(normalizeRemoteSearchTerm('  Dune!\t/ Messiah?  ')).toBe('Dune Messiah');
    expect(normalizeRemoteSearchTerm('  Łódź Ærø STRAẞE  ')).toBe('Łódź Ærø STRAẞE');
  });

  it('returns an empty string for empty or whitespace-only input', () => {
    expect(normalizeRemoteSearchTerm('')).toBe('');
    expect(normalizeRemoteSearchTerm(' \t ')).toBe('');
  });
});

describe('normalizeLocalSearchTerm', () => {
  it('normalizes representative text for local matching', () => {
    expect(normalizeLocalSearchTerm('  Ætna   Łódź / Straße!  ')).toBe('aetna lodz strasse');
  });

  it('strips punctuation and collapses whitespace', () => {
    expect(normalizeLocalSearchTerm('  Dune!\t/ Messiah?  ')).toBe('dune messiah');
  });

  it('returns an empty string for empty or whitespace-only input', () => {
    expect(normalizeLocalSearchTerm('')).toBe('');
    expect(normalizeLocalSearchTerm(' \t ')).toBe('');
  });
});

