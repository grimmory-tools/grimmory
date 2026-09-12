import {type BrowseFacetBucket, formatRangeToken} from '../../../shared/browse/facet-ranges';
import {ReadStatus} from './book.model';

interface RangeConfig {
  id: number;
  label: string;
  min: number;
  max: number;
}

export const READ_STATUS_LABELS = {
  [ReadStatus.UNREAD]: 'Unread',
  [ReadStatus.READING]: 'Reading',
  [ReadStatus.RE_READING]: 'Re-reading',
  [ReadStatus.PARTIALLY_READ]: 'Partially Read',
  [ReadStatus.PAUSED]: 'Paused',
  [ReadStatus.READ]: 'Read',
  [ReadStatus.WONT_READ]: 'Won\'t Read',
  [ReadStatus.ABANDONED]: 'Abandoned',
  [ReadStatus.UNSET]: 'Unset'
} satisfies Readonly<Record<ReadStatus, string>>;

export const PAGE_COUNT_RANGES: readonly RangeConfig[] = [
  {id: 0, label: '< 50 pages', min: 0, max: 50},
  {id: 1, label: '50–100 pages', min: 50, max: 100},
  {id: 2, label: '100–200 pages', min: 100, max: 200},
  {id: 3, label: '200–400 pages', min: 200, max: 400},
  {id: 4, label: '400–600 pages', min: 400, max: 600},
  {id: 5, label: '600–1000 pages', min: 600, max: 1000},
  {id: 6, label: '1000+ pages', min: 1000, max: Infinity}
];

export const AGE_RATING_OPTIONS: readonly RangeConfig[] = [
  {id: 0, min: 0, max: 6, label: 'All Ages'},
  {id: 6, min: 6, max: 10, label: '6+'},
  {id: 10, min: 10, max: 13, label: '10+'},
  {id: 13, min: 13, max: 16, label: '13+'},
  {id: 16, min: 16, max: 18, label: '16+'},
  {id: 18, min: 18, max: 21, label: '18+'},
  {id: 21, min: 21, max: Infinity, label: '21+'}
];

export const CONTENT_RATING_LABELS: Readonly<Record<string, string>> = {
  'EVERYONE': 'Everyone',
  'TEEN': 'Teen',
  'MATURE': 'Mature',
  'ADULT': 'Adult',
  'EXPLICIT': 'Explicit'
};

export interface MatchScoreBand extends BrowseFacetBucket {
  readonly labelKey: string;
}

export const MATCH_SCORE_BANDS: readonly MatchScoreBand[] = [
  {min: 95, labelKey: 'outstanding'},
  {min: 90, max: 95, labelKey: 'excellent'},
  {min: 80, max: 90, labelKey: 'great'},
  {min: 70, max: 80, labelKey: 'good'},
  {min: 50, max: 70, labelKey: 'fair'},
  {min: 30, max: 50, labelKey: 'weak'},
  {min: 0, max: 30, labelKey: 'poor'},
];

export function bookMatchScoreRangeToken(score: number): string | null {
  const band = MATCH_SCORE_BANDS.find(candidate =>
    (candidate.min == null || score >= candidate.min) && (candidate.max == null || score <= candidate.max));
  return band ? formatRangeToken(band) : null;
}
