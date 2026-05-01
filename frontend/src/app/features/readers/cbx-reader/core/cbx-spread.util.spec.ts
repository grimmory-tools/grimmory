import {describe, expect, it} from 'vitest';
import {CbxPageSpread} from '../../../settings/user-management/user.service';
import {computeCbxSpreads, findCbxSpreadForPage} from './cbx-spread.util';
import {CbxPageDimension} from '../models/cbx-page-dimension.model';

function dimensions(widePages: number[] = []): CbxPageDimension[] {
  return Array.from({length: 6}, (_, index) => ({
    pageNumber: index + 1,
    width: widePages.includes(index) ? 1800 : 900,
    height: 1200,
    wide: widePages.includes(index)
  }));
}

describe('cbx spread utilities', () => {
  const pages = [1, 2, 3, 4, 5, 6];

  it('keeps the cover solo and pairs even-starting spreads for LTR comics', () => {
    const spreads = computeCbxSpreads(pages, dimensions(), CbxPageSpread.EVEN);

    expect(spreads.map(spread => spread.pages)).toEqual([
      [0],
      [1, 2],
      [3, 4],
      [5],
    ]);
  });

  it('keeps alignment pages solo until the configured spread parity starts', () => {
    const spreads = computeCbxSpreads(pages, dimensions(), CbxPageSpread.ODD);

    expect(spreads.map(spread => spread.pages)).toEqual([
      [0],
      [1],
      [2, 3],
      [4, 5],
    ]);
  });

  it('keeps wide pages solo and resumes pairing after them', () => {
    const spreads = computeCbxSpreads(pages, dimensions([3]), CbxPageSpread.EVEN);

    expect(spreads.map(spread => spread.pages)).toEqual([
      [0],
      [1, 2],
      [3],
      [4],
      [5],
    ]);
  });

  it('finds the spread for either page in a pair', () => {
    const spreads = computeCbxSpreads(pages, dimensions(), CbxPageSpread.EVEN);

    expect(findCbxSpreadForPage(spreads, 1)?.pages).toEqual([1, 2]);
    expect(findCbxSpreadForPage(spreads, 2)?.pages).toEqual([1, 2]);
  });
});
