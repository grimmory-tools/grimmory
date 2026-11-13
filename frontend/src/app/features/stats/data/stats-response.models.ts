import {type BOOK_FILE_TYPES, type BookReadStatus} from '../../book/data/book-response.models';

export const STATS_PAGE_BANDS = [
  'up-to-100',
  '101-to-200',
  '201-to-300',
  '301-to-500',
  '501-to-750',
  '751-to-1000',
  'over-1000',
] as const;

export type StatsPageBand = (typeof STATS_PAGE_BANDS)[number];

export type StatsMetadataBand = 'very-poor' | 'poor' | 'fair' | 'good' | 'excellent';

export type StatsFinishBand = 'under-a-week' | '1-to-2-weeks' | '2-to-4-weeks' | '1-to-2-months' | '2-to-3-months' | '3-months-or-more';

export type StatsRatingLevel = 'low' | 'mid' | 'high';
export type StatsStatusGroup = 'read' | 'reading' | 'paused' | 'abandoned' | 'unread';

export interface StatsDistributionValues {
  readonly format: string;
  readonly language: string;
  readonly metadata_score: StatsMetadataBand;
  readonly page_band: StatsPageBand;
  readonly publication_year: number;
  readonly status: BookReadStatus;
  readonly rating: number;
  readonly stopped: number;
}

export type StatsDistributionDimension = keyof StatsDistributionValues;

export interface StatsCount<V> {
  readonly value: V | null;
  readonly books: number;
}

export interface StatsDistribution<V> {
  readonly rows: readonly StatsCount<V>[];
  readonly other?: number;
  readonly average?: number;
}

export interface StatsRows<T> {
  readonly rows: readonly T[];
}

export interface StatsLengthCell {
  readonly band: StatsPageBand;
  readonly rating: number;
  readonly books: number;
}

export interface StatsComparisonCell {
  readonly outside: number;
  readonly rating: number;
  readonly books: number;
  readonly gapSum: number;
}

export interface StatsDecadeCell {
  readonly decade: number;
  readonly rating: number;
  readonly books: number;
}

export interface StatsYearFlow {
  readonly year: number | null;
  readonly group: StatsStatusGroup;
  readonly books: number;
}

export interface StatsRatingFlow {
  readonly group: StatsStatusGroup;
  readonly level: StatsRatingLevel | null;
  readonly books: number;
}

export interface StatsBookFlow {
  readonly byYear: readonly StatsYearFlow[];
  readonly byRating: readonly StatsRatingFlow[];
}

export interface StatsSlot {
  readonly weekday: number;
  readonly hour: number;
  readonly seconds: number;
}

export interface StatsSlots {
  readonly rows: readonly StatsSlot[];
  readonly days: readonly number[];
}

export interface StatsDayCount {
  readonly day: string;
  readonly sessions: number;
}

export interface StatsGenreTime {
  readonly genre: string;
  readonly seconds: number;
}

export interface StatsWeekSession {
  readonly bookId: number;
  readonly title: string;
  readonly format: (typeof BOOK_FILE_TYPES)[number];
  readonly start: string;
  readonly end: string;
}

export interface StatsWeek {
  readonly rows: readonly StatsWeekSession[];
  readonly truncated: boolean;
}

export interface StatsFinishedBook {
  readonly title: string;
  readonly days: number;
}

export interface StatsFinishCount {
  readonly band: StatsFinishBand;
  readonly books: number;
  readonly titles: readonly StatsFinishedBook[];
}

export interface StatsFinishTimes {
  readonly rows: readonly StatsFinishCount[];
  readonly median: number | null;
  readonly quickest: StatsFinishedBook | null;
  readonly longest: StatsFinishedBook | null;
}

export interface StatsStatusCount {
  readonly status: BookReadStatus;
  readonly books: number;
}

export const STATS_ITEM_DIMENSIONS = ['author', 'genre', 'series', 'publisher', 'tag', 'mood'] as const;

export type StatsItemDimension = (typeof STATS_ITEM_DIMENSIONS)[number];

export const STATS_ITEM_MEASURES = ['books', 'read', 'rating'] as const;

export type StatsItemMeasure = (typeof STATS_ITEM_MEASURES)[number];

export interface StatsTopName {
  readonly name: string;
  readonly books: number;
  readonly statuses: readonly StatsStatusCount[];
  readonly rating: number | null;
}

export interface StatsActivityMonth {
  readonly month: string;
  readonly added: number;
  readonly finished: number;
  readonly finishedPages: number;
  readonly backlogChange: number;
}

export type SeriesState = 'in-progress' | 'paused' | 'not-started' | 'completed' | 'abandoned';
export type SeriesStateFilter = 'all' | SeriesState;
export type SeriesSort = 'progress' | 'rating' | 'books' | 'name';

export interface SeriesBar {
  readonly series: string;
  readonly books: number;
  readonly read: number;
  readonly reading: number;
  readonly partiallyRead: number;
  readonly paused: number;
  readonly abandoned: number;
  readonly wontRead: number;
  readonly unread: number;
  readonly rating: number | null;
}

export interface SeriesSummary {
  readonly top: readonly SeriesBar[];
  readonly total: number;
  readonly averageCompletion: number | null;
  readonly measured: number;
  readonly completed: number;
  readonly inProgress: number;
  readonly notStarted: number;
}

export interface SeriesRow {
  readonly series: string;
  readonly state: SeriesState;
  readonly books: number;
  readonly read: number;
  readonly reading: number;
  readonly intended: number;
  readonly completion: number | null;
  readonly rating: number | null;
  readonly nextUnread: string | null;
}

export interface SeriesPage {
  readonly rows: readonly SeriesRow[];
  readonly matching: number;
}

export interface ProfileRow {
  readonly profile: 'dna' | 'habits';
  readonly trait: string;
  readonly score: number;
}

export interface StatsStreaks {
  readonly current: number;
  readonly longest: number;
  readonly readingDays: number;
}

export interface StatsDays {
  readonly rows: readonly StatsDayCount[];
  readonly streaks: StatsStreaks;
}

export const NO_ROWS = {rows: []} as const;
export const NO_SLOTS: StatsSlots = {rows: [], days: [0, 0, 0, 0, 0, 0, 0]};
export const NO_BOOK_FLOW: StatsBookFlow = {byYear: [], byRating: []};
export const NO_WEEK: StatsWeek = {rows: [], truncated: false};
export const NO_DAYS: StatsDays = {rows: [], streaks: {current: 0, longest: 0, readingDays: 0}};
export const NO_FINISH_TIMES: StatsFinishTimes = {rows: [], median: null, quickest: null, longest: null};
export const NO_SERIES: SeriesSummary = {top: [], total: 0, averageCompletion: null, measured: 0, completed: 0, inProgress: 0, notStarted: 0};
