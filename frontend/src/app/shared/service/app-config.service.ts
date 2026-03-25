import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { effect, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { $t, updatePreset, updateSurfacePalette } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';
import { AppState } from '../model/app-state.model';

type ColorPalette = Record<string, string>;

interface Palette {
  name: string;
  palette: ColorPalette;
}

@Injectable({
  providedIn: 'root',
})
export class AppConfigService {
  private readonly STORAGE_KEY = 'appConfigState';
  appState = signal<AppState>({});
  document = inject(DOCUMENT);
  platformId = inject(PLATFORM_ID);
  private initialized = false;

  readonly surfaces: Palette[] = [
    {
      name: 'slate',
      palette: {
        0: '#ffffff',
        50: '#f8fafc',
        100: '#f1f5f9',
        200: '#e2e8f0',
        300: '#cbd5e1',
        400: '#94a3b8',
        500: '#64748b',
        600: '#475569',
        700: '#334155',
        800: '#283548',
        900: '#0f172a',
        950: '#020617'
      }
    },
    {
      name: 'gray',
      palette: {
        0: '#ffffff',
        50: '#fafafa',
        100: '#f4f4f5',
        200: '#e4e4e7',
        300: '#d4d4d8',
        400: '#a1a1aa',
        500: '#71717a',
        600: '#52525b',
        700: '#3f3f46',
        800: '#313136',
        900: '#18181b',
        950: '#09090b'
      }
    },
    {
      name: 'neutral',
      palette: {
        0: '#ffffff',
        50: '#fafafa',
        100: '#f5f5f5',
        200: '#e5e5e5',
        300: '#d4d4d4',
        400: '#a3a3a3',
        500: '#737373',
        600: '#525252',
        700: '#404040',
        800: '#303030',
        900: '#171717',
        950: '#0a0a0a'
      }
    },
    {
      name: 'zinc',
      palette: {
        0: '#ffffff',
        50: '#fafafa',
        100: '#f4f4f5',
        200: '#e4e4e7',
        300: '#d4d4d8',
        400: '#a1a1aa',
        500: '#71717a',
        600: '#52525b',
        700: '#3f3f46',
        800: '#313136',
        900: '#18181b',
        950: '#09090b'
      }
    },
    {
      name: 'stone',
      palette: {
        0: '#ffffff',
        50: '#fafaf9',
        100: '#f5f5f4',
        200: '#e7e5e4',
        300: '#d6d3d1',
        400: '#a8a29e',
        500: '#78716c',
        600: '#57534e',
        700: '#44403c',
        800: '#342f2b',
        900: '#1c1917',
        950: '#0c0a09'
      }
    },
    {
      name: 'iron',
      palette: {
        0: '#ffffff',
        50: '#f1f3f4',
        100: '#e3e5e6',
        200: '#d2d4d6',
        300: '#b8bbc0',
        400: '#9ca0a6',
        500: '#7d8288',
        600: '#646a70',
        700: '#4c5258',
        800: '#353a40',
        900: '#1a1d20',
        950: '#0d0f11'
      }
    },
    {
      name: 'steel',
      palette: {
        0: '#ffffff',
        50: '#f3f4f6',
        100: '#e5e7eb',
        200: '#d1d5db',
        300: '#b0b7c3',
        400: '#8b93a6',
        500: '#6b7280',
        600: '#545861',
        700: '#3f4347',
        800: '#2c2f33',
        900: '#16181b',
        950: '#0b0d0e'
      }
    },
    {
      name: 'carbon',
      palette: {
        0: '#ffffff',
        50: '#eef0f2',
        100: '#dde1e6',
        200: '#c7cdd5',
        300: '#a8b0bc',
        400: '#8691a0',
        500: '#697080',
        600: '#545a66',
        700: '#41464f',
        800: '#303439',
        900: '#181a1d',
        950: '#0c0e10'
      }
    },
    {
      name: 'ash',
      palette: {
        0: '#ffffff',
        50: '#f4f6f8',
        100: '#e6e9ed',
        200: '#d3d8de',
        300: '#b4bcc7',
        400: '#919ca9',
        500: '#71808a',
        600: '#5a666f',
        700: '#464f56',
        800: '#34393e',
        900: '#1a1e21',
        950: '#0d1012'
      }
    },
    {
      name: 'smoke',
      palette: {
        0: '#ffffff',
        50: '#f6f6f7',
        100: '#ebebed',
        200: '#dadadd',
        300: '#bbbcc1',
        400: '#989aa1',
        500: '#797c84',
        600: '#63666d',
        700: '#4f5257',
        800: '#3a3d42',
        900: '#1d2023',
        950: '#0e1011'
      }
    },
    {
      name: 'midnight-blue',
      palette: {
        0: '#ffffff',
        50: '#fcfcfd',
        100: '#f7f8fb',
        200: '#f0f2f7',
        300: '#e5e8f0',
        400: '#d3d8e3',
        500: '#5c6b7a',
        600: '#4a5866',
        700: '#3b4651',
        800: '#38424d',
        900: '#1f252c',
        950: '#121518'
      }
    },
    {
      name: 'charcoal',
      palette: {
        0: '#ffffff',
        50: '#f0f0f0',
        100: '#e5e5e5',
        200: '#d1d1d1',
        300: '#b8b8b8',
        400: '#9a9a9a',
        500: '#7d7d7d',
        600: '#666666',
        700: '#525252',
        800: '#3d3d3d',
        900: '#242424',
        950: '#141414'
      }
    },
    {
      name: 'soho',
      palette: {
        0: '#ffffff',
        50: '#ececec',
        100: '#dedfdf',
        200: '#c4c4c6',
        300: '#adaeb0',
        400: '#97979b',
        500: '#7f8084',
        600: '#6a6b70',
        700: '#55565b',
        800: '#3f4046',
        900: '#252530',
        950: '#16161d'
      }
    },
    {
      name: 'viva',
      palette: {
        0: '#ffffff',
        50: '#f3f3f3',
        100: '#e7e7e8',
        200: '#cfd0d0',
        300: '#b7b8b9',
        400: '#9fa1a1',
        500: '#87898a',
        600: '#6e7173',
        700: '#565a5b',
        800: '#3e4244',
        900: '#1e2324',
        950: '#0e1315'
      }
    },
    {
      name: 'ocean',
      palette: {
        0: '#ffffff',
        50: '#fbfcfc',
        100: '#F7F9F8',
        200: '#EFF3F2',
        300: '#DADEDD',
        400: '#B1B7B6',
        500: '#828787',
        600: '#5F7274',
        700: '#415B61',
        800: '#324f5a',
        900: '#183240',
        950: '#0c1920'
      }
    },
    {
      name: 'light-slate',
      palette: {
        0: '#ffffff',
        50: '#fcfcfd',
        100: '#f8fafc',
        200: '#f1f5f9',
        300: '#e2e8f0',
        400: '#cbd5e1',
        500: '#94a3b8',
        600: '#64748b',
        700: '#475569',
        800: '#3d5068',
        900: '#1e293b',
        950: '#0f172a'
      }
    },
    {
      name: 'olive',
      palette: {
        0: '#ffffff',
        50: '#fcfcfb',
        100: '#f8f9f6',
        200: '#f1f4ed',
        300: '#e6eadc',
        400: '#d4dfc5',
        500: '#6a755e',
        600: '#555d4c',
        700: '#434a3d',
        800: '#3d4438',
        900: '#232824',
        950: '#131614'
      }
    },
    {
      name: 'crimson',
      palette: {
        0: '#ffffff',
        50: '#fcfbfb',
        100: '#f8f5f5',
        200: '#f1ebeb',
        300: '#e6d9d9',
        400: '#d4c3c3',
        500: '#755e5e',
        600: '#5d4c4c',
        700: '#4a3d3d',
        800: '#423838',
        900: '#242020',
        950: '#141212'
      }
    }
  ];

  constructor() {
    const initialState = this.loadAppState();
    this.appState.set({ ...initialState });
    this.document.documentElement.classList.add('p-dark');

    if (isPlatformBrowser(this.platformId)) {
      this.onPresetChange();
    }

    effect(() => {
      const state = this.appState();
      if (!this.initialized || !state) {
        this.initialized = true;
        return;
      }
      this.saveAppState(state);
      this.onPresetChange();
    });
  }

  private loadAppState(): AppState {
    if (isPlatformBrowser(this.platformId)) {
      const storedState = localStorage.getItem(this.STORAGE_KEY);
      if (storedState) {
        return JSON.parse(storedState);
      }
    }
    return {
      preset: 'Aura',
      primary: 'green',
      surface: 'ash',
    };
  }

  private saveAppState(state: AppState): void {
    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(state));
    }
  }

  private getSurfacePalette(surface: string): ColorPalette {
    return this.surfaces.find(s => s.name === surface)?.palette ?? {};
  }

  getPresetExt(): object {
    const surfacePalette = this.getSurfacePalette(this.appState().surface ?? 'neutral');
    const primaryName = this.appState().primary ?? 'green';
    const presetPalette = (Aura.primitive ?? {}) as Record<string, ColorPalette>;
    const color = presetPalette[primaryName] ?? {};

    if (primaryName === 'noir') {
      return {
        semantic: {
          primary: { ...surfacePalette },
          colorScheme: {
            dark: {
              primary: {
                color: '{primary.50}',
                contrastColor: '{primary.950}',
                hoverColor: '{primary.200}',
                activeColor: '{primary.300}'
              },
              highlight: {
                background: '{primary.50}',
                focusBackground: '{primary.300}',
                color: '{primary.950}',
                focusColor: '{primary.950}'
              }
            }
          }
        }
      };
    }

    return {
      semantic: {
        primary: color,
        colorScheme: {
          dark: {
            primary: {
              color: '{primary.400}',
              contrastColor: '{surface.900}',
              hoverColor: '{primary.300}',
              activeColor: '{primary.200}'
            },
            highlight: {
              background: 'color-mix(in srgb, {primary.400}, transparent 84%)',
              focusBackground: 'color-mix(in srgb, {primary.400}, transparent 76%)',
              color: 'rgba(255,255,255,.87)',
              focusColor: 'rgba(255,255,255,.87)'
            }
          }
        }
      }
    };
  }

  onPresetChange(): void {
    const surfacePalette = this.getSurfacePalette(this.appState().surface ?? 'neutral');
    const preset = this.getPresetExt();
    $t().preset(Aura).preset(preset).surfacePalette(surfacePalette).use({ useDefaultOptions: true });
  }
}
