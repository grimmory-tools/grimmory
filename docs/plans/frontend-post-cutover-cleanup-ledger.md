# Frontend Post-Cutover Cleanup Ledger

This document tracks the remaining Booklore-era frontend references that should survive the `booklore-ui -> frontend` rename temporarily. The goal is to preserve short-term compatibility now and remove these aliases later with explicit triggers instead of ad hoc cleanup.

## Remove Once the Symlink Window Closes

- `frontend/README.md`
  - Replace the remaining `booklore-ui` project-path wording and examples after the compatibility symlink is removed.
- `frontend/CONTRIBUTING.md`
  - Replace the remaining `booklore-ui` project-path wording after the compatibility symlink is removed.

## Remove After Existing PRs Have Been Replayed

- The tracked symlink `booklore-ui -> frontend`
  - Remove after the PR replay script has been exercised on the active open frontend PRs and maintainers no longer need the compatibility path.

## Remove After the Local Storage Migration Window

- [frontend/src/app/core/config/language-initializer.ts](/Users/james/Projects/grimmory/grimmory/frontend/src/app/core/config/language-initializer.ts)
  - `LEGACY_LANG_STORAGE_KEY = 'booklore-lang'`
  - Remove after at least one stable release cycle has shipped with the migration logic and maintainers are comfortable dropping the legacy browser key.

## Remove After Backend API Contracts Are Renamed

- [frontend/src/app/shared/constants/reset-progress-type.ts](/Users/james/Projects/grimmory/grimmory/frontend/src/app/shared/constants/reset-progress-type.ts)
  - `GRIMMORY: 'BOOKLORE'`
  - Remove after the backend emits a Grimmory-native reset-progress type.
- [frontend/src/app/features/settings/user-management/user.service.ts](/Users/james/Projects/grimmory/grimmory/frontend/src/app/features/settings/user-management/user.service.ts)
  - Legacy Booklore permission and create-user aliases.
  - Remove after the backend accepts only Grimmory naming.
- [frontend/src/app/features/settings/device-settings/component/koreader-settings/koreader.service.ts](/Users/james/Projects/grimmory/grimmory/frontend/src/app/features/settings/device-settings/component/koreader-settings/koreader.service.ts)
  - Legacy Booklore KOReader sync field and fallback endpoint.
  - Remove after the backend no longer returns or expects the Booklore alias.

## Follow-Up Expectations

- Any backend migration plan should reference this ledger and either consume or supersede the API-contract-driven entries.
- When an entry is removed, update this file in the same change so the ledger remains accurate.
