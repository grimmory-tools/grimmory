/**
 * Jaro-Winkler algorithm constants
 */
const JARO_WINKLER_PREFIX_SCALE = 0.1;
const JARO_WINKLER_BOOST_THRESHOLD = 0.7;
const MAX_PREFIX_LENGTH = 4;
const MIN_NAME_PART_SIMILARITY = 0.75;

/**
 * Calculates the Jaro similarity between two strings.
 * Returns a value between 0.0 (no similarity) and 1.0 (identical).
 *
 * Note: The match window is floored to ensure integer bounds for the matching range.
 *
 * @param s1 First string
 * @param s2 Second string
 * @returns Jaro similarity score (0.0 to 1.0)
 */
export function calculateJaro(s1: string, s2: string): number {
  if (s1 === s2) return 1.0;
  if (s1.length === 0 || s2.length === 0) return 0.0;

  const matchWindow = Math.floor(Math.max(s1.length, s2.length) / 2) - 1;
  const { matches, s1Matches, s2Matches } = findMatches(s1, s2, matchWindow);

  if (matches === 0) return 0.0;

  const transpositions = countTranspositions(s1, s2, s1Matches, s2Matches);

  // Jaro formula: (m/|s1| + m/|s2| + (m - t/2)/m) / 3
  return (matches / s1.length + matches / s2.length + (matches - transpositions / 2) / matches) / 3.0;
}

/**
 * Finds matching characters between two strings within a match window.
 *
 * @param s1 First string
 * @param s2 Second string
 * @param matchWindow Maximum distance for matches
 * @returns Object containing match count and match arrays
 */
function findMatches(s1: string, s2: string, matchWindow: number): {
  matches: number;
  s1Matches: boolean[];
  s2Matches: boolean[];
} {
  const s1Matches = new Array(s1.length).fill(false);
  const s2Matches = new Array(s2.length).fill(false);
  let matches = 0;

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

  return { matches, s1Matches, s2Matches };
}

/**
 * Counts transpositions between matched characters.
 *
 * @param s1 First string
 * @param s2 Second string
 * @param s1Matches Array indicating which characters in s1 matched
 * @param s2Matches Array indicating which characters in s2 matched
 * @returns Number of transpositions
 */
function countTranspositions(s1: string, s2: string, s1Matches: boolean[], s2Matches: boolean[]): number {
  let transpositions = 0;
  let k = 0;

  for (let i = 0; i < s1.length; i++) {
    if (!s1Matches[i]) continue;
    while (!s2Matches[k]) k++;
    if (s1[i] !== s2[k]) transpositions++;
    k++;
  }

  return transpositions;
}

/**
 * Calculates the Jaro-Winkler similarity between two strings.
 * Applies a prefix bonus for strings with matching prefixes.
 * Returns a value between 0.0 (no similarity) and 1.0 (identical).
 *
 * @param s1 First string
 * @param s2 Second string
 * @returns Jaro-Winkler similarity score (0.0 to 1.0)
 */
export function calculateJaroWinkler(s1: string, s2: string): number {
  const jaro = calculateJaro(s1, s2);

  if (jaro < JARO_WINKLER_BOOST_THRESHOLD) {
    return jaro;
  }

  const prefixLength = calculateCommonPrefixLength(s1, s2);

  return jaro + (prefixLength * JARO_WINKLER_PREFIX_SCALE * (1 - jaro));
}

/**
 * Calculates the length of the common prefix between two strings.
 * Limited to a maximum of 4 characters as per Jaro-Winkler specification.
 *
 * @param s1 First string
 * @param s2 Second string
 * @returns Length of common prefix (0 to 4)
 */
function calculateCommonPrefixLength(s1: string, s2: string): number {
  let prefixLength = 0;
  const maxPrefix = Math.min(MAX_PREFIX_LENGTH, s1.length, s2.length);

  for (let i = 0; i < maxPrefix; i++) {
    if (s1[i] === s2[i]) {
      prefixLength++;
    } else {
      break;
    }
  }

  return prefixLength;
}

/**
 * Determines if two author names are similar enough to be potential duplicates.
 * Uses Jaro-Winkler distance with special handling for multi-part names.
 *
 * Rules:
 * - Single-word names: Direct Jaro-Winkler comparison
 * - Multi-part names: Both first AND last names must be similar
 * - Single vs multi-part: Never match (different entities)
 *
 * @param name1 First author name
 * @param name2 Second author name
 * @param threshold Minimum similarity score (0.0 to 1.0)
 * @returns true if names are similar enough to be duplicates
 */
export function areAuthorsSimilar(name1: string, name2: string, threshold: number): boolean {
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
  const averageSimilarity = (firstNameSimilarity + lastNameSimilarity) / 2;

  return firstNameSimilarity >= MIN_NAME_PART_SIMILARITY &&
         lastNameSimilarity >= MIN_NAME_PART_SIMILARITY &&
         averageSimilarity >= threshold;
}
