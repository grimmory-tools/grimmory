export interface PageInfo {
  percentCompleted: number;
  sectionTimeText: string;
}

export interface ThemeInfo {
  fg: string;
  bg: string;
}

export class PageDecorator {
  private static readonly DEFAULT_FONT_SIZE = '0.875rem';

  static updateHeadersAndFooters(renderer: any, chapterName: string, pageInfo?: PageInfo, theme?: ThemeInfo, timeRemainingLabel?: string): void {

    if (!renderer) {
      return;
    }

    const columnCount = renderer.heads?.length || 0;
    const isSingleColumn = columnCount === 1;

    this.updateHeaders(renderer, chapterName, isSingleColumn, theme);
    this.updateFooters(renderer, pageInfo, isSingleColumn, theme, timeRemainingLabel);
  }

  private static updateHeaders(renderer: any, chapterName: string, isSingleColumn: boolean, theme?: ThemeInfo): void {
    if (!renderer.heads || !Array.isArray(renderer.heads) || renderer.heads.length === 0) {
      return;
    }

    const headerStyle = this.buildStyle(theme);

    renderer.heads.forEach((headElement: HTMLElement, index: number) => {
      if (headElement) {
        headElement.style.visibility = 'visible';
        const headerContent = this.createHeaderContent(chapterName, isSingleColumn, index, headerStyle);
        headElement.replaceChildren(headerContent);
      }
    });
  }

  private static updateFooters(renderer: any, pageInfo: PageInfo | undefined, isSingleColumn: boolean, theme?: ThemeInfo, timeRemainingLabel?: string): void {
    if (!renderer.feet || !Array.isArray(renderer.feet) || renderer.feet.length === 0 || !pageInfo) {
      return;
    }

    const footerStyle = this.buildStyle(theme);

    renderer.feet.forEach((footElement: HTMLElement, index: number) => {
      if (footElement) {
        const footerContent = this.createFooterContent(pageInfo, isSingleColumn, index, renderer.feet.length, footerStyle, timeRemainingLabel);
        footElement.replaceChildren(footerContent);
      }
    });
  }

  private static buildStyle(theme?: ThemeInfo): string {
    const baseStyle = `width: 100%; display: flex; justify-content: space-between; align-items: center; font-size: ${this.DEFAULT_FONT_SIZE}; font-family: inherit;`;
    if (theme) {
      return `${baseStyle} color: ${theme.fg};`;
    }
    return baseStyle;
  }

  private static createHeaderContent(chapterName: string, isSingleColumn: boolean, index: number, style: string): HTMLElement {
    const headerContent = document.createElement('div');
    headerContent.style.cssText = style;

    if (isSingleColumn) {
      const spacer = document.createElement('span');
      const chapterSpan = document.createElement('span');
      chapterSpan.textContent = chapterName || '';
      chapterSpan.style.textAlign = 'right';
      headerContent.style.justifyContent = 'left';

      headerContent.appendChild(spacer);
      headerContent.appendChild(chapterSpan);
    } else {
      if (index === 0) {
        const chapterSpan = document.createElement('span');
        chapterSpan.textContent = chapterName || '';
        chapterSpan.style.textAlign = 'left';
        headerContent.appendChild(chapterSpan);
      }
    }

    return headerContent;
  }

  private static createFooterContent(pageInfo: PageInfo, isSingleColumn: boolean, index: number, totalColumns: number, style: string, timeRemainingLabel?: string): HTMLElement {
    const footerContent = document.createElement('div');
    footerContent.style.cssText = style;

    const text = timeRemainingLabel ?? ('Time remaining in section: ' + (pageInfo.sectionTimeText ?? '0s'));

    if (isSingleColumn) {
      const timeSpan = document.createElement('span');
      timeSpan.textContent = text;
      timeSpan.style.textAlign = 'left';

      const progressSpan = document.createElement('span');
      progressSpan.textContent = `${pageInfo.percentCompleted}%`;
      progressSpan.style.textAlign = 'right';

      footerContent.appendChild(timeSpan);
      footerContent.appendChild(progressSpan);
    } else {
      if (index === 0) {
        const timeSpan = document.createElement('span');
        timeSpan.textContent = text;
        timeSpan.style.textAlign = 'left';
        footerContent.appendChild(timeSpan);

        const spacer = document.createElement('span');
        footerContent.appendChild(spacer);
      } else if (index === totalColumns - 1) {
        const spacer = document.createElement('span');
        footerContent.appendChild(spacer);

        const progressSpan = document.createElement('span');
        progressSpan.textContent = `${pageInfo.percentCompleted}%`;
        progressSpan.style.textAlign = 'right';
        footerContent.appendChild(progressSpan);
      }
    }

    return footerContent;
  }
}
