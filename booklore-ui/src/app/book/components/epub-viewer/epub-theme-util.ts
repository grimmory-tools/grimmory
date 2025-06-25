export class EpubThemeUtil {
  static readonly themesMap = new Map<string, any>([
    ['black', {
      "body": {"background-color": "#000000", "color": "#f9f9f9"},
      "p": {"color": "#f9f9f9"},
      "h1, h2, h3, h4, h5, h6": {"color": "#f9f9f9"},
      "a": {"color": "#f9f9f9"},
      "img": {"-webkit-filter": "invert(1) hue-rotate(180deg)", "filter": "invert(1) hue-rotate(180deg)"},
      "code": {"color": "#00ff00", "background-color": "black"}
    }],
    ['sepia', {
      "body": {"background-color": "#f4ecd8", "color": "#6e4b3a"},
      "p": {"color": "#6e4b3a"},
      "h1, h2, h3, h4, h5, h6": {"color": "#6e4b3a"},
      "a": {"color": "#8b4513"},
      "img": {"-webkit-filter": "sepia(1) contrast(1.5)", "filter": "sepia(1) contrast(1.5)"},
      "code": {"color": "#8b0000", "background-color": "#f4ecd8"}
    }],
    ['white', {
      "body": {"background-color": "#ffffff", "color": "#000000"},
      "p": {"color": "#000000"},
      "h1, h2, h3, h4, h5, h6": {"color": "#000000"},
      "a": {"color": "#000000"},
      "img": {"-webkit-filter": "none", "filter": "none"},
      "code": {"color": "#d14", "background-color": "#f5f5f5"}
    }],
    ['grey', {
      "body": {"background-color": "#404040", "color": "#d3d3d3"},
      "p": {"color": "#d3d3d3"},
      "h1, h2, h3, h4, h5, h6": {"color": "#d3d3d3"},
      "a": {"color": "#1e90ff"},
      "img": {"filter": "none"},
      "code": {"color": "#d14", "background-color": "#585858"}
    }]
  ]);

  static applyTheme(rendition: any, themeKey: string, fontFamily?: string, fontSize?: number, lineHeight?: number, letterSpacing?: number): void {
    if (!rendition) return;

    const baseTheme = this.themesMap.get(themeKey ?? 'black') ?? {};
    const combined = {
      ...baseTheme,
      body: {
        ...baseTheme.body,
        'font-family': fontFamily,
        'font-size': `${fontSize ?? 100}%`,
        'line-height': lineHeight,
        'letter-spacing': `${letterSpacing}em`
      },
      '*': {
        ...baseTheme['*'],
        'line-height': lineHeight,
        'letter-spacing': `${letterSpacing}em`
      }
    };

    rendition.themes.register('custom', combined);
    rendition.themes.select('custom');
  }
}
