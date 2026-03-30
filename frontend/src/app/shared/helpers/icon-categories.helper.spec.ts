import {describe, expect, it} from 'vitest';

import {IconCategoriesHelper} from './icon-categories.helper';

describe('IconCategoriesHelper', () => {
  it('creates a prime icon class list from the static categories', () => {
    const icons = IconCategoriesHelper.createIconList();

    expect(icons[0]).toBe('pi pi-address-book');
    expect(icons.at(-1)).toBe('pi pi-youtube');
    expect(new Set(icons).size).toBe(icons.length);
    expect(icons).toContain('pi pi-book');
    expect(icons).toContain('pi pi-shield');
  });
});
