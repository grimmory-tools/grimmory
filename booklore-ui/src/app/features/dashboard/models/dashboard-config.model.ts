import {DEFAULT_MAX_ITEMS} from '../components/dashboard-settings/dashboard-settings.component';

export enum ScrollerType {
  LAST_READ = 'lastRead',
  LAST_LISTENED = 'lastListened',
  LATEST_ADDED = 'latestAdded',
  RANDOM = 'random',
  MAGIC_SHELF = 'magicShelf',
  UP_NEXT = 'upNext',
  READ_AGAIN = 'readAgain',
  RECOMMENDATIONS = 'recommendations'
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
  upNextShowFirstUnread?: boolean;
  readAgainSortByFinished?: boolean;
}

export interface DashboardConfig {
  scrollers: ScrollerConfig[];
}

export const DEFAULT_DASHBOARD_CONFIG: DashboardConfig = {
  scrollers: [
    {id: '1', type: ScrollerType.LAST_READ, title: 'dashboard.scroller.continueReading', enabled: true, order: 1, maxItems: DEFAULT_MAX_ITEMS},
    {id: '2', type: ScrollerType.LAST_LISTENED, title: 'dashboard.scroller.continueListening', enabled: false, order: 2, maxItems: DEFAULT_MAX_ITEMS},
    {id: '3', type: ScrollerType.LATEST_ADDED, title: 'dashboard.scroller.recentlyAdded', enabled: true, order: 3, maxItems: DEFAULT_MAX_ITEMS},
    {id: '4', type: ScrollerType.RANDOM, title: 'dashboard.scroller.discoverNew', enabled: true, order: 4, maxItems: DEFAULT_MAX_ITEMS},
    {id: '5', type: ScrollerType.UP_NEXT, title: 'dashboard.scroller.upNext', enabled: true, order: 5, maxItems: DEFAULT_MAX_ITEMS, upNextShowFirstUnread: false},
    {id: '6', type: ScrollerType.READ_AGAIN, title: 'dashboard.scroller.readAgain', enabled: false, order: 6, maxItems: DEFAULT_MAX_ITEMS, readAgainSortByFinished: false},
    {id: '7', type: ScrollerType.RECOMMENDATIONS, title: 'dashboard.scroller.recommendations', enabled: true, order: 7, maxItems: DEFAULT_MAX_ITEMS}
  ]
};
