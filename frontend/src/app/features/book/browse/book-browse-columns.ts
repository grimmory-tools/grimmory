import {formatMediumDate} from '../../../shared/util/date-format';
import {formatFileSizeKb} from '../../../shared/util/file-size';
import {type TableColumnPreference} from '../../settings/user-management/user.service';
import {type BookQuerySortKey} from '../data/book-query-params';
import {type BookSummary} from '../data/book-response.models';
import {
  COLUMNS,
  COLUMNS_BY_KEY,
  COLUMN_GROUP_ORDER,
  bookReadStatusLabelKey,
  type BookBrowseColumnGroupId,
  type BookColumnKey,
  type BookColumnKind,
  type BookColumnValue,
} from './book-browse-fields';

export const BOOK_EMPTY_VALUE = '—';

const DEFAULT_COLUMN_MIN_WIDTH = 84;
const DEFAULT_COLUMN_MAX_WIDTH = 420;
const NUMBER_FORMAT = new Intl.NumberFormat();

export interface BookColumnSizing {
  readonly size: number;
  readonly minSize: number;
  readonly maxSize: number;
}

export interface BookColumnOption {
  readonly field: BookColumnKey;
  readonly labelKey: string;
  readonly visible: boolean;
  readonly hideable: boolean;
}

export interface BookBrowseColumnVisibilityChange {
  readonly field: string;
  readonly visible: boolean;
}

export interface BookColumnSection {
  readonly id: BookBrowseColumnGroupId;
  readonly columns: readonly BookColumnOption[];
}

export function normalizeBookColumnPreferences(
  saved: readonly TableColumnPreference[] | undefined,
): TableColumnPreference[] {
  const savedByField = new Map(saved?.map(preference => [preference.field, preference]));
  return COLUMNS.map(({key, column}, order) => {
    const preference = savedByField.get(key);
    return {
      field: key,
      visible: column.hideable === false || (preference?.visible ?? column.defaultVisible),
      order: preference?.order ?? order,
    };
  });
}

export function bookColumnOptions(
  preferences: readonly TableColumnPreference[],
): BookColumnOption[] {
  const visibleByField = new Map(
    preferences.map(preference => [preference.field, preference.visible]),
  );
  return COLUMNS.map(({key, labelKey, column}) => ({
    field: key,
    labelKey,
    visible: visibleByField.get(key) === true,
    hideable: column.hideable !== false,
  }));
}

export function bookVisibleColumnOptions(
  preferences: readonly TableColumnPreference[],
): BookColumnOption[] {
  const orderByField = new Map(preferences.map(preference => [preference.field, preference.order]));
  const orderOf = (option: BookColumnOption) =>
    option.field === 'title' ? -1 : orderByField.get(option.field) ?? Number.MAX_SAFE_INTEGER;
  return bookColumnOptions(preferences)
    .filter(option => option.visible)
    .sort((first, second) => orderOf(first) - orderOf(second));
}

export function bookColumnSections(
  options: readonly BookColumnOption[],
): BookColumnSection[] {
  const optionsByField = new Map(options.map(option => [option.field, option]));
  return COLUMN_GROUP_ORDER.map(id => ({
    id,
    columns: COLUMNS.flatMap(({key, column}) => {
      const option = column.group === id ? optionsByField.get(key) : undefined;
      return option ? [option] : [];
    }),
  }));
}

export function bookColumnSortKey(field: string): BookQuerySortKey | undefined {
  return COLUMNS_BY_KEY.get(field)?.sort?.key;
}

export function bookColumnKind(field: string): BookColumnKind {
  return COLUMNS_BY_KEY.get(field)?.column.kind ?? 'text';
}

export function bookColumnSizing(field: BookColumnKey): BookColumnSizing {
  const {defaultWidth, minWidth, maxWidth} = COLUMNS_BY_KEY.get(field)!.column;
  return {
    size: defaultWidth,
    minSize: minWidth ?? DEFAULT_COLUMN_MIN_WIDTH,
    maxSize: maxWidth ?? DEFAULT_COLUMN_MAX_WIDTH,
  };
}

export function bookColumnValue(
  book: BookSummary,
  field: string,
): BookColumnValue {
  return COLUMNS_BY_KEY.get(field)?.column.value(book);
}

export function formatBookValue(
  kind: BookColumnKind,
  value: BookColumnValue,
  translate: (key: string) => string,
): string {
  if (value == null || value === '') {
    return BOOK_EMPTY_VALUE;
  }
  switch (kind) {
    case 'date':
      return formatMediumDate(String(value)) || BOOK_EMPTY_VALUE;
    case 'fileSize':
      return formatFileSizeKb(Number(value));
    case 'rating':
      return Number(value).toFixed(1);
    case 'number':
      return NUMBER_FORMAT.format(Number(value));
    case 'readStatus': {
      const labelKey = typeof value === 'string' ? bookReadStatusLabelKey(value) : null;
      return labelKey ? translate(labelKey) : BOOK_EMPTY_VALUE;
    }
    case 'text':
      return String(value);
  }
}
