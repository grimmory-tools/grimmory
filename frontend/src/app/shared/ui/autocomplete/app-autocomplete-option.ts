export interface AppAutocompleteOption<T = unknown> {
  value: string;
  label: string;
  data?: T;
}

export type AppAutocompleteSuggestion = string | AppAutocompleteOption;

export function toAutocompleteOption(suggestion: AppAutocompleteSuggestion): AppAutocompleteOption {
  return typeof suggestion === 'string' ? { value: suggestion, label: suggestion } : suggestion;
}
