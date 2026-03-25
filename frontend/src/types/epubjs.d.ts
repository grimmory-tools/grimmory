declare module 'epubjs' {
  export interface TocItem {
    id?: string;
    href: string;
    label: string;
    subitems?: TocItem[];
  }

  export interface Navigation {
    toc: TocItem[];
    get(target: string): any;
  }

  export interface Location {
    start: {
      cfi: string;
      href: string;
      displayed: {
        page: number;
        total: number;
      };
      index: number;
    };
    end: {
      cfi: string;
      href: string;
    };
  }

  export interface Locations {
    total: number;
    generate(chars: number): Promise<void>;
    percentageFromCfi(cfi: string): number;
  }

  export interface SpineItem {
    href: string;
    index: number;
    cfi: string;
    document?: Document;
    cfiFromElement(element: Element): string;
  }

  export interface Spine {
    items: SpineItem[];
    get(href: string): SpineItem | null;
  }

  export interface Themes {
    register(name: string, theme: any): void;
    select(name: string): void;
    override(property: string, value: string): void;
    font(name: string): void;
    fontSize(size: string): void;
  }

  export interface Rendition {
    themes: Themes;
    display(target?: string): Promise<void>;
    prev(): Promise<void>;
    next(): Promise<void>;
    currentLocation(): Location | null;
    on(event: 'relocated', callback: (location: Location) => void): void;
    on(event: 'rendered', callback: (section: any) => void): void;
    on(event: 'keyup', callback: (event: KeyboardEvent) => void): void;
    on(event: string, callback: any): void;
    off(event: string, callback: any): void;
    destroy(): void;
  }

  export interface Book {
    loaded: {
      navigation: Promise<Navigation>;
    };
    navigation: Navigation;
    spine: Spine;
    locations: Locations;
    ready: Promise<void>;
    rendition: Rendition;
    renderTo(element: HTMLElement, options: {
      flow?: string;
      manager?: string;
      width?: string;
      height?: string;
      spread?: string;
      allowScriptedContent?: boolean;
    }): Rendition;
    canonical(href: string): string;
    epubProgress?: { cfi: string };
  }

  export class EpubCFI {
    compare(cfi1: string, cfi2: string): number;
    prototype: any; // Allow prototype access if needed
  }

  export default function ePub(input: string | ArrayBuffer): Book;
}