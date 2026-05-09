import {Signal, computed, signal} from '@angular/core';
import {LocalStorageService} from '../service/local-storage.service';
import {scaleForGridColumns} from './virtual-grid.util';

const FIXED_COLUMN_GAP = 8;

type GridDensityDirection = 'smaller' | 'larger';

interface GridDensityOptions {
  useFixedColumns: Signal<boolean>;
  screenWidth: Signal<number>;
  storageKey: string;
  defaultColumns: number;
  minColumns: number;
  maxColumns: number;
  scale: Signal<number>;
  minScale: number;
  maxScale: number;
  gap: number;
  baseWidth: Signal<number>;
  setScale: (scale: number) => void;
}

interface GridDensityGrid {
  gridColumns: Signal<number>;
  viewportWidth: Signal<number>;
  updatePreservingScrollPosition: (update: () => void) => void;
}

export function createGridDensity(localStorageService: LocalStorageService, options: GridDensityOptions) {
  const defaultColumns = clampColumns(options.defaultColumns, options.minColumns, options.maxColumns);
  const toColumns = (value: unknown) => {
    const columns = Number(value);
    return Number.isFinite(columns)
      ? clampColumns(columns, options.minColumns, options.maxColumns)
      : defaultColumns;
  };
  const savedColumns = localStorageService.get<unknown>(options.storageKey);
  const fixedColumns = signal(savedColumns === null ? defaultColumns : toColumns(savedColumns));

  const adjustFixedColumns = (direction: GridDensityDirection): void => {
    const columns = toColumns(fixedColumns() + (direction === 'smaller' ? 1 : -1));
    fixedColumns.set(columns);
    localStorageService.set(options.storageKey, columns);
  };

  const adjustScale = (direction: GridDensityDirection, grid: GridDensityGrid): void => {
    const columns = Math.max(1, grid.gridColumns() + (direction === 'smaller' ? 1 : -1));
    const viewportWidth = grid.viewportWidth() || options.screenWidth();
    grid.updatePreservingScrollPosition(() => {
      options.setScale(scaleForGridColumns(
        viewportWidth,
        options.gap,
        columns,
        options.baseWidth(),
        options.minScale,
        options.maxScale
      ));
    });
  };

  return {
    gap: computed(() => options.useFixedColumns() ? FIXED_COLUMN_GAP : options.gap),
    columns: computed(() => options.useFixedColumns() ? fixedColumns() : undefined),
    smallerDisabled: computed(() => options.useFixedColumns()
      ? fixedColumns() >= options.maxColumns
      : options.scale() <= options.minScale
    ),
    largerDisabled: computed(() => options.useFixedColumns()
      ? fixedColumns() <= options.minColumns
      : options.scale() >= options.maxScale
    ),
    adjust: (direction: GridDensityDirection, grid: GridDensityGrid) =>
      options.useFixedColumns() ? adjustFixedColumns(direction) : adjustScale(direction, grid),
  };
}

function clampColumns(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, Math.round(value)));
}
