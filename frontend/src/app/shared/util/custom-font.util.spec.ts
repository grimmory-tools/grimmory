import {describe, expect, it} from 'vitest';

import {FontFormat} from '../model/custom-font.model';
import {addCustomFontsToDropdown} from './custom-font.util';

describe('custom-font.util', () => {
  it('does nothing when no custom fonts are supplied', () => {
    const selectItems = [{label: 'System', value: 'system'}];
    const preferenceItems = [{name: 'System', displayName: 'System', key: 'system'}];

    addCustomFontsToDropdown([], selectItems, 'select');
    addCustomFontsToDropdown([], preferenceItems, 'preference');

    expect(selectItems).toEqual([{label: 'System', value: 'system'}]);
    expect(preferenceItems).toEqual([{name: 'System', displayName: 'System', key: 'system'}]);
  });

  it('appends custom fonts to a select dropdown and removes stale separators', () => {
    const selectItems = [
      {label: 'System', value: 'system'},
      {label: 'Separator', value: 'separator'},
    ];

    addCustomFontsToDropdown([
      {id: 7, fontName: 'Bookerly', originalFileName: 'Bookerly.ttf', format: FontFormat.TTF, fileSize: 10, uploadedAt: '2026-03-26T00:00:00Z'},
      {id: 8, fontName: 'Atkinson Hyperlegible', originalFileName: 'Atkinson.ttf', format: FontFormat.TTF, fileSize: 10, uploadedAt: '2026-03-26T00:00:00Z'},
    ], selectItems, 'select');

    expect(selectItems).toEqual([
      {label: 'System', value: 'system'},
      {label: 'Bookerly', value: 'custom:7'},
      {label: 'Atkinson Hyperlegible', value: 'custom:8'},
    ]);
  });

  it('appends custom fonts to a preference dropdown with truncated display names', () => {
    const preferenceItems = [
      {name: 'System', displayName: 'System', key: 'system'},
      {name: 'Separator', displayName: 'Separator', key: 'separator'},
    ];

    addCustomFontsToDropdown([
      {id: 9, fontName: 'Very Long Font Name', originalFileName: 'Font.ttf', format: FontFormat.TTF, fileSize: 10, uploadedAt: '2026-03-26T00:00:00Z'},
    ], preferenceItems, 'preference');

    expect(preferenceItems).toEqual([
      {name: 'System', displayName: 'System', key: 'system'},
      {name: 'Very Long Font Name', displayName: 'Very Long Fo', key: 'custom:9'},
    ]);
  });
});
