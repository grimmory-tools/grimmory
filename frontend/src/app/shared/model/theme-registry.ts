export const THEME_REGISTRY = [
  {name: 'grimmory', labelKey: 'layout.theme.options.grimmory'},
  {name: 'cobalt', labelKey: 'layout.theme.options.cobalt'},
  {name: 'ember', labelKey: 'layout.theme.options.ember'},
  {name: 'crimson', labelKey: 'layout.theme.options.crimson'},
  {name: 'rose', labelKey: 'layout.theme.options.rose'},
  {name: 'forest', labelKey: 'layout.theme.options.forest'},
  {name: 'meadow', labelKey: 'layout.theme.options.meadow'},
  {name: 'teal', labelKey: 'layout.theme.options.teal'},
  {name: 'lagoon', labelKey: 'layout.theme.options.lagoon'},
  {name: 'violet', labelKey: 'layout.theme.options.violet'},
  {name: 'fuchsia', labelKey: 'layout.theme.options.fuchsia'},
  {name: 'slate', labelKey: 'layout.theme.options.slate'},
  {name: 'custom', labelKey: 'layout.theme.options.custom'},
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
