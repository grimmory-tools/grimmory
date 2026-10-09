export enum ScrollerType {
  LAST_READ = 'lastRead',
  LAST_LISTENED = 'lastListened',
  LATEST_ADDED = 'latestAdded',
  RECENTLY_FINISHED = 'recentlyFinished',
  RANDOM = 'random',
  LIBRARY = 'library',
  SHELF = 'shelf',
  MAGIC_SHELF = 'magicShelf'
}

export interface ScrollerConfig {
  id: string;
  type: ScrollerType;
  enabled: boolean;
  order: number;
  magicShelfId?: number;
  libraryId?: number;
  shelfId?: number;
  sortField?: string;
  sortDirection?: string;
}

export interface DashboardConfig {
  scrollers: ScrollerConfig[];
}

export const DEFAULT_DASHBOARD_CONFIG: DashboardConfig = {
  scrollers: [
    {id: '1', type: ScrollerType.LAST_LISTENED, enabled: true, order: 1},
    {id: '2', type: ScrollerType.LAST_READ, enabled: true, order: 2},
    {id: '3', type: ScrollerType.LATEST_ADDED, enabled: true, order: 3},
    {id: '4', type: ScrollerType.RANDOM, enabled: true, order: 4}
  ]
};

export function dashboardConfigOrDefault(config: DashboardConfig | undefined): DashboardConfig {
  return config?.scrollers?.length ? config : DEFAULT_DASHBOARD_CONFIG;
}
