import type {AppearancePreference} from '../../model/app-state.model';

export interface AppearanceOption {
  value: AppearancePreference;
  labelKey: string;
  icon: string;
}

export const APPEARANCE_OPTIONS: readonly AppearanceOption[] = [
  {value: 'light', labelKey: 'layout.theme.light', icon: 'pi pi-sun'},
  {value: 'dark', labelKey: 'layout.theme.dark', icon: 'pi pi-moon'},
  {value: 'system', labelKey: 'layout.theme.system', icon: 'pi pi-desktop'},
];
