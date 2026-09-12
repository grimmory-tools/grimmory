import {type BrowseFacetBucket} from '../../../shared/browse/facet-ranges';

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
