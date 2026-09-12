export interface BrowseFacetBucket {
  readonly min?: number;
  readonly max?: number;
  readonly stars?: number;
}

export function formatRangeToken(
  {min, max}: {readonly min?: number | null; readonly max?: number | null},
): string | null {
  if (min == null && max == null) {
    return null;
  }
  return `${min ?? '*'}..${max ?? '*'}`;
}

export function formatRangeLabel(
  {min, max}: {readonly min?: number | string | null; readonly max?: number | string | null},
): string | null {
  if (min == null && max == null) {
    return null;
  }
  if (max == null) {
    return `${min}+`;
  }
  if (min == null) {
    return `-${max}`;
  }
  return min === max ? `${min}` : `${min}-${max}`;
}

export function parseRangeToken(token: string): {min: number | null; max: number | null} | null {
  const match = /^(\*|-?\d+(?:\.\d+)?)\.\.(\*|-?\d+(?:\.\d+)?)$/.exec(token);
  if (match) {
    const min = match[1] === '*' ? null : Number(match[1]);
    const max = match[2] === '*' ? null : Number(match[2]);
    return min == null && max == null ? null : {min, max};
  }
  const exact = Number(token);
  return token.trim() !== '' && Number.isFinite(exact) ? {min: exact, max: exact} : null;
}

export function bucketRangeTokens(
  buckets: readonly BrowseFacetBucket[] | undefined,
): ReadonlySet<string> {
  return new Set((buckets ?? []).flatMap(bucket => {
    const token = formatRangeToken(bucket);
    return token == null ? [] : [token];
  }));
}
