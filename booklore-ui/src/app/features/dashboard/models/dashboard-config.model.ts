import {DEFAULT_MAX_ITEMS} from '../components/dashboard-settings/dashboard-settings.component';

export enum ScrollerType {
  LAST_READ = 'lastRead',
  LAST_LISTENED = 'lastListened',
  LATEST_ADDED = 'latestAdded',
  RANDOM = 'random',
  MAGIC_SHELF = 'magicShelf'
}

export interface ScrollerConfig {
  id: string;
  type: ScrollerType;
  title: string;
  enabled: boolean;
  order: number;
  maxItems: number;
  magicShelfId?: number;
  sortField?: string;
  sortDirection?: string;
}

export interface DashboardConfig {
  scrollers: ScrollerConfig[];
}

export const DEFAULT_DASHBOARD_CONFIG: DashboardConfig = {
  scrollers: [
    {id: '1', type: ScrollerType.LAST_LISTENED, title: 'dashboard.scroller.continueListening', enabled: true, order: 1, maxItems: DEFAULT_MAX_ITEMS},
    {id: '2', type: ScrollerType.LAST_READ, title: 'dashboard.scroller.continueReading', enabled: true, order: 2, maxItems: DEFAULT_MAX_ITEMS},
    {id: '3', type: ScrollerType.LATEST_ADDED, title: 'dashboard.scroller.recentlyAdded', enabled: true, order: 3, maxItems: DEFAULT_MAX_ITEMS},
    {id: '4', type: ScrollerType.RANDOM, title: 'dashboard.scroller.discoverNew', enabled: true, order: 4, maxItems: DEFAULT_MAX_ITEMS}
  ]
};
