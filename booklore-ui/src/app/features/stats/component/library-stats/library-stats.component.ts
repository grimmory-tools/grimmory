import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {of, Subject} from 'rxjs';
import {catchError, map, startWith, takeUntil} from 'rxjs/operators';
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

interface ChartConfig {
  id: string;
  name: string;
  enabled: boolean;
  category: string;
}

@Component({
  selector: 'app-library-stats',
  standalone: true,
  imports: [
    CommonModule,
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
    ReadingJourneyChartComponent
  ],
  templateUrl: './library-stats.component.html',
  styleUrls: ['./library-stats.component.scss']
})
export class LibraryStatsComponent implements OnInit, OnDestroy {
  private readonly libraryFilterService = inject(LibraryFilterService);
  private readonly librariesSummaryService = inject(LibrariesSummaryService);
  private readonly destroy$ = new Subject<void>();

  public isLoading = true;
  public hasData = false;
  public hasError = false;
  public libraryOptions: LibraryOption[] = [];
  public selectedLibrary: LibraryOption | null = null;
  public showConfigPanel = false;

  public chartsConfig: ChartConfig[] = [
    {id: 'bookFormats', name: 'Book Formats', enabled: true, category: 'small'},
    {id: 'languageDistribution', name: 'Languages', enabled: true, category: 'small'},
    {id: 'metadataScore', name: 'Metadata Score', enabled: true, category: 'small'},
    {id: 'pageCountDistribution', name: 'Page Count', enabled: true, category: 'small'},
    {id: 'publicationTimeline', name: 'Publication Timeline', enabled: true, category: 'large'},
    {id: 'readingJourney', name: 'Reading Journey', enabled: true, category: 'large'},
    {id: 'topItems', name: 'Top Items (Authors/Categories/etc.)', enabled: true, category: 'large'},
    {id: 'authorUniverse', name: 'Author Universe', enabled: true, category: 'large'},
    {id: 'publicationTrend', name: 'Publication Trend', enabled: true, category: 'xlarge'}
  ];

  booksSummary$ = this.librariesSummaryService.getBooksSummary().pipe(
    catchError(error => {
      console.error('Error loading books summary:', error);
      this.hasError = true;
      return of({totalBooks: 0, totalSizeKb: 0, totalAuthors: 0, totalSeries: 0, totalPublishers: 0});
    })
  );

  public readonly totalBooks$ = this.booksSummary$.pipe(map(summary => summary.totalBooks));
  public readonly totalAuthors$ = this.booksSummary$.pipe(map(summary => summary.totalAuthors));
  public readonly totalSeries$ = this.booksSummary$.pipe(map(summary => summary.totalSeries));
  public readonly totalPublishers$ = this.booksSummary$.pipe(map(summary => summary.totalPublishers));
  public readonly totalSize$ = this.librariesSummaryService.getFormattedSize().pipe(catchError(() => of('0 KB')));

  ngOnInit(): void {
    this.loadLibraryOptions();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onLibraryChange(): void {
    if (!this.selectedLibrary) {
      return;
    }
    const libraryId = this.selectedLibrary.id;
    this.libraryFilterService.setSelectedLibrary(libraryId);
  }

  private loadLibraryOptions(): void {
    this.libraryFilterService.getLibraryOptions()
      .pipe(
        takeUntil(this.destroy$),
        startWith([]),
        catchError(error => {
          console.error('Error loading library options:', error);
          this.hasError = true;
          this.isLoading = false;
          return of([]);
        })
      )
      .subscribe({
        next: (options) => {
          this.libraryOptions = options;
          this.initializeSelectedLibrary(options);
        },
        error: (error) => {
          console.error('Subscription error:', error);
          this.hasError = true;
          this.isLoading = false;
        }
      });
  }

  private initializeSelectedLibrary(options: LibraryOption[]): void {
    if (options.length === 0) {
      this.hasData = false;
      this.isLoading = false;
      return;
    }

    if (!this.selectedLibrary) {
      this.hasData = true;
      this.isLoading = false;
      this.selectedLibrary = options[0];
      this.libraryFilterService.setSelectedLibrary(this.selectedLibrary.id);
    }
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
    this.chartsConfig = [
      {id: 'bookFormats', name: 'Book Formats', enabled: true, category: 'small'},
      {id: 'languageDistribution', name: 'Languages', enabled: true, category: 'small'},
      {id: 'metadataScore', name: 'Metadata Score', enabled: true, category: 'small'},
      {id: 'pageCountDistribution', name: 'Page Count', enabled: true, category: 'small'},
      {id: 'publicationTimeline', name: 'Publication Timeline', enabled: true, category: 'large'},
      {id: 'readingJourney', name: 'Reading Journey', enabled: true, category: 'large'},
      {id: 'topItems', name: 'Top Items (Authors/Categories/etc.)', enabled: true, category: 'large'},
      {id: 'authorUniverse', name: 'Author Universe', enabled: true, category: 'large'},
      {id: 'publicationTrend', name: 'Publication Trend', enabled: true, category: 'xlarge'}
    ];
  }
}
