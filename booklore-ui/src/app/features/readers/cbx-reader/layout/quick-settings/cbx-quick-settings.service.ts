import {Injectable, signal} from '@angular/core';
import {Subject} from 'rxjs';
import {CbxBackgroundColor, CbxFitMode, CbxMagnifierLensSize, CbxMagnifierZoom, CbxPageSpread, CbxPageViewMode, CbxScrollMode, CbxReadingDirection, CbxSlideshowInterval} from '../../../../settings/user-management/user.service';

export interface CbxQuickSettingsState {
  fitMode: CbxFitMode;
  scrollMode: CbxScrollMode;
  pageViewMode: CbxPageViewMode;
  pageSpread: CbxPageSpread;
  backgroundColor: CbxBackgroundColor;
  readingDirection: CbxReadingDirection;
  slideshowInterval: CbxSlideshowInterval;
  magnifierZoom: CbxMagnifierZoom;
  magnifierLensSize: CbxMagnifierLensSize;
}

@Injectable()
export class CbxQuickSettingsService {
  private readonly defaultState: CbxQuickSettingsState = {
    fitMode: CbxFitMode.FIT_PAGE,
    scrollMode: CbxScrollMode.PAGINATED,
    pageViewMode: CbxPageViewMode.SINGLE_PAGE,
    pageSpread: CbxPageSpread.ODD,
    backgroundColor: CbxBackgroundColor.GRAY,
    readingDirection: CbxReadingDirection.LTR,
    slideshowInterval: CbxSlideshowInterval.FIVE_SECONDS,
    magnifierZoom: CbxMagnifierZoom.ZOOM_3X,
    magnifierLensSize: CbxMagnifierLensSize.MEDIUM
  };

  private readonly _state = signal<CbxQuickSettingsState>(this.defaultState);
  readonly state = this._state.asReadonly();

  private readonly _visible = signal(false);
  readonly visible = this._visible.asReadonly();

  private _fitModeChange = new Subject<CbxFitMode>();
  fitModeChange$ = this._fitModeChange.asObservable();

  private _scrollModeChange = new Subject<CbxScrollMode>();
  scrollModeChange$ = this._scrollModeChange.asObservable();

  private _pageViewModeChange = new Subject<CbxPageViewMode>();
  pageViewModeChange$ = this._pageViewModeChange.asObservable();

  private _pageSpreadChange = new Subject<CbxPageSpread>();
  pageSpreadChange$ = this._pageSpreadChange.asObservable();

  private _backgroundColorChange = new Subject<CbxBackgroundColor>();
  backgroundColorChange$ = this._backgroundColorChange.asObservable();

  private _readingDirectionChange = new Subject<CbxReadingDirection>();
  readingDirectionChange$ = this._readingDirectionChange.asObservable();

  private _slideshowIntervalChange = new Subject<CbxSlideshowInterval>();
  slideshowIntervalChange$ = this._slideshowIntervalChange.asObservable();

  private _magnifierZoomChange = new Subject<CbxMagnifierZoom>();
  magnifierZoomChange$ = this._magnifierZoomChange.asObservable();

  private _magnifierLensSizeChange = new Subject<CbxMagnifierLensSize>();
  magnifierLensSizeChange$ = this._magnifierLensSizeChange.asObservable();

  show(): void {
    this._visible.set(true);
  }

  close(): void {
    this._visible.set(false);
  }

  updateState(partial: Partial<CbxQuickSettingsState>): void {
    this._state.update(current => ({...current, ...partial}));
  }

  setFitMode(mode: CbxFitMode): void {
    this.updateState({fitMode: mode});
  }

  setScrollMode(mode: CbxScrollMode): void {
    this.updateState({scrollMode: mode});
  }

  setPageViewMode(mode: CbxPageViewMode): void {
    this.updateState({pageViewMode: mode});
  }

  setPageSpread(spread: CbxPageSpread): void {
    this.updateState({pageSpread: spread});
  }

  setBackgroundColor(color: CbxBackgroundColor): void {
    this.updateState({backgroundColor: color});
  }

  setReadingDirection(direction: CbxReadingDirection): void {
    this.updateState({readingDirection: direction});
  }

  setSlideshowInterval(interval: CbxSlideshowInterval): void {
    this.updateState({slideshowInterval: interval});
  }

  setMagnifierZoom(zoom: CbxMagnifierZoom): void {
    this.updateState({magnifierZoom: zoom});
  }

  setMagnifierLensSize(size: CbxMagnifierLensSize): void {
    this.updateState({magnifierLensSize: size});
  }

  // Actions emitted from component
  emitFitModeChange(mode: CbxFitMode): void {
    this._fitModeChange.next(mode);
  }

  emitScrollModeChange(mode: CbxScrollMode): void {
    this._scrollModeChange.next(mode);
  }

  emitPageViewModeChange(mode: CbxPageViewMode): void {
    this._pageViewModeChange.next(mode);
  }

  emitPageSpreadChange(spread: CbxPageSpread): void {
    this._pageSpreadChange.next(spread);
  }

  emitBackgroundColorChange(color: CbxBackgroundColor): void {
    this._backgroundColorChange.next(color);
  }

  emitReadingDirectionChange(direction: CbxReadingDirection): void {
    this._readingDirectionChange.next(direction);
  }

  emitSlideshowIntervalChange(interval: CbxSlideshowInterval): void {
    this._slideshowIntervalChange.next(interval);
  }

  emitMagnifierZoomChange(zoom: CbxMagnifierZoom): void {
    this._magnifierZoomChange.next(zoom);
  }

  emitMagnifierLensSizeChange(size: CbxMagnifierLensSize): void {
    this._magnifierLensSizeChange.next(size);
  }

  reset(): void {
    this._state.set(this.defaultState);
    this._visible.set(false);
  }
}
