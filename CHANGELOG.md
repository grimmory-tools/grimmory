## [2.3.0](https://github.com/grimmory-tools/grimmory/compare/v2.2.6...v2.3.0) (2026-03-21)

### Features

* **release:** document develop-based stable release previews ([930e526](https://github.com/grimmory-tools/grimmory/commit/930e5262285540b3b65b9ef4f7be328a05dfb5b4))

### Bug Fixes

* **api:** fix potential memory leaks in file processing ([031e8ae](https://github.com/grimmory-tools/grimmory/commit/031e8ae257c3354cacce2d17c8c6bc35ce80badb))
* **ci:** correct artifact download action pin ([37ca101](https://github.com/grimmory-tools/grimmory/commit/37ca101dd4bd08ccfe6d4d2395ecc71298cc323d))
* **ci:** publish PR test results from workflow_run ([11a76bf](https://github.com/grimmory-tools/grimmory/commit/11a76bffe12f80d0271e3626291cfbd275346727))
* **ci:** repair release preview and test result publishing ([afa5b81](https://github.com/grimmory-tools/grimmory/commit/afa5b818ebe612e66acc819aa398ac0c6184d21b))
* drop telemetry from app ([#52](https://github.com/grimmory-tools/grimmory/issues/52)) ([4d82cb7](https://github.com/grimmory-tools/grimmory/commit/4d82cb718833a2a4e08ee2f18b2ff3ab9b043dd6))
* **ui:** repair frontend compile after rebrand ([fea1ec6](https://github.com/grimmory-tools/grimmory/commit/fea1ec6930ae64445c6d7e7b38bdc4e7925b51c1))

### Refactors

* **build:** rename frontend dist output to grimmory ([ecf388f](https://github.com/grimmory-tools/grimmory/commit/ecf388f7a2086fbd1d8737a972926d70ec3190d4))
* **i18n:** rename booklore translation keys to grimmory ([eb94afa](https://github.com/grimmory-tools/grimmory/commit/eb94afa7ad600eaa2535802583f21937bd1ae2c0))
* **metadata:** move default parser from Amazon to Goodreads ([e252122](https://github.com/grimmory-tools/grimmory/commit/e252122bc5a89c75af85ddbaae66a14d368479f9))
* pull kepubify & ffprobe during build ([#50](https://github.com/grimmory-tools/grimmory/issues/50)) ([1c15629](https://github.com/grimmory-tools/grimmory/commit/1c15629a10ba2e2ad78455f118e8c937b585b157))
* **ui:** rebrand frontend surfaces to grimmory ([d786dd8](https://github.com/grimmory-tools/grimmory/commit/d786dd8ccbebaa360385dce834816bcb6aaf3b2f))

### Chores

* **api:** remove the custom startup banner ([98c9b1a](https://github.com/grimmory-tools/grimmory/commit/98c9b1ae653c2140b7b2ab86b5511be3376bfe43))
* **deps:** bump flatted from 3.4.1 to 3.4.2 in /booklore-ui ([#73](https://github.com/grimmory-tools/grimmory/issues/73)) ([c4bd0c7](https://github.com/grimmory-tools/grimmory/commit/c4bd0c779ae398968dbdea6d566693ed57eab3eb))
* **funding:** point support links at opencollective ([55c0ac0](https://github.com/grimmory-tools/grimmory/commit/55c0ac089966b8586e7d7bdef1fc469049903d08))
* **release:** 2.2.7 [skip ci] ([0b5e24c](https://github.com/grimmory-tools/grimmory/commit/0b5e24c23848c9e97b04f4221303d0b4bf0b2dd7))
* remove old verbose PR template, replace with temporary more low-key one. ([#84](https://github.com/grimmory-tools/grimmory/issues/84)) ([b868526](https://github.com/grimmory-tools/grimmory/commit/b8685268a9a9730416a209201a70c284cee590c5))
* **ui:** drop financial support dialog ([#21](https://github.com/grimmory-tools/grimmory/issues/21)) ([62be6b1](https://github.com/grimmory-tools/grimmory/commit/62be6b152cbd42910620fdeae1f35783ea258b27))

### Documentation

* updated supported file formats in README.md ([#68](https://github.com/grimmory-tools/grimmory/issues/68)) ([f912e80](https://github.com/grimmory-tools/grimmory/commit/f912e802f97263206308817acd5bab84a5321dcf))

### Style

* **i18n:** normalize translation json formatting ([#89](https://github.com/grimmory-tools/grimmory/issues/89)) ([857290d](https://github.com/grimmory-tools/grimmory/commit/857290d215b518e61c9b3a058ae5d40d5e214672))
* **ui:** simplify the topbar logo branding ([0416d48](https://github.com/grimmory-tools/grimmory/commit/0416d48a6c441792b4c9607b3e472159c879d439))

## [2.2.7](https://github.com/grimmory-tools/grimmory/compare/v2.2.6...v2.2.7) (2026-03-21)

### Bug Fixes

* **api:** fix potential memory leaks in file processing ([031e8ae](https://github.com/grimmory-tools/grimmory/commit/031e8ae257c3354cacce2d17c8c6bc35ce80badb))
* **ci:** correct artifact download action pin ([37ca101](https://github.com/grimmory-tools/grimmory/commit/37ca101dd4bd08ccfe6d4d2395ecc71298cc323d))
* **ci:** publish PR test results from workflow_run ([11a76bf](https://github.com/grimmory-tools/grimmory/commit/11a76bffe12f80d0271e3626291cfbd275346727))
* drop telemetry from app ([#52](https://github.com/grimmory-tools/grimmory/issues/52)) ([4d82cb7](https://github.com/grimmory-tools/grimmory/commit/4d82cb718833a2a4e08ee2f18b2ff3ab9b043dd6))
* **ui:** repair frontend compile after rebrand ([fea1ec6](https://github.com/grimmory-tools/grimmory/commit/fea1ec6930ae64445c6d7e7b38bdc4e7925b51c1))

### Refactors

* **build:** rename frontend dist output to grimmory ([ecf388f](https://github.com/grimmory-tools/grimmory/commit/ecf388f7a2086fbd1d8737a972926d70ec3190d4))
* **i18n:** rename booklore translation keys to grimmory ([eb94afa](https://github.com/grimmory-tools/grimmory/commit/eb94afa7ad600eaa2535802583f21937bd1ae2c0))
* **metadata:** move default parser from Amazon to Goodreads ([e252122](https://github.com/grimmory-tools/grimmory/commit/e252122bc5a89c75af85ddbaae66a14d368479f9))
* pull kepubify & ffprobe during build ([#50](https://github.com/grimmory-tools/grimmory/issues/50)) ([1c15629](https://github.com/grimmory-tools/grimmory/commit/1c15629a10ba2e2ad78455f118e8c937b585b157))
* **ui:** rebrand frontend surfaces to grimmory ([d786dd8](https://github.com/grimmory-tools/grimmory/commit/d786dd8ccbebaa360385dce834816bcb6aaf3b2f))

### Chores

* **api:** remove the custom startup banner ([98c9b1a](https://github.com/grimmory-tools/grimmory/commit/98c9b1ae653c2140b7b2ab86b5511be3376bfe43))
* **deps:** bump flatted from 3.4.1 to 3.4.2 in /booklore-ui ([#73](https://github.com/grimmory-tools/grimmory/issues/73)) ([c4bd0c7](https://github.com/grimmory-tools/grimmory/commit/c4bd0c779ae398968dbdea6d566693ed57eab3eb))
* **funding:** point support links at opencollective ([55c0ac0](https://github.com/grimmory-tools/grimmory/commit/55c0ac089966b8586e7d7bdef1fc469049903d08))
* remove old verbose PR template, replace with temporary more low-key one. ([#84](https://github.com/grimmory-tools/grimmory/issues/84)) ([b868526](https://github.com/grimmory-tools/grimmory/commit/b8685268a9a9730416a209201a70c284cee590c5))
* **ui:** drop financial support dialog ([#21](https://github.com/grimmory-tools/grimmory/issues/21)) ([62be6b1](https://github.com/grimmory-tools/grimmory/commit/62be6b152cbd42910620fdeae1f35783ea258b27))

### Documentation

* updated supported file formats in README.md ([#68](https://github.com/grimmory-tools/grimmory/issues/68)) ([f912e80](https://github.com/grimmory-tools/grimmory/commit/f912e802f97263206308817acd5bab84a5321dcf))

### Style

* **i18n:** normalize translation json formatting ([#89](https://github.com/grimmory-tools/grimmory/issues/89)) ([857290d](https://github.com/grimmory-tools/grimmory/commit/857290d215b518e61c9b3a058ae5d40d5e214672))
* **ui:** simplify the topbar logo branding ([0416d48](https://github.com/grimmory-tools/grimmory/commit/0416d48a6c441792b4c9607b3e472159c879d439))

## [2.2.6](https://github.com/grimmory-tools/grimmory/compare/v2.2.5...v2.2.6) (2026-03-20)

### Bug Fixes

* **bookservice:** fix of missing cover images by returning ByteArrayResource ([#59](https://github.com/grimmory-tools/grimmory/issues/59)) ([b658a0a](https://github.com/grimmory-tools/grimmory/commit/b658a0a778cd9945be502737d7ef1a25770dc123))

## [2.2.5](https://github.com/grimmory-tools/grimmory/compare/v2.2.4...v2.2.5) (2026-03-20)

### Bug Fixes

* **frontend:** fix user permissions checks to use optional chaining ([#58](https://github.com/grimmory-tools/grimmory/issues/58)) ([5fc4773](https://github.com/grimmory-tools/grimmory/commit/5fc4773431a385235632d9d1a25846729ab51ff8))

## [2.2.4](https://github.com/grimmory-tools/grimmory/compare/v2.2.3...v2.2.4) (2026-03-20)

### Bug Fixes

* **release:** publish stable images via reusable workflow ([03f4a8e](https://github.com/grimmory-tools/grimmory/commit/03f4a8e22a6993cf9edd29d510ec74d4b4bf98fe))

## [2.2.3](https://github.com/grimmory-tools/grimmory/compare/v2.2.2...v2.2.3) (2026-03-20)

### Bug Fixes

* **build:** make frontend resources optional in test runs ([dfd7a19](https://github.com/grimmory-tools/grimmory/commit/dfd7a19f5cdc52ac6ae55c5765ad78e8e25bc73c))
* **build:** package frontend assets through Gradle resources ([c4b021f](https://github.com/grimmory-tools/grimmory/commit/c4b021f3b904f565943afe371efc3f1d8cfeec0d))

### Performance

* **ci:** speed up image builds and centralize cache writes ([d148a09](https://github.com/grimmory-tools/grimmory/commit/d148a097c6972c55a44d1e8f66616090e55ca810))

### Chores

* **deps:** bump actions/setup-node from 6.2.0 to 6.3.0 ([#1](https://github.com/grimmory-tools/grimmory/issues/1)) ([489ffb4](https://github.com/grimmory-tools/grimmory/commit/489ffb49efaafe8cea34d262129c7fb0bbbf3b08))
* **deps:** bump docker/build-push-action from 6.19.2 to 7.0.0 ([#3](https://github.com/grimmory-tools/grimmory/issues/3)) ([cd6f37a](https://github.com/grimmory-tools/grimmory/commit/cd6f37a8d6b3bd3f122946606b2cd40231f83c15))
* **deps:** bump docker/login-action from 3.7.0 to 4.0.0 ([#6](https://github.com/grimmory-tools/grimmory/issues/6)) ([9373b50](https://github.com/grimmory-tools/grimmory/commit/9373b50b5b2a90107a07f31f1b4f5a218b3aa870))
* **deps:** bump docker/setup-buildx-action from 3.12.0 to 4.0.0 ([#2](https://github.com/grimmory-tools/grimmory/issues/2)) ([cc4efe5](https://github.com/grimmory-tools/grimmory/commit/cc4efe5897152e1fd089ce4c5e28270f74be3eb4))

### Documentation

* Add a note about how to make a release ([2b2e08b](https://github.com/grimmory-tools/grimmory/commit/2b2e08b19384b913c5740dc1c3c1ebf6ccaac73c))
* Adds a note for moving away from packaging everything in a jar ([2294a38](https://github.com/grimmory-tools/grimmory/commit/2294a388cb0a2771407c3e2c4524ceeaa8a868d1))

## [2.2.2](https://github.com/grimmory-tools/grimmory/compare/v2.2.1...v2.2.2) (2026-03-19)

### Bug Fixes

* **ci:** forward checks permission to reusable test jobs ([e3b3e7c](https://github.com/grimmory-tools/grimmory/commit/e3b3e7c4d7aae23f517b6029af56df360d40edf1))

### Chores

* **ci:** rebrand and streamline the release pipeline ([#66](https://github.com/grimmory-tools/grimmory/issues/66)) ([1bc45da](https://github.com/grimmory-tools/grimmory/commit/1bc45da013415dc66621dc72eb21be9640028f4d))
* Update README to use new links, and clean up formatting ([6b0c4aa](https://github.com/grimmory-tools/grimmory/commit/6b0c4aa66c2247de0335340dbae8ec0afc1fbd59))

# Changelog

All notable Grimmory releases are tracked here.

This file is maintained by semantic-release.
