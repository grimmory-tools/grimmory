import { LucideMonitor, LucideMoon, LucideSun, type LucideIconData } from '@lucide/angular';
import type {AppearancePreference} from '../../model/app-state.model';

export interface AppearanceOption {
  value: AppearancePreference;
  labelKey: string;
  icon: LucideIconData;
}

export const APPEARANCE_OPTIONS: readonly AppearanceOption[] = [
  {value: 'light', labelKey: 'layout.theme.light', icon: LucideSun.icon},
  {value: 'dark', labelKey: 'layout.theme.dark', icon: LucideMoon.icon},
  {value: 'system', labelKey: 'layout.theme.system', icon: LucideMonitor.icon},
];
