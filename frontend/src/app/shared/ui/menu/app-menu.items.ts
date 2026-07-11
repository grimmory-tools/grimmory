import { type MenuItem } from 'primeng/api';

export type AppMenuItem = MenuItem;

export interface AppMenuSectionItem extends MenuItem {
  appMenuSection: true;
}

export const appMenuSection = (label: string): AppMenuSectionItem => ({
  label,
  appMenuSection: true,
});

export const appMenuSeparator = (): MenuItem => ({
  separator: true,
});
