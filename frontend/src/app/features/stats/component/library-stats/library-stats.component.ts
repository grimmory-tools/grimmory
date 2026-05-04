import {Component, computed, inject} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {CdkDragDrop, DragDropModule, moveItemInArray} from '@angular/cdk/drag-drop';
import {Select} from 'primeng/select';
import {Button} from 'primeng/button';
import {LanguageChartComponent} from './charts/language-chart/language-chart.component';
import {BookFormatsChartComponent} from './charts/book-formats-chart/book-formats-chart.component';
import {MetadataScoreChartComponent} from './charts/metadata-score-chart/metadata-score-chart.component';
import {PageCountChartComponent} from './charts/page-count-chart/page-count-chart.component';
import {TopItemsChartComponent} from './charts/top-items-chart/top-items-chart.component';
import {AuthorUniverseChartComponent} from './charts/author-universe-chart/author-universe-chart.component';
import {PublicationTimelineChartComponent} from './charts/publication-timeline-chart/publication-timeline-chart.component';
import {PublicationTrendChartComponent} from './charts/publication-trend-chart/publication-trend-chart.component';
import {ReadingJourneyChartComponent} from './charts/reading-journey-chart/reading-journey-chart.component';
import {LibrariesSummaryService} from './service/libraries-summary.service';
import {LibraryFilterService, LibraryOption} from './service/library-filter.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {BookService} from '../../../book/service/book.service';
import {LibraryService} from '../../../book/service/library.service';

interface ChartConfig {
  id: string;
  name: string;
  enabled: boolean;
  category: string;
}

import {provideCharts, withDefaultRegisterables} from 'ng2-charts';
import ChartDataLabels from 'chartjs-plugin-datalabels';

@Component({
  selector: 'app-library-stats',
  standalone: true,
  imports: [
    FormsModule,
    Select,
    DragDropModule,
    Button,
    BookFormatsChartComponent,
    LanguageChartComponent,
    MetadataScoreChartComponent,
    PageCountChartComponent,
    TopItemsChartComponent,
    AuthorUniverseChartComponent,
    PublicationTimelineChartComponent,
    PublicationTrendChartComponent,
    ReadingJourneyChartComponent,
    TranslocoDirective
  ],
  providers: [provideCharts(withDefaultRegisterables(ChartDataLabels))],
  templateUrl: './library-stats.component.html',
  styleUrls: ['./library-stats.component.scss']
})
export class LibraryStatsComponent {
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly librariesSummaryService = inject(LibrariesSummaryService);
  private readonly bookService = inject(BookService);
  private readonly libraryService = inject(LibraryService);
  private readonly t = inject(TranslocoService);

  public readonly isLoading = computed(() =>
    this.bookService.isBooksLoading() || this.libraryService.isLibrariesLoading()
  );
  public readonly hasData = computed(() => this.booksSummary().totalBooks > 0);
  public readonly libraryOptions = this.libraryFilterService.libraryOptions;
  public readonly booksSummary = this.librariesSummaryService.booksSummary;
  public readonly totalSize = this.librariesSummaryService.formattedSize;
  public readonly selectedLibrary = computed<LibraryOption | null>(() => {
    const options = this.libraryOptions();

    if (options.length === 0) {
      return null;
    }

    return options.find(option => option.id === this.libraryFilterService.selectedLibrary()) ?? options[0];
  });
  public showConfigPanel = false;

  public chartsConfig: ChartConfig[] = this.buildChartsConfig();

  onLibraryChange(selectedLibrary: LibraryOption | null): void {
    if (!selectedLibrary) {
      return;
    }

    this.libraryFilterService.setSelectedLibrary(selectedLibrary.id);
  }

  public toggleConfigPanel(): void {
    this.showConfigPanel = !this.showConfigPanel;
  }

  public closeConfigPanel(): void {
    this.showConfigPanel = false;
  }

  public toggleChart(chartId: string): void {
    const chart = this.chartsConfig.find(c => c.id === chartId);
    if (chart) {
      chart.enabled = !chart.enabled;
    }
  }

  public isChartEnabled(chartId: string): boolean {
    return this.chartsConfig.find(c => c.id === chartId)?.enabled ?? false;
  }

  public enableAllCharts(): void {
    this.chartsConfig.forEach(chart => chart.enabled = true);
  }

  public disableAllCharts(): void {
    this.chartsConfig.forEach(chart => chart.enabled = false);
  }

  public getChartsByCategory(category: string): ChartConfig[] {
    return this.chartsConfig.filter(chart => chart.category === category);
  }

  public getEnabledChartsSorted(): ChartConfig[] {
    return this.chartsConfig.filter(chart => chart.enabled);
  }

  public onChartReorder(event: CdkDragDrop<ChartConfig[]>): void {
    if (event.previousIndex !== event.currentIndex) {
      moveItemInArray(this.chartsConfig, event.previousIndex, event.currentIndex);
    }
  }

  public resetChartOrder(): void {
    this.chartsConfig = this.buildChartsConfig();
  }

  private buildChartsConfig(): ChartConfig[] {
    return [
      {id: 'bookFormats', name: this.t.translate('statsLibrary.chartNames.bookFormats'), enabled: true, category: 'small'},
      {id: 'languageDistribution', name: this.t.translate('statsLibrary.chartNames.languages'), enabled: true, category: 'small'},
      {id: 'metadataScore', name: this.t.translate('statsLibrary.chartNames.metadataScore'), enabled: true, category: 'small'},
      {id: 'pageCountDistribution', name: this.t.translate('statsLibrary.chartNames.pageCount'), enabled: true, category: 'small'},
      {id: 'publicationTimeline', name: this.t.translate('statsLibrary.chartNames.publicationTimeline'), enabled: true, category: 'large'},
      {id: 'readingJourney', name: this.t.translate('statsLibrary.chartNames.readingJourney'), enabled: true, category: 'large'},
      {id: 'topItems', name: this.t.translate('statsLibrary.chartNames.topItems'), enabled: true, category: 'large'},
      {id: 'authorUniverse', name: this.t.translate('statsLibrary.chartNames.authorUniverse'), enabled: true, category: 'large'},
      {id: 'publicationTrend', name: this.t.translate('statsLibrary.chartNames.publicationTrend'), enabled: true, category: 'xlarge'}
    ];
  }
}
