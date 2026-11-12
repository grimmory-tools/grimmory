import {BookFormatsChartComponent} from '../component/library-stats/charts/book-formats-chart/book-formats-chart.component';
import {LanguageChartComponent} from '../component/library-stats/charts/language-chart/language-chart.component';
import {MetadataScoreChartComponent} from '../component/library-stats/charts/metadata-score-chart/metadata-score-chart.component';
import {PageCountChartComponent} from '../component/library-stats/charts/page-count-chart/page-count-chart.component';
import {PublicationTimelineChartComponent} from '../component/library-stats/charts/publication-timeline-chart/publication-timeline-chart.component';
import {TopItemsChartComponent} from '../component/library-stats/charts/top-items-chart/top-items-chart.component';
import {knownValues} from '../component/shared/stats-chart-data';
import {defineStatsChart, type StatsChartDefinition} from '../card/stats-chart-definition';
import {NO_ROWS} from '../data/stats-response.models';

export const LIBRARY_STATS_CHARTS: readonly StatsChartDefinition[] = [
  defineStatsChart({
    id: 'library.bookFormats',
    nameKey: 'statsLibrary.chartNames.bookFormats',
    titleKey: 'statsLibrary.bookFormats.title',
    size: 'small',
    filter: 'none',
    query: (params, stats) => stats.distribution(params, 'format'),
    empty: NO_ROWS,
    isEmpty: formats => formats.rows.length === 0,
    chart: BookFormatsChartComponent,
  }),
  defineStatsChart({
    id: 'library.languages',
    nameKey: 'statsLibrary.chartNames.languages',
    titleKey: 'statsLibrary.languages.title',
    size: 'small',
    filter: 'none',
    query: (params, stats) => stats.distribution(params, 'language'),
    empty: NO_ROWS,
    select: languages => knownValues(languages),
    isEmpty: ({rows}) => rows.length === 0,
    chart: LanguageChartComponent,
  }),
  defineStatsChart({
    id: 'library.metadataScore',
    nameKey: 'statsLibrary.chartNames.metadataScore',
    titleKey: 'statsLibrary.metadataScore.title',
    size: 'small',
    filter: 'none',
    query: (params, stats) => stats.distribution(params, 'metadata_score'),
    empty: NO_ROWS,
    select: scores => knownValues(scores),
    isEmpty: ({rows}) => rows.length === 0,
    chart: MetadataScoreChartComponent,
  }),
  defineStatsChart({
    id: 'library.pageCount',
    nameKey: 'statsLibrary.chartNames.pageCount',
    titleKey: 'statsLibrary.pageCount.title',
    size: 'medium',
    filter: 'none',
    query: (params, stats) => stats.distribution(params, 'page_band'),
    empty: NO_ROWS,
    select: bands => knownValues(bands),
    isEmpty: ({rows}) => rows.length === 0,
    chart: PageCountChartComponent,
  }),
  defineStatsChart({
    id: 'library.publicationTimeline',
    nameKey: 'statsLibrary.chartNames.publicationTimeline',
    titleKey: 'statsLibrary.publicationTimeline.title',
    size: 'medium',
    filter: 'none',
    query: (params, stats) => stats.distribution(params, 'publication_year'),
    empty: NO_ROWS,
    select: years => knownValues(years),
    isEmpty: ({rows}) => rows.length === 0,
    chart: PublicationTimelineChartComponent,
  }),
  defineStatsChart({
    id: 'library.topItems',
    nameKey: 'statsLibrary.chartNames.topItems',
    titleKey: 'statsLibrary.topItems.title',
    size: 'full',
    filter: 'item',
    query: (params, stats) => stats.top(params, params.item, params.by),
    empty: NO_ROWS,
    select: (items, {item, by}) => ({item, by, rows: items.rows}),
    isEmpty: ({rows}) => rows.length === 0,
    chart: TopItemsChartComponent,
  }),
];
