import {ChangeDetectionStrategy, Component, Input} from '@angular/core';

@Component({
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: ''
})
export class CoverGeneratorComponent {
  @Input() title: string = '';
  @Input() author: string = '';
  @Input() isSquare: boolean = false;

  private wrapText(text: string, maxLineLength: number): string[] {
    const words = text.split(' ');
    const lines: string[] = [];
    let currentLine = '';

    words.forEach(word => {
      if (word.length > maxLineLength) {
        if (currentLine.length > 0) {
          lines.push(currentLine);
          currentLine = '';
        }
        lines.push(word);
      } else if (currentLine.length + word.length + 1 > maxLineLength) {
        lines.push(currentLine);
        currentLine = word;
      } else {
        currentLine += (currentLine.length > 0 ? ' ' : '') + word;
      }
    });

    if (currentLine.length > 0) {
      lines.push(currentLine);
    }

    return lines;
  }

  private truncateText(text: string, maxLength: number): string {
    if (text.length <= maxLength) return text;
    return text.substring(0, maxLength - 3) + '...';
  }

  private escapeXml(text: string): string {
    return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&apos;');
  }

  private hashString(str: string): number {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      const char = str.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash;
    }
    return Math.abs(hash);
  }

  generateCover(): string {
    const width = 250;
    const height = this.isSquare ? 250 : 350;
    const hash = this.hashString(this.title + this.author);

    if (this.isSquare) {
      return this.generateAudiobookCover(width, height, hash);
    } else {
      return this.generateEbookCover(width, height, hash);
    }
  }

  private generateEbookCover(width: number, height: number, hash: number): string {
    const palettes = [
      {bg1: '#1a1a2e', bg2: '#16213e', accent: '#e94560', text: '#eaeaea', textSecondary: '#b8b8b8'},
      {bg1: '#2d3436', bg2: '#636e72', accent: '#74b9ff', text: '#ffffff', textSecondary: '#dfe6e9'},
      {bg1: '#0c3547', bg2: '#1a5276', accent: '#f39c12', text: '#ecf0f1', textSecondary: '#bdc3c7'},
      {bg1: '#1e3d59', bg2: '#17263a', accent: '#ffc13b', text: '#f5f0e1', textSecondary: '#c7b198'},
      {bg1: '#2c2c54', bg2: '#474787', accent: '#ff793f', text: '#f8f8f8', textSecondary: '#d1ccc0'},
      {bg1: '#1b262c', bg2: '#0f4c75', accent: '#3282b8', text: '#bbe1fa', textSecondary: '#8fbdd3'},
    ];

    const palette = palettes[hash % palettes.length];
    const {titleLines, authorLines, titleFontSize, authorFontSize} = this.processText(false);

    const titleLineHeight = titleFontSize * 1.3;
    const titleStartY = 80;

    const titleElements = titleLines.map((line, index) => {
      const y = titleStartY + (index * titleLineHeight);
      return `<text x="125" y="${y}" text-anchor="middle" font-family="Georgia, serif" font-weight="600" font-size="${titleFontSize}" fill="${palette.text}">${this.escapeXml(line)}</text>`;
    }).join('\n');

    const authorLineHeight = authorFontSize * 1.3;
    const authorStartY = height - 50 - ((authorLines.length - 1) * authorLineHeight);

    const authorElements = authorLines.map((line, index) => {
      const y = authorStartY + (index * authorLineHeight);
      return `<text x="125" y="${y}" text-anchor="middle" font-family="Helvetica, Arial, sans-serif" font-size="${authorFontSize}" fill="${palette.textSecondary}">${this.escapeXml(line)}</text>`;
    }).join('\n');

    const decorY = titleStartY + (titleLines.length * titleLineHeight) + 20;

    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
      <defs>
        <linearGradient id="bgGrad" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" style="stop-color:${palette.bg1}"/>
          <stop offset="100%" style="stop-color:${palette.bg2}"/>
        </linearGradient>
        <linearGradient id="spineGrad" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" style="stop-color:rgba(0,0,0,0.4)"/>
          <stop offset="100%" style="stop-color:rgba(0,0,0,0)"/>
        </linearGradient>
      </defs>
      <rect width="100%" height="100%" fill="url(#bgGrad)"/>
      <rect x="0" y="0" width="12" height="100%" fill="url(#spineGrad)"/>
      <rect x="20" y="20" width="${width - 40}" height="${height - 40}" fill="none" stroke="${palette.accent}" stroke-width="1" opacity="0.4" rx="2"/>
      <rect x="25" y="25" width="${width - 50}" height="${height - 50}" fill="none" stroke="${palette.accent}" stroke-width="0.5" opacity="0.2" rx="1"/>
      ${titleElements}
      <line x1="80" y1="${decorY}" x2="170" y2="${decorY}" stroke="${palette.accent}" stroke-width="2" stroke-linecap="round"/>
      <circle cx="125" cy="${decorY}" r="3" fill="${palette.accent}"/>
      ${authorElements}
      <rect x="0" y="0" width="100%" height="100%" fill="none" stroke="rgba(255,255,255,0.1)" stroke-width="1"/>
    </svg>`;

    return this.svgToDataUrl(svg);
  }

  private generateAudiobookCover(width: number, height: number, hash: number): string {
    const palettes = [
      {bg1: '#667eea', bg2: '#764ba2', accent: '#ffffff', text: '#ffffff', textSecondary: '#e8e8e8'},
      {bg1: '#f093fb', bg2: '#f5576c', accent: '#ffffff', text: '#ffffff', textSecondary: '#ffeaea'},
      {bg1: '#4facfe', bg2: '#00f2fe', accent: '#ffffff', text: '#1a1a2e', textSecondary: '#2d3436'},
      {bg1: '#43e97b', bg2: '#38f9d7', accent: '#ffffff', text: '#1a1a2e', textSecondary: '#2d3436'},
      {bg1: '#fa709a', bg2: '#fee140', accent: '#ffffff', text: '#1a1a2e', textSecondary: '#2d3436'},
      {bg1: '#a18cd1', bg2: '#fbc2eb', accent: '#ffffff', text: '#2d3436', textSecondary: '#4a4a4a'},
    ];

    const palette = palettes[hash % palettes.length];
    const {titleLines, authorLines, titleFontSize, authorFontSize} = this.processText(true);

    const titleLineHeight = titleFontSize * 1.3;
    const titleStartY = 70;

    const titleElements = titleLines.map((line, index) => {
      const y = titleStartY + (index * titleLineHeight);
      return `<text x="125" y="${y}" text-anchor="middle" font-family="Helvetica, Arial, sans-serif" font-weight="700" font-size="${titleFontSize}" fill="${palette.text}">${this.escapeXml(line)}</text>`;
    }).join('\n');

    const authorLineHeight = authorFontSize * 1.3;
    const authorStartY = height - 40 - ((authorLines.length - 1) * authorLineHeight);

    const authorElements = authorLines.map((line, index) => {
      const y = authorStartY + (index * authorLineHeight);
      return `<text x="125" y="${y}" text-anchor="middle" font-family="Helvetica, Arial, sans-serif" font-size="${authorFontSize}" fill="${palette.textSecondary}">${this.escapeXml(line)}</text>`;
    }).join('\n');

    const iconY = titleStartY + (titleLines.length * titleLineHeight) + 25;

    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
      <defs>
        <linearGradient id="bgGrad" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" style="stop-color:${palette.bg1}"/>
          <stop offset="100%" style="stop-color:${palette.bg2}"/>
        </linearGradient>
      </defs>
      <rect width="100%" height="100%" fill="url(#bgGrad)"/>
      <circle cx="125" cy="125" r="100" fill="none" stroke="${palette.accent}" stroke-width="1" opacity="0.15"/>
      <circle cx="125" cy="125" r="80" fill="none" stroke="${palette.accent}" stroke-width="1" opacity="0.1"/>
      ${titleElements}
      <g transform="translate(125, ${iconY})" opacity="0.9">
        <circle cx="0" cy="0" r="18" fill="none" stroke="${palette.accent}" stroke-width="2" opacity="0.6"/>
        <path d="M-6,-8 L-6,8 L8,0 Z" fill="${palette.accent}" opacity="0.8"/>
      </g>
      ${authorElements}
    </svg>`;

    return this.svgToDataUrl(svg);
  }

  private processText(isSquare: boolean): {
    titleLines: string[];
    authorLines: string[];
    titleFontSize: number;
    authorFontSize: number;
  } {
    const maxTitleLength = 60;
    const maxAuthorLength = 40;
    const truncatedTitle = this.truncateText(this.title, maxTitleLength);
    const truncatedAuthor = this.truncateText(this.author, maxAuthorLength);

    const maxLineLength = 14;
    const maxTitleLines = isSquare ? 3 : 4;
    const maxAuthorLines = isSquare ? 2 : 3;

    let titleLines = this.wrapText(truncatedTitle, maxLineLength);
    let authorLines = this.wrapText(truncatedAuthor, maxLineLength);

    if (titleLines.length > maxTitleLines) {
      titleLines = titleLines.slice(0, maxTitleLines);
      titleLines[maxTitleLines - 1] = this.truncateText(titleLines[maxTitleLines - 1], maxLineLength);
    }

    if (authorLines.length > maxAuthorLines) {
      authorLines = authorLines.slice(0, maxAuthorLines);
      authorLines[maxAuthorLines - 1] = this.truncateText(authorLines[maxAuthorLines - 1], maxLineLength);
    }

    const titleFontSize = this.calculateTitleFontSize(titleLines.length);
    const authorFontSize = this.calculateAuthorFontSize(authorLines.length);

    return {titleLines, authorLines, titleFontSize, authorFontSize};
  }

  private calculateTitleFontSize(lineCount: number): number {
    if (lineCount <= 2) return 28;
    if (lineCount === 3) return 24;
    return 20;
  }

  private calculateAuthorFontSize(lineCount: number): number {
    if (lineCount <= 2) return 18;
    return 16;
  }

  private svgToDataUrl(svg: string): string {
    const base64 = btoa(encodeURIComponent(svg).replace(/%([0-9A-F]{2})/g, (_, p1) => {
      return String.fromCharCode(parseInt(p1, 16));
    }));
    return `data:image/svg+xml;base64,${base64}`;
  }
}
