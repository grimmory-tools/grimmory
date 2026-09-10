import {
  type EntityViewPreference,
  type EntityViewPreferenceOverride,
  type EntityViewPreferences,
  type SortCriterion,
} from './user.service';

export type EntityViewPreferenceContext = Pick<EntityViewPreferenceOverride, 'entityType' | 'entityId'>;

export function entityViewSortCriteria(preference: EntityViewPreference): SortCriterion[] {
  if (preference.sortCriteria?.length) {
    return preference.sortCriteria;
  }
  return preference.sortKey && preference.sortDir
    ? [{field: preference.sortKey, direction: preference.sortDir}]
    : [];
}

export function findEntityViewPreferenceOverride(
  preferences: EntityViewPreferences | undefined,
  context: EntityViewPreferenceContext,
): EntityViewPreference | undefined {
  return preferences?.overrides?.find(override =>
    override.entityType === context.entityType && override.entityId === context.entityId,
  )?.preferences;
}

export function entityViewMode(
  preferences: EntityViewPreferences | undefined,
  context: EntityViewPreferenceContext | null,
): 'GRID' | 'TABLE' {
  const override = context ? findEntityViewPreferenceOverride(preferences, context) : undefined;
  return override?.view ?? preferences?.global.view ?? 'GRID';
}

export function entityViewSortPatch(sortCriteria: readonly SortCriterion[]): Partial<EntityViewPreference> {
  const primary = sortCriteria.at(0);
  return {sortKey: primary?.field, sortDir: primary?.direction, sortCriteria: [...sortCriteria]};
}

export function upsertEntityViewPreference(
  preferences: EntityViewPreferences,
  context: EntityViewPreferenceContext | null,
  patch: Partial<EntityViewPreference>,
): EntityViewPreferences {
  const next = structuredClone(preferences);
  if (context === null) {
    next.global = {...next.global, ...patch};
    return next;
  }
  next.overrides ??= [];
  const override = next.overrides.find(candidate =>
    candidate.entityType === context.entityType && candidate.entityId === context.entityId);
  if (override) {
    override.preferences = {...override.preferences, ...patch};
  } else {
    next.overrides.push({...context, preferences: {...next.global, ...patch}});
  }
  return next;
}
