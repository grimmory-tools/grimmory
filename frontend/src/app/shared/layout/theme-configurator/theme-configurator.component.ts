import {ChangeDetectionStrategy, Component, computed, effect, inject} from '@angular/core';
import {FormsModule} from '@angular/forms';


import Aura from '../theme-palette-extend';

import {AppConfigService} from '../../service/app-config.service';
import {TranslocoDirective} from '@jsverse/transloco';
import {FaviconService} from './favicon-service';
import {NgClass} from '@angular/common';

type ColorPalette = Record<string, string>;

interface Palette {
  name: string;
  palette: ColorPalette;
}

@Component({
  selector: 'app-theme-configurator',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './theme-configurator.component.html',
  host: {
    class: 'config-panel hidden'
  },
  imports: [
    NgClass,
    FormsModule,
    TranslocoDirective
  ]
})
export class ThemeConfiguratorComponent {
  readonly configService = inject(AppConfigService);
  readonly faviconService = inject(FaviconService);

  readonly surfaces = this.configService.surfaces;

  readonly selectedPrimaryColor = computed(() => this.configService.appState().primary);
  readonly selectedSurfaceColor = computed(() => this.configService.appState().surface);

  readonly faviconGradient = computed(() => {
    const name = this.selectedPrimaryColor() ?? 'orange';
    const presetPalette = (Aura.primitive ?? {}) as Record<string, ColorPalette>;
    const fallbackPalette = presetPalette['orange'] ?? {};

    if (name === 'noir') {
      const surfaceName = this.selectedSurfaceColor() ?? 'ash';
      const surfacePalette = this.configService.surfaces.find((surface) => surface.name === surfaceName)?.palette ?? {};

      return {
        start: surfacePalette['50'] ?? surfacePalette['0'] ?? '#f4f6f8',
        end: surfacePalette['300'] ?? surfacePalette['200'] ?? '#b4bcc7'
      };
    }

    const colorPalette = presetPalette[name] ?? fallbackPalette;

    return {
      start: colorPalette[300] ?? colorPalette[500] ?? '#fdba74',
      end: colorPalette[500] ?? colorPalette[700] ?? '#f97316'
    };
  });

  private readonly _faviconSyncEffect = effect(() => {
    const gradient = this.faviconGradient();
    this.faviconService.updateFavicon(gradient.start, gradient.end);
  });

  readonly primaryColors = computed<Palette[]>(() => {
    const presetPalette = (Aura.primitive ?? {}) as Record<string, ColorPalette>;
    const colors = [
      'orange', 'amber', 'yellow', 'lime', 'green', 'emerald', 
      'teal', 'cyan', 'sky', 'blue', 'indigo', 'violet',
      'purple', 'fuchsia', 'pink', 'rose', 'red',
      'coralSunset', 'roseBlush', 'melonBlush', 'cottonCandy',
      'apricotSunrise', 'antiqueBronze', 'butteryYellow', 'vanillaCream',
      'citrusMint', 'freshMint', 'sagePearl', 'skyBlue','periwinkleCream',
      'pastelRoyalBlue', 'lavenderDream', 'dustyNeutral'
    ];
    return [{name: 'noir', palette: {}}].concat(
      colors.map(name => ({
        name,
        palette: presetPalette[name] ?? {}
      }))
    );
  });

  updateColors(event: Event, type: 'primary' | 'surface', color: { name: string; palette?: ColorPalette }) {
    this.configService.appState.update((state) => ({
      ...state,
      [type]: color.name
    }));
    event.stopPropagation();
  }
}
