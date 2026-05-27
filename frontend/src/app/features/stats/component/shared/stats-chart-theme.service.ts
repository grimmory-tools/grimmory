import {DOCUMENT, isPlatformBrowser} from '@angular/common';
import {DestroyRef, inject, Injectable, PLATFORM_ID, signal} from '@angular/core';
import {Chart, ChartConfiguration, registerables} from 'chart.js';
import {ThemeService} from 'ng2-charts';

export interface StatsChartThemeColors {
  text: string;
  textSecondary: string;
  textMuted: string;
  border: string;
  grid: string;
  surface: string;
}

let cachedSignature = '';
let cachedColors: StatsChartThemeColors | null = null;
let chartDefaultsRegistered = false;

@Injectable({
  providedIn: 'root',
})
export class StatsChartThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  private readonly ngChartsThemeService = inject(ThemeService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly revision = signal(0);
  readonly themeRevision = this.revision.asReadonly();
  private observer: MutationObserver | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => this.observer?.disconnect());
  }

  activate(): void {
    if (!isPlatformBrowser(this.platformId) || this.observer) {
      return;
    }

    this.applyTheme();
    this.observer = new MutationObserver(() => this.applyTheme());
    this.observer.observe(this.document.documentElement, {
      attributes: true,
      attributeFilter: ['class', 'data-app-theme', 'style'],
    });
  }

  colors(): StatsChartThemeColors {
    this.revision();
    return readStatsChartThemeColors();
  }

  private applyTheme(): void {
    this.ngChartsThemeService.setColorschemesOptions(buildStatsChartThemeOptions());
    this.revision.update((value) => value + 1);
  }
}

function buildStatsChartThemeOptions(): ChartConfiguration['options'] {
  const colors = readStatsChartThemeColors();

  applyStatsChartDefaults(colors);

  return {
    color: colors.textSecondary,
    backgroundColor: colors.surface,
    borderColor: colors.grid,
    plugins: {
      legend: {labels: {color: colors.textSecondary}},
      tooltip: {
        backgroundColor: colors.surface,
        titleColor: colors.text,
        bodyColor: colors.textSecondary,
        borderColor: colors.border,
        borderWidth: 1,
      },
      datalabels: {color: colors.text},
    },
  };
}

function applyStatsChartDefaults(colors: StatsChartThemeColors): void {
  if (!chartDefaultsRegistered) {
    Chart.register(...registerables);
    chartDefaultsRegistered = true;
  }

  Chart.defaults.color = colors.textSecondary;
  Chart.defaults.backgroundColor = colors.surface;
  Chart.defaults.borderColor = colors.grid;

  Object.assign(Chart.defaults.elements.arc, {
    borderColor: 'transparent',
    borderWidth: 0,
    hoverBorderColor: colors.textSecondary,
    hoverBorderWidth: 2,
  });

  Object.assign(Chart.defaults.elements.point, {
    backgroundColor: colors.surface,
    hoverBorderColor: colors.textSecondary,
  });

  Object.assign(Chart.defaults.scale.ticks, {
    color: colors.textSecondary,
    backdropColor: 'transparent',
  });
  Object.assign(Chart.defaults.scale.grid, {color: colors.grid});
  Object.assign(Chart.defaults.scales.radialLinear.angleLines, {color: colors.grid});
  Object.assign(Chart.defaults.scales.radialLinear.pointLabels, {color: colors.textSecondary});
  Object.assign(Chart.defaults.scales.radialLinear.ticks, {
    color: colors.textSecondary,
    backdropColor: 'transparent',
  });
}

export function readStatsChartThemeColors(): StatsChartThemeColors {
  if (typeof document === 'undefined' || !document.body) {
    throw new Error('Stats chart theme colors require browser CSS theme tokens.');
  }

  const root = document.documentElement;
  const signature = [
    root.dataset['appTheme'] ?? '',
    root.className,
    root.getAttribute('style') ?? '',
  ].join('|');

  if (signature === cachedSignature && cachedColors) {
    return cachedColors;
  }

  const text = resolveCssColor('--color-text');
  const textSecondary = resolveCssColor('--color-text-secondary');
  const textMuted = resolveCssColor('--color-text-muted');
  const border = resolveCssColor('--color-border');
  const surface = resolveCssColor('--color-card');

  cachedSignature = signature;
  cachedColors = {text, textSecondary, textMuted, border, grid: border, surface};

  return cachedColors;
}

function resolveCssColor(variableName: string): string {
  const probe = document.createElement('span');
  probe.style.color = `var(${variableName})`;
  probe.style.display = 'none';
  document.body.appendChild(probe);
  const resolved = getComputedStyle(probe).color;
  probe.remove();

  if (!resolved) {
    throw new Error(`Stats chart theme token ${variableName} could not be resolved.`);
  }

  return resolved;
}
