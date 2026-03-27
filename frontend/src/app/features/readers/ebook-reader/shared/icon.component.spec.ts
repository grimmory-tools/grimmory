import {describe, expect, it} from 'vitest';

import {ReaderIconComponent} from './icon.component';

describe('ReaderIconComponent', () => {
  it('exposes SVG path metadata for a known icon name', () => {
    const component = new ReaderIconComponent();
    component.name = 'bookmark';

    expect(component.paths.length).toBeGreaterThan(0);
    expect(component.paths[0]?.d).toContain('M19 21');
  });

  it('returns zero coordinates when the line descriptor is incomplete', () => {
    const component = new ReaderIconComponent();

    expect(component.getLineCoords('M3,6 L21,6', 0)).toBe('3');
    expect(component.getLineCoords('M3,6 L21,6', 10)).toBe('0');
  });
});
