export interface SelectOption<T> {
  readonly label: string;
  readonly value: T;
  readonly disabled?: boolean;
}

export interface SelectOptionGroup<T> {
  readonly label: string;
  readonly options: readonly SelectOption<T>[];
}

export type SelectCompareWith<T> = (optionValue: T, selectedValue: T) => boolean;
