import {describe, expect, it} from 'vitest';

import {entityViewCardDetail, findEntityViewPreferenceOverride, upsertEntityViewPreference} from './entity-view-preferences';
import {type EntityViewPreferences} from './user.service';

const DEFAULTS: EntityViewPreferences = {
  global: {sortKey: 'title', sortDir: 'ASC', view: 'GRID', cardDetail: null, coverSize: 1, seriesCollapsed: false, overlayBookType: true},
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

  it('resolves card detail from an explicit override or falls back to global', () => {
    const context = {entityType: 'LIBRARY' as const, entityId: 3};
    const withGlobal: EntityViewPreferences = {global: {...DEFAULTS.global, cardDetail: 'addedOn'}, overrides: []};

    expect(entityViewCardDetail(withGlobal, context)).toBe('addedOn');
    expect(entityViewCardDetail({
      ...withGlobal,
      overrides: [{...context, preferences: {...DEFAULTS.global, cardDetail: undefined}}],
    }, context)).toBe('addedOn');
    expect(entityViewCardDetail({
      ...withGlobal,
      overrides: [{...context, preferences: {...DEFAULTS.global, cardDetail: null}}],
    }, context)).toBeNull();
  });
});
