export const THEME_REGISTRY = [
  {name: 'grimmory', label: 'Grimmory'},
  {name: 'cobalt', label: 'Cobalt'},
  {name: 'ember', label: 'Ember'},
  {name: 'crimson', label: 'Crimson'},
  {name: 'rose', label: 'Rose'},
  {name: 'forest', label: 'Forest'},
  {name: 'meadow', label: 'Meadow'},
  {name: 'teal', label: 'Teal'},
  {name: 'lagoon', label: 'Lagoon'},
  {name: 'violet', label: 'Violet'},
  {name: 'fuchsia', label: 'Fuchsia'},
  {name: 'slate', label: 'Slate'},
  {name: 'custom', label: 'Custom'},
] as const;

export type AppTheme = (typeof THEME_REGISTRY)[number]['name'];

export const DEFAULT_APP_THEME: AppTheme = 'grimmory';
export const APP_THEME_OPTIONS = THEME_REGISTRY;

export const CUSTOM_PRIMARY_OPTIONS = [
  'red', 'orange', 'amber', 'yellow', 'lime', 'green', 'emerald', 'teal',
  'cyan', 'sky', 'blue', 'indigo', 'violet', 'purple', 'fuchsia', 'pink', 'rose',
] as const;

export type CustomPrimary = (typeof CUSTOM_PRIMARY_OPTIONS)[number];

export const DEFAULT_CUSTOM_PRIMARY: CustomPrimary = 'orange';
