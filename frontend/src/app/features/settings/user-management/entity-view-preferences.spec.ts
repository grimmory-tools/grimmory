import {describe, expect, it} from 'vitest';

import {findEntityViewPreferenceOverride, upsertEntityViewPreference} from './entity-view-preferences';
import {type EntityViewPreferences} from './user.service';

const DEFAULTS: EntityViewPreferences = {
  global: {sortKey: 'title', sortDir: 'ASC', view: 'GRID', coverSize: 1, seriesCollapsed: false, overlayBookType: true},
  overrides: null,
};

describe('entity view preferences with the backend default of null overrides', () => {
  it('finds no override', () => {
    expect(findEntityViewPreferenceOverride(DEFAULTS, {entityType: 'LIBRARY', entityId: 3})).toBeUndefined();
  });

  it('adds the first override', () => {
    const next = upsertEntityViewPreference(DEFAULTS, {entityType: 'LIBRARY', entityId: 3}, {view: 'TABLE'});

    expect(next.overrides).toEqual([{entityType: 'LIBRARY', entityId: 3, preferences: {...DEFAULTS.global, view: 'TABLE'}}]);
    expect(DEFAULTS.overrides).toBeNull();
  });
});
