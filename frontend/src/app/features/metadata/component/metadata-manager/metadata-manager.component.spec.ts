import {describe, expect, it} from 'vitest';
import {calculateJaro, calculateJaroWinkler, areAuthorsSimilar} from '../../../../shared/utils/string-similarity.util';

describe('MetadataManagerComponent - Duplicate Detection', () => {

  describe('calculateJaroWinkler', () => {
    it('should return 1.0 for identical strings', () => {
      const result = calculateJaroWinkler('hello', 'hello');
      expect(result).toBe(1.0);
    });

    it('should return low similarity for completely different strings', () => {
      const result = calculateJaroWinkler('abc', 'xyz');
      expect(result).toBeLessThan(0.5);
    });

    it('should return moderate similarity for strings with typos', () => {
      const result = calculateJaroWinkler('stephen', 'stefen');
      expect(result).toBeGreaterThan(0.89);
      expect(result).toBeLessThan(0.90);
    });

    it('should apply Winkler bonus for matching prefixes', () => {
      const withPrefix = calculateJaroWinkler('stephen king', 'stephen kong');
      const withoutPrefix = calculateJaroWinkler('king stephen', 'kong stephen');
      expect(withPrefix).toBeGreaterThan(withoutPrefix);
    });

    it('should handle empty strings', () => {
      const result = calculateJaroWinkler('', '');
      expect(result).toBe(1.0);
    });

    it('should return 0.0 when one string is empty', () => {
      expect(calculateJaroWinkler('hello', '')).toBe(0.0);
      expect(calculateJaroWinkler('', 'world')).toBe(0.0);
    });
  });

  describe('calculateJaro', () => {
    it('should return 1.0 for identical strings', () => {
      const result = calculateJaro('test', 'test');
      expect(result).toBe(1.0);
    });

    it('should return 0.0 for no matches', () => {
      const result = calculateJaro('abc', 'xyz');
      expect(result).toBe(0.0);
    });

    it('should handle transpositions correctly', () => {
      // "martha" vs "marhta" has transposition
      const result = calculateJaro('martha', 'marhta');
      expect(result).toBeGreaterThan(0.9);
    });

    it('should handle empty strings correctly', () => {
      expect(calculateJaro('', '')).toBe(1.0);
      expect(calculateJaro('abc', '')).toBe(0.0);
    });
  });

  describe('areAuthorsSimilar', () => {
    const THRESHOLD = 0.85;

    describe('single-word names', () => {
      it('should match similar single-word names', () => {
        const result = areAuthorsSimilar('King', 'Kong', THRESHOLD);
        expect(result).toBe(true);
      });

      it('should not match dissimilar single-word names', () => {
        const result = areAuthorsSimilar('King', 'Smith', THRESHOLD);
        expect(result).toBe(false);
      });

      it('should match identical single-word names', () => {
        const result = areAuthorsSimilar('Molière', 'Molière', THRESHOLD);
        expect(result).toBe(true);
      });
    });

    describe('single-word vs multi-part names', () => {
      it('should NOT match single-word name with multi-part name', () => {
        // Different people despite sharing a name component
        const result = areAuthorsSimilar('Molière', 'Molière Hubert Carrier', THRESHOLD);
        expect(result).toBe(false);
      });

      it('should NOT match multi-part name with single-word name', () => {
        const result = areAuthorsSimilar('Stephen King', 'King', THRESHOLD);
        expect(result).toBe(false);
      });

      it('should NOT match when only first name matches', () => {
        const result = areAuthorsSimilar('Alessandro', 'Alessandro Volta', THRESHOLD);
        expect(result).toBe(false);
      });
    });

    describe('multi-part names', () => {
      it('should match names with minimal typos', () => {
        // One character difference in last name, identical first name
        const result = areAuthorsSimilar('John Smith', 'John Smithh', THRESHOLD);
        expect(result).toBe(true);
      });

      it('should match names with character transposition', () => {
        // Transposed characters in last name
        const result = areAuthorsSimilar('John King', 'John Knig', THRESHOLD);
        expect(result).toBe(true);
      });

      it('should NOT match when only first names match', () => {
        // Alessandro Baricco vs Alessandro Volta
        const result = areAuthorsSimilar('Alessandro Baricco', 'Alessandro Volta', THRESHOLD);
        expect(result).toBe(false);
      });

      it('should NOT match when only last names match', () => {
        // David Eddings vs Sarah Eddings
        const result = areAuthorsSimilar('David Eddings', 'Sarah Eddings', THRESHOLD);
        expect(result).toBe(false);
      });

      it('should NOT match completely different names', () => {
        const result = areAuthorsSimilar('David Eddings', 'Davide Palladini', THRESHOLD);
        expect(result).toBe(false);
      });

      it('should NOT match different names sharing first name', () => {
        // Grazia Gatti vs Grazia Nidasio
        const result = areAuthorsSimilar('Grazia Gatti', 'Grazia Nidasio', THRESHOLD);
        expect(result).toBe(false);
      });

      it('should handle three-part names correctly', () => {
        // Single character typo in last part
        const result = areAuthorsSimilar('J. R. R. Tolkien', 'J. R. R. Tolkienn', THRESHOLD);
        expect(result).toBe(true);
      });

      it('should handle names with middle names', () => {
        // George R. R. Martin vs George R R Martin
        const result = areAuthorsSimilar('George R. R. Martin', 'George R R Martin', THRESHOLD);
        expect(result).toBe(true);
      });
    });

    describe('case insensitivity', () => {
      it('should match regardless of case', () => {
        expect(areAuthorsSimilar('Stephen King', 'stephen king', THRESHOLD)).toBe(true);
        expect(areAuthorsSimilar('STEPHEN KING', 'Stephen King', THRESHOLD)).toBe(true);
        expect(areAuthorsSimilar('StEpHeN KiNg', 'stephen king', THRESHOLD)).toBe(true);
      });
    });

    describe('whitespace handling', () => {
      it.each([
        ['extra whitespace', 'Stephen  King', 'Stephen King'],
        ['leading/trailing whitespace', ' Stephen King ', 'Stephen King'],
        ['tabs and multiple spaces', 'Stephen\t\tKing', 'Stephen King'],
      ])('should handle %s', (_description, name1, name2) => {
        const result = areAuthorsSimilar(name1, name2, THRESHOLD);
        expect(result).toBe(true);
      });
    });

    describe('threshold behavior', () => {
      it('should respect MIN_PART_THRESHOLD of 0.75', () => {
        // First name very dissimilar (< 0.75), last name identical
        const result = areAuthorsSimilar('John Smith', 'Alexandra Smith', 0.85);
        expect(result).toBe(false);
      });

      it('should require average similarity to meet threshold', () => {
        // Single character typo meets 0.85 threshold
        const result = areAuthorsSimilar('John Smith', 'John Smithh', 0.85);
        expect(result).toBe(true);
      });
    });

    describe('real-world author examples', () => {
      it('should match common typo patterns', () => {
        // Extra character at end
        expect(areAuthorsSimilar('John Smith', 'John Smithh', THRESHOLD)).toBe(true);

        // Transposition in last name
        expect(areAuthorsSimilar('John King', 'John Knig', THRESHOLD)).toBe(true);

        // Missing character in middle
        expect(areAuthorsSimilar('Neil Gaiman', 'Neil Gaman', THRESHOLD)).toBe(true);
      });

      it('should NOT match different authors with common first names', () => {
        expect(areAuthorsSimilar('John Smith', 'John Doe', THRESHOLD)).toBe(false);
        expect(areAuthorsSimilar('Mary Johnson', 'Mary Williams', THRESHOLD)).toBe(false);
      });

      it('should match names with diacritics removed/added', () => {
        // This tests if the algorithm handles character differences
        const result = areAuthorsSimilar('Jose Saramago', 'José Saramago', THRESHOLD);
        expect(result).toBe(true);
      });
    });
  });
});
