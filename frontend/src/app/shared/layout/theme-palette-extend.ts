import Aura from '@primeuix/themes/aura';
import { definePreset } from '@primeuix/themes';

/*
 * PrimeNG theme bridge.
 *
 * App-owned CSS tokens define the native shell/page foundation. This file only
 * extends Prime's Aura preset so Prime components can use the same app-selected
 * palettes and the same Tailwind-owned page background.
 */

/** Popup menu surfaces use card elevation, not page canvas. */
const menuRoot = { background: 'var(--color-card)' };

/*
 * Aura light outlined buttons default to *-200 borders — too faint on --color-card.
 * Use *-400 borders and *-600 text: readable without feeling heavy.
 */
const lightOutlinedButton = {
  primary: { borderColor: '{primary.400}', color: '{primary.600}' },
  secondary: { borderColor: '{surface.400}', color: '{surface.600}' },
  plain: { borderColor: '{surface.400}', color: '{surface.600}' },
  success: { borderColor: '{green.400}', color: '{green.600}' },
  info: { borderColor: '{sky.400}', color: '{sky.600}' },
  warn: { borderColor: '{orange.400}', color: '{orange.600}' },
  danger: { borderColor: '{red.400}', color: '{red.600}' },
  help: { borderColor: '{purple.400}', color: '{purple.600}' },
};

const AppPrimePreset = definePreset(Aura, {
  semantic: {
    colorScheme: {
      light: { content: { background: 'var(--color-page)' } },
      dark: {
        content: { background: 'var(--color-page)' },
        // Card menus are surface.800; Aura default hover is also 800 — use app surface-hover mix.
        navigation: {
          item: {
            focusBackground: 'color-mix(in srgb, {text.color}, transparent 92%)',
            activeBackground: 'color-mix(in srgb, {text.color}, transparent 92%)',
          },
        },
      },
    },
  },
  components: {
    menu: { root: menuRoot },
    tieredmenu: { root: menuRoot },
    contextmenu: { root: menuRoot },
    menubar: { submenu: menuRoot },
    button: {
      colorScheme: {
        light: { outlined: lightOutlinedButton },
      },
    },
  },
});

export default AppPrimePreset;
