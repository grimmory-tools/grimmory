export interface HeaderFooterVisibilityState {
  headerVisible: boolean;
  footerVisible: boolean;
}

export class ReaderHeaderFooterVisibilityManager {
  private isPinned = false;
  private mouseY: number;

  private readonly HEADER_HEIGHT = 20;
  private readonly FOOTER_HEIGHT = 40;
  private readonly HEADER_TRIGGER_ZONE = 20;
  private readonly FOOTER_TRIGGER_ZONE = 30;

  private headerVisible = false;
  private footerVisible = false;

  private onStateChangeCallback?: (state: HeaderFooterVisibilityState) => void;

  constructor(private windowHeight: number) {
    this.mouseY = windowHeight / 2;
  }

  onStateChange(callback: (state: HeaderFooterVisibilityState) => void): void {
    this.onStateChangeCallback = callback;
  }

  updateWindowHeight(height: number): void {
    this.windowHeight = height;
  }

  handleMouseMove(mouseY: number): void {
    this.mouseY = mouseY;
    this.updateVisibility();
  }

  handleMouseLeave(): void {
    if (!this.isPinned) {
      this.setHeaderVisible(false);
      this.setFooterVisible(false);
      this.notifyStateChange();
    }
  }

  handleHeaderZoneEnter(): void {
    if (!this.isPinned) {
      this.setHeaderVisible(true);
      this.notifyStateChange();
    }
  }

  handleFooterZoneEnter(): void {
    if (!this.isPinned) {
      this.setFooterVisible(true);
      this.notifyStateChange();
    }
  }

  togglePinned(): void {
    this.isPinned = !this.isPinned;
    this.updateVisibility();
  }

  getVisibilityState(): HeaderFooterVisibilityState {
    return {
      headerVisible: this.headerVisible,
      footerVisible: this.footerVisible
    };
  }

  private updateVisibility(): void {
    if (
      this.mouseY <= this.HEADER_TRIGGER_ZONE ||
      (this.mouseY <= this.HEADER_HEIGHT && this.headerVisible) ||
      this.isPinned
    ) {
      this.setHeaderVisible(true);
    } else if (this.mouseY > this.HEADER_HEIGHT) {
      this.setHeaderVisible(this.isPinned);
    }

    const footerTop = this.windowHeight - this.FOOTER_HEIGHT;
    if (
      this.mouseY >= this.windowHeight - this.FOOTER_TRIGGER_ZONE ||
      (this.mouseY >= footerTop && this.footerVisible) ||
      this.isPinned
    ) {
      this.setFooterVisible(true);
    } else if (this.mouseY < footerTop) {
      this.setFooterVisible(this.isPinned);
    }

    this.notifyStateChange();
  }

  private setHeaderVisible(visible: boolean): void {
    this.headerVisible = visible;
  }

  private setFooterVisible(visible: boolean): void {
    this.footerVisible = visible;
  }

  private notifyStateChange(): void {
    if (this.onStateChangeCallback) {
      this.onStateChangeCallback(this.getVisibilityState());
    }
  }
}
