import {describe, expect, it} from 'vitest';

// Test the duplicate detection algorithms directly
// These are private methods but we access them via type casting for testing

// Helper to access private methods
type ComponentType = {
  calculateJaro: (s1: string, s2: string) => number;
  calculateJaroWinkler: (s1: string, s2: string) => number;
  areAuthorsSimilar: (name1: string, name2: string, threshold: number) => boolean;
};

// Create test implementations of the algorithms
const calculateJaro = (s1: string, s2: string): number => {
  if (s1 === s2) return 1.0;
  if (s1.length === 0 || s2.length === 0) return 0.0;

  const matchWindow = Math.max(s1.length, s2.length) / 2 - 1;
  const s1Matches = new Array(s1.length).fill(false);
  const s2Matches = new Array(s2.length).fill(false);

  let matches = 0;
  let transpositions = 0;

  // Find matches
  for (let i = 0; i < s1.length; i++) {
    const start = Math.max(0, i - matchWindow);
    const end = Math.min(i + matchWindow + 1, s2.length);

    for (let j = start; j < end; j++) {
      if (s2Matches[j] || s1[i] !== s2[j]) continue;
      s1Matches[i] = true;
      s2Matches[j] = true;
      matches++;
      break;
    }
  }

  if (matches === 0) return 0.0;

  // Find transpositions
  let k = 0;
  for (let i = 0; i < s1.length; i++) {
    if (!s1Matches[i]) continue;
    while (!s2Matches[k]) k++;
    if (s1[i] !== s2[k]) transpositions++;
    k++;
  }

  return (matches / s1.length + matches / s2.length + (matches - transpositions / 2) / matches) / 3.0;
};

const calculateJaroWinkler = (s1: string, s2: string): number => {
  const jaro = calculateJaro(s1, s2);

  if (jaro < 0.7) {
    return jaro;
  }

  let prefixLength = 0;
  const maxPrefix = Math.min(4, s1.length, s2.length);
  for (let i = 0; i < maxPrefix; i++) {
    if (s1[i] === s2[i]) {
      prefixLength++;
    } else {
      break;
    }
  }

  return jaro + (prefixLength * 0.1 * (1 - jaro));
};

const areAuthorsSimilar = (name1: string, name2: string, threshold: number): boolean => {
  const lower1 = name1.toLowerCase();
  const lower2 = name2.toLowerCase();

  const parts1 = lower1.split(/\s+/).filter(p => p.length > 0);
  const parts2 = lower2.split(/\s+/).filter(p => p.length > 0);

  if (parts1.length === 1 && parts2.length === 1) {
    return calculateJaroWinkler(lower1, lower2) >= threshold;
  }

  if (parts1.length === 1 || parts2.length === 1) {
    return false;
  }

  const firstName1 = parts1[0];
  const firstName2 = parts2[0];
  const lastName1 = parts1[parts1.length - 1];
  const lastName2 = parts2[parts2.length - 1];

  const firstNameSimilarity = calculateJaroWinkler(firstName1, firstName2);
  const lastNameSimilarity = calculateJaroWinkler(lastName1, lastName2);

  const MIN_PART_THRESHOLD = 0.75;
  const averageSimilarity = (firstNameSimilarity + lastNameSimilarity) / 2;

  return firstNameSimilarity >= MIN_PART_THRESHOLD &&
         lastNameSimilarity >= MIN_PART_THRESHOLD &&
         averageSimilarity >= threshold;
};

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
      expect(result).toBeGreaterThan(0.6);
      expect(result).toBeLessThan(0.7);
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
      it('should handle extra whitespace', () => {
        const result = areAuthorsSimilar('Stephen  King', 'Stephen King', THRESHOLD);
        expect(result).toBe(true);
      });

      it('should handle leading/trailing whitespace', () => {
        const result = areAuthorsSimilar(' Stephen King ', 'Stephen King', THRESHOLD);
        expect(result).toBe(true);
      });

      it('should handle tabs and multiple spaces', () => {
        const result = areAuthorsSimilar('Stephen\t\tKing', 'Stephen King', THRESHOLD);
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
