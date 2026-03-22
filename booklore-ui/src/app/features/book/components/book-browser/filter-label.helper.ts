import {fileSizeRanges, matchScoreRanges, pageCountRanges, ratingOptions10, ratingRanges} from './book-filter/book-filter.config';

export class FilterLabelHelper {


  static getFilterDisplayValue(filterType: string, value: string | number): string {
    const numericValue = typeof value === 'string' ? Number(value) : value;

    switch (filterType.toLowerCase()) {
      case 'filesize': {
        const fileSizeRange = fileSizeRanges.find(r => r.id === numericValue);
        if (fileSizeRange) return fileSizeRange.label;
        return String(value);
      }

      case 'pagecount': {
        const pageCountRange = pageCountRanges.find(r => r.id === numericValue);
        if (pageCountRange) return pageCountRange.label;
        return String(value);
      }

      case 'matchscore': {
        const matchScoreRange = matchScoreRanges.find(r => r.id === numericValue);
        if (matchScoreRange) return matchScoreRange.label;
        return String(value);
      }

      case 'personalrating': {
        const personalRating = ratingOptions10.find(r => r.id === numericValue);
        if (personalRating) return personalRating.label;
        return String(value);
      }

      case 'amazonrating':
      case 'goodreadsrating':
      case 'hardcoverrating': {
        const ratingRange = ratingRanges.find(r => r.id === numericValue);
        if (ratingRange) return ratingRange.label;
        return String(value);
      }

      default:
        return String(value);
    }
  }

}
