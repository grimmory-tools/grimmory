import {CustomFont} from '../model/custom-font.model';

/**
 * Font dropdown item for epub-reader component (PrimeNG Select format)
 */
export interface FontDropdownItem {
  label: string;
  value: string | null;
  disabled?: boolean;
}

/**
 * Font dropdown item for epub-reader-preferences component
 */
export interface FontPreferenceItem {
  name: string;
  displayName: string;
  key: string | null;
}

const MAX_FONT_DISPLAY_LENGTH = 12;

/**
 * Adds custom fonts to a dropdown array with a separator.
 * Removes any existing separator before adding to prevent duplicates.
 */
export function addCustomFontsToDropdown<T extends FontDropdownItem | FontPreferenceItem>(
  fonts: CustomFont[],
  targetArray: T[],
  format: 'select' | 'preference'
): void {
  if (fonts.length === 0) {
    return;
  }

  if (format === 'select') {
    const separatorIndex = (targetArray as FontDropdownItem[]).findIndex(item => item.value === 'separator');
    if (separatorIndex !== -1) {
      targetArray.splice(separatorIndex, 1);
    }

    fonts.forEach(font => {
      (targetArray as FontDropdownItem[]).push({
        label: font.fontName,
        value: `custom:${font.id}`
      });
    });
  } else {
    const separatorIndex = (targetArray as FontPreferenceItem[]).findIndex(item => item.key === 'separator');
    if (separatorIndex !== -1) {
      targetArray.splice(separatorIndex, 1);
    }

    fonts.forEach(font => {
      (targetArray as FontPreferenceItem[]).push({
        name: font.fontName,
        displayName: font.fontName.substring(0, MAX_FONT_DISPLAY_LENGTH),
        key: `custom:${font.id}`
      });
    });
  }
}
