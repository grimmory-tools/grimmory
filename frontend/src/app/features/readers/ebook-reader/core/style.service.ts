import {inject, Injectable} from '@angular/core';
import {ReaderState} from '../state/reader-state.service';
import {EpubCustomFontService} from '../features/fonts/custom-font.service';

@Injectable({
  providedIn: 'root'
})
export class ReaderStyleService {
  private epubCustomFontService = inject(EpubCustomFontService);

  generateCSS(state: ReaderState): string {
    const {lineHeight, justify, hyphenate, fontSize, theme, fontFamily} = state;
    const userStylesheet = '';
    const overrideFont = false;
    const mediaActiveClass = 'media-active';

    let fontFaceRule = '';
    let actualFontFamily = null;

    if (fontFamily !== null) {
      const customFontId = this.parseCustomFontId(fontFamily);
      if (customFontId !== null) {
        const customFont = this.epubCustomFontService.getCustomFontById(customFontId);
        const blobUrl = this.epubCustomFontService.getBlobUrl(customFontId);
        if (customFont && blobUrl) {
          const sanitizedFontName = this.epubCustomFontService.sanitizeFontName(customFont.fontName);
          actualFontFamily = `"${sanitizedFontName}", sans-serif`;
          fontFaceRule = `@font-face {
              font-family: "${sanitizedFontName}";
              src: url("${blobUrl}") format("truetype");
              font-weight: normal;
              font-style: normal;
              font-display: swap;
          }`;
        }
      } else {
        actualFontFamily = fontFamily;
      }
    }

    const fontFamilyRule = actualFontFamily ? `
        body {
            font-family: ${actualFontFamily} !important;
        }
        body * {
            font-family: inherit !important;
        }` : '';

    return `
      ${fontFaceRule}
      @namespace epub "http://www.idpf.org/2007/ops";
      @media print {
          html {
              column-width: auto !important;
              height: auto !important;
              width: auto !important;
          }
      }
      @media screen {
          html {
              color-scheme: light dark;
              color: ${theme.fg || theme.light.fg};
              font-size: ${fontSize}px;
          }${fontFamilyRule}
          a:any-link {
              color: ${theme.link || theme.light.link};
              text-decoration-color: light-dark(
                  color-mix(in srgb, currentColor 20%, transparent),
                  color-mix(in srgb, currentColor 40%, transparent));
              text-underline-offset: .1em;
          }
          a:any-link:hover {
              text-decoration-color: unset;
          }
          @media (prefers-color-scheme: dark) {
              html {
                  color: ${theme.fg || theme.dark.fg};
              }
              a:any-link {
                  color: ${theme.link || theme.dark.link};
              }
          }
          aside[epub|type~="footnote"] {
              display: none;
          }
      }
      html {
          line-height: ${lineHeight};
          hanging-punctuation: allow-end last;
          orphans: 2;
          widows: 2;
      }
      [align="left"] { text-align: left; }
      [align="right"] { text-align: right; }
      [align="center"] { text-align: center; }
      [align="justify"] { text-align: justify; }
      :is(hgroup, header) p {
          text-align: unset;
          hyphens: unset;
      }
      h1, h2, h3, h4, h5, h6, hgroup, th {
          text-wrap: balance;
      }
      pre {
          white-space: pre-wrap !important;
          tab-size: 2;
      }
      @media screen and (prefers-color-scheme: light) {
          ${(theme.bg || theme.light.bg) !== '#ffffff' ? `
          html, body {
              color: ${theme.fg || theme.light.fg} !important;
              background: none !important;
          }
          body * {
              color: inherit !important;
              border-color: currentColor !important;
              background-color: ${theme.bg || theme.light.bg} !important;
          }
          a:any-link {
              color: ${theme.link || theme.light.link} !important;
          }
          svg, img {
              background-color: transparent !important;
              mix-blend-mode: multiply;
          }
          .${mediaActiveClass}, .${mediaActiveClass} * {
              color: ${theme.fg || theme.light.fg} !important;
              background: color-mix(in hsl, ${theme.fg || theme.light.fg}, #fff 50%) !important;
              background: color-mix(in hsl, ${theme.fg || theme.light.fg}, ${theme.bg || theme.light.bg} 85%) !important;
          }` : ''}
      }
      @media screen and (prefers-color-scheme: dark) {

          html, body {
              color: ${theme.fg || theme.dark.fg} !important;
              background: none !important;
          }
          body * {
              color: inherit !important;
              border-color: currentColor !important;
              background-color: ${theme.bg || theme.dark.bg} !important;
          }
          a:any-link {
              color: ${theme.link || theme.dark.link} !important;
          }
          .${mediaActiveClass}, .${mediaActiveClass} * {
              color: ${theme.fg || theme.dark.fg} !important;
              background: color-mix(in hsl, ${theme.fg || theme.dark.fg}, #000 50%) !important;
              background: color-mix(in hsl, ${theme.fg || theme.dark.fg}, ${theme.bg || theme.dark.bg} 75%) !important;
          }
      }
      p, li, blockquote, dd {
          line-height: ${lineHeight};
          text-align: ${justify ? 'justify' : 'start'} !important;
          hyphens: ${hyphenate ? 'auto' : 'none'};
      }
      ${overrideFont ? '' : ''}
      ${userStylesheet}
      ::selection {
          background-color: rgba(128, 128, 128, 0.3);
      }
      ::-moz-selection {
          background-color: rgba(128, 128, 128, 0.3);
      }
    `;
  }

  private parseCustomFontId(fontFamily: string): number | null {
    if (fontFamily.startsWith('custom:')) {
      const id = parseInt(fontFamily.substring(7), 10);
      return !isNaN(id) ? id : null;
    }

    const id = parseInt(fontFamily, 10);
    return !isNaN(id) && id.toString() === fontFamily ? id : null;
  }

  applyStylesToRenderer(renderer: any, state: ReaderState): void {
    if (!renderer) return;

    renderer.setAttribute('max-column-count', state.maxColumnCount);
    renderer.setAttribute('gap', `${state.gap * 100}%`);
    renderer.setAttribute('max-inline-size', `${state.maxInlineSize}px`);
    renderer.setAttribute('max-block-size', `${state.maxBlockSize}px`);
    if (typeof renderer.setStyles === 'function') {
      const css = this.generateCSS(state);
      renderer.setStyles(css);
    }

    if (state.flow === 'paginated') {
      renderer.setAttribute('margin', '40px');
    } else {
      renderer.removeAttribute('margin');
    }
  }
}
