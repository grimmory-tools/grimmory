export interface BrowseFacetBucket {
  readonly min?: number;
  readonly max?: number;
  readonly maxExclusive?: boolean;
}

export function formatRangeToken(
  {min, max, maxExclusive}: {readonly min?: number | null; readonly max?: number | null; readonly maxExclusive?: boolean},
): string | null {
  if (min == null && max == null) {
    return null;
  }
  return `${min ?? '*'}..${max != null && maxExclusive ? '<' : ''}${max ?? '*'}`;
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

export function parseRangeToken(token: string): {min: number | null; max: number | null; maxExclusive: boolean} | null {
  const match = /^(\*|-?\d+(?:\.\d+)?)\.\.(?:(\*)|(<?)(-?\d+(?:\.\d+)?))$/.exec(token);
  if (match) {
    const min = match[1] === '*' ? null : Number(match[1]);
    const max = match[2] === '*' ? null : Number(match[4]);
    return min == null && max == null ? null : {min, max, maxExclusive: match[3] === '<'};
  }
  const exact = Number(token);
  return token.trim() !== '' && Number.isFinite(exact) ? {min: exact, max: exact, maxExclusive: false} : null;
}
