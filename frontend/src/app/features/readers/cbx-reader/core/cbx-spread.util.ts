import {CbxPageSpread} from '../../../settings/user-management/user.service';
import {CbxPageDimension} from '../models/cbx-page-dimension.model';

export interface CbxSpread {
  readonly pages: number[];
}

function isWidePage(dimensions: CbxPageDimension[], pageIndex: number): boolean {
  return dimensions[pageIndex]?.wide === true;
}

function pageMatchesSpreadStart(pageNumber: number, pageSpread: CbxPageSpread): boolean {
  const startsOdd = pageSpread === CbxPageSpread.ODD;
  return (pageNumber % 2 === 1) === startsOdd;
}

export function computeCbxSpreads(
  pages: number[],
  dimensions: CbxPageDimension[],
  pageSpread: CbxPageSpread
): CbxSpread[] {
  if (!pages.length) {
    return [];
  }

  const spreads: CbxSpread[] = [{pages: [0]}];
  let pageIndex = 1;

  while (pageIndex < pages.length) {
    const nextIndex = pageIndex + 1;

    if (
      isWidePage(dimensions, pageIndex) ||
      !pageMatchesSpreadStart(pages[pageIndex], pageSpread) ||
      nextIndex >= pages.length ||
      isWidePage(dimensions, nextIndex)
    ) {
      spreads.push({pages: [pageIndex]});
      pageIndex++;
      continue;
    }

    spreads.push({pages: [pageIndex, nextIndex]});
    pageIndex += 2;
  }

  return spreads;
}

export function findCbxSpreadForPage(spreads: CbxSpread[], pageIndex: number): CbxSpread | undefined {
  return spreads.find(spread => spread.pages.includes(pageIndex));
}
