# Frontend Stub Fill Log

Single-threaded pass to replace generic TODO spec stubs under `frontend/src/app`.

Rules for this pass:
- No runtime code changes.
- Replace generic stubs with real specs where possible.
- If a real spec would need a new runtime seam, keep the spec skipped and state the seam explicitly.
- Commits are optional; this log is the durable grouping record.

## 2026-03-26

### Batch 1
- Scope: initial pure/type-bearing frontend files.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/core/config/api-config.spec.ts`
  - `frontend/src/app/features/author-browser/model/author.model.spec.ts`
  - `frontend/src/app/features/author-browser/service/author-query-keys.spec.ts`
  - `frontend/src/app/features/book/components/book-browser/book-filter/book-filter.config.spec.ts`
  - `frontend/src/app/features/book/components/book-browser/sorting/BookSorter.spec.ts`
  - `frontend/src/app/features/book/model/library.model.spec.ts`
  - `frontend/src/app/features/book/service/library-query-keys.spec.ts`
  - `frontend/src/app/features/metadata/model/request/metadata-refresh-type.enum.spec.ts`
  - `frontend/src/app/features/readers/ebook-reader/state/themes.constant.spec.ts`
  - `frontend/src/app/shared/constants/reset-progress-type.spec.ts`
  - `frontend/src/app/shared/model/app-state.model.spec.ts`
  - `frontend/src/app/shared/websocket/model/log-notification.model.spec.ts`

### Batch 2
- Scope: additional pure models, enums, and type-only request payloads.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/features/book/model/sort.model.spec.ts`
  - `frontend/src/app/features/book/model/shelf.model.spec.ts`
  - `frontend/src/app/features/metadata/model/request/fetch-metadata-request.model.spec.ts`
  - `frontend/src/app/features/metadata/model/request/metadata-refresh-options.model.spec.ts`
  - `frontend/src/app/features/metadata/model/request/metadata-refresh-request.model.spec.ts`
  - `frontend/src/app/features/notebook/model/notebook.model.spec.ts`
  - `frontend/src/app/features/readers/audiobook-player/audiobook.model.spec.ts`
  - `frontend/src/app/features/series-browser/model/series.model.spec.ts`
  - `frontend/src/app/shared/layout/api/menuchangeevent.spec.ts`
  - `frontend/src/app/shared/model/oidc-group-mapping.model.spec.ts`
  - `frontend/src/app/shared/models/api-exception.model.spec.ts`

### Batch 3
- Scope: remaining pure query keys, settings/email contracts, and light index exports.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/shared/service/app-settings-query-keys.spec.ts`
  - `frontend/src/app/features/settings/user-management/user-query-keys.spec.ts`
  - `frontend/src/app/features/settings/email-v2/email-provider.model.spec.ts`
  - `frontend/src/app/features/settings/email-v2/email-recipient.model.spec.ts`
  - `frontend/src/app/features/settings/user-management/content-restriction.model.spec.ts`
  - `frontend/src/app/shared/metadata/metadata-field.config.spec.ts`
  - `frontend/src/app/shared/metadata/embeddable-fields.config.spec.ts`
  - `frontend/src/app/shared/metadata/index.spec.ts`
  - `frontend/src/app/features/readers/audiobook-player/index.spec.ts`

### Batch 4
- Scope: larger pure models and default config exports.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/shared/model/app-settings.model.spec.ts`
  - `frontend/src/app/features/dashboard/models/dashboard-config.model.spec.ts`
  - `frontend/src/app/features/book/model/book.model.spec.ts`

### Batch 5
- Scope: guards, setup flows, and small websocket/helper utilities that do not need runtime seams.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/core/testing/transloco-testing.spec.ts`
  - `frontend/src/app/core/security/guards/manage-library.guard.spec.ts`
  - `frontend/src/app/core/security/guards/bookdrop.guard.spec.ts`
  - `frontend/src/app/core/security/guards/library-stats.guard.spec.ts`
  - `frontend/src/app/core/security/guards/user-stats.guard.spec.ts`
  - `frontend/src/app/core/security/guards/edit-metdata.guard.spec.ts`
  - `frontend/src/app/shared/components/setup/setup.service.spec.ts`
  - `frontend/src/app/shared/components/setup/setup.guard.spec.ts`
  - `frontend/src/app/shared/components/setup/login.guard.spec.ts`
  - `frontend/src/app/shared/components/setup/setup-redirect.guard.spec.ts`
  - `frontend/src/app/shared/websocket/rx-stomp.config.spec.ts`
  - `frontend/src/app/shared/websocket/rx-stomp-service-factory.spec.ts`
  - `frontend/src/app/features/book/components/book-browser/filter-label.helper.spec.ts`

### Batch 6
- Scope: lightweight services and observables that can be exercised without runtime code seams.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/shared/service/page-title.service.spec.ts`
  - `frontend/src/app/shared/service/settings-helper.service.spec.ts`
  - `frontend/src/app/shared/service/icon-picker.service.spec.ts`
  - `frontend/src/app/shared/service/metadata-match-weights.service.spec.ts`
  - `frontend/src/app/shared/websocket/notification-event.service.spec.ts`

### Batch 7
- Scope: shared HTTP-wrapper services with straightforward request/response contracts.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/shared/service/oidc-group-mapping.service.spec.ts`
  - `frontend/src/app/shared/service/book-note.service.spec.ts`
  - `frontend/src/app/shared/service/book-note-v2.service.spec.ts`
  - `frontend/src/app/shared/service/book-mark.service.spec.ts`
  - `frontend/src/app/shared/service/annotation.service.spec.ts`
  - `frontend/src/app/shared/service/reading-session-api.service.spec.ts`
  - `frontend/src/app/shared/service/file-download.service.spec.ts`
  - `frontend/src/app/shared/websocket/rx-stomp.service.spec.ts`
  - `frontend/src/app/shared/services/dialog-launcher.service.spec.ts`
  - `frontend/src/app/shared/service/app-settings.service.spec.ts`

### Batch 8
- Scope: metadata utilities and lightweight shared/book HTTP services.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/shared/services/book-cover.service.spec.ts`
  - `frontend/src/app/shared/service/file-operations.service.spec.ts`
  - `frontend/src/app/features/book/service/metadata-task.spec.ts`
  - `frontend/src/app/shared/metadata/metadata-utils.service.spec.ts`
  - `frontend/src/app/shared/metadata/metadata-form.builder.spec.ts`
  - `frontend/src/app/shared/service/metadata-progress.service.spec.ts`

### Batch 9
- Scope: shared utility services, theme helpers, and seam-documented runtime-heavy specs.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/shared/layout/component/theme-palette-extend.spec.ts`
  - `frontend/src/app/shared/layout/component/theme-configurator/favicon-service.spec.ts`
  - `frontend/src/app/shared/service/pdf-annotation.service.spec.ts`
  - `frontend/src/app/shared/service/audiobook-session.service.spec.ts`
  - `frontend/src/app/shared/service/custom-font.service.spec.ts`
  - `frontend/src/app/shared/service/app-config.service.spec.ts`

### Batch 10
- Scope: shared layout, upload, icon, and reader-session services with real browser/service coverage.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/shared/layout/component/layout-main/service/app.layout.service.spec.ts`
  - `frontend/src/app/shared/layout/component/theme-configurator/background-upload.service.spec.ts`
  - `frontend/src/app/features/readers/audiobook-player/audiobook.service.spec.ts`
  - `frontend/src/app/shared/services/icon.service.spec.ts`
  - `frontend/src/app/shared/service/reading-session.service.spec.ts`

### Batch 11
- Scope: low-friction shared/settings service wrappers plus one explicit dynamic-component seam.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/shared/components/directory-picker/utility.service.spec.ts`
  - `frontend/src/app/features/settings/audit-logs/audit-log.service.spec.ts`
  - `frontend/src/app/features/library-creator/library-loading.service.spec.ts`

### Batch 12
- Scope: book service control-flow surfaces and a query-heavy user-service seam note.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/features/book/service/cbx-reader.service.spec.ts`
  - `frontend/src/app/features/book/service/book-patch.service.spec.ts`
  - `frontend/src/app/features/settings/user-management/user.service.spec.ts`

### Batch 13
- Scope: small shared standalone components with direct behavior or render assertions.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/shared/components/external-doc-link/external-doc-link.component.spec.ts`
  - `frontend/src/app/shared/components/empty/empty.component.spec.ts`
  - `frontend/src/app/shared/components/tag/tag.component.spec.ts`

### Batch 14
- Scope: one pure library-loading component plus local shared components with mocked dialog and websocket behavior.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/features/library-creator/library-loading/library-loading.component.spec.ts`
  - `frontend/src/app/shared/components/live-notification-box/live-notification-box.component.spec.ts`
  - `frontend/src/app/shared/components/directory-picker/directory-picker.component.spec.ts`

### Batch 15
- Scope: runtime-heavy shared and library flows converted from generic placeholders to seam-specific skipped specs.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/features/magic-shelf/service/magic-shelf.service.spec.ts`
  - `frontend/src/app/features/library-creator/library-creator.component.spec.ts`

### Batch 28
- Scope: next settings, book, metadata, and stats shells converted from generic TODO stubs to explicit seam-documented specs.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Remaining generic TODO stubs: `59`
- Files:
  - `frontend/src/app/features/settings/library-metadata-settings/library-metadata-settings.component.spec.ts`
  - `frontend/src/app/features/settings/view-preferences-parent/filter-preferences/filter-preferences.component.spec.ts`
  - `frontend/src/app/features/book/service/book-menu.service.spec.ts`
  - `frontend/src/app/features/book/service/book-file.service.spec.ts`
  - `frontend/src/app/features/settings/settings.component.spec.ts`
  - `frontend/src/app/features/settings/device-settings/device-settings-component.spec.ts`
  - `frontend/src/app/features/bookdrop/component/bookdrop-file-metadata-picker/bookdrop-file-metadata-picker.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/charts/reading-dna-chart/reading-dna-chart.component.spec.ts`
  - `frontend/src/app/features/metadata/component/cover-search/cover-search.component.spec.ts`
  - `frontend/src/app/features/book/components/book-notes/book-notes-component.spec.ts`

### Batch 29
- Scope: KOReader and metadata settings received real coverage where the surfaces were deterministic; the remaining dialog/query-heavy files were converted to explicit seam notes.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Remaining generic TODO stubs: `49`
- Files:
  - `frontend/src/app/features/settings/metadata-settings/metadata-persistence-settings/metadata-persistence-settings-component.spec.ts`
  - `frontend/src/app/features/settings/device-settings/component/koreader-settings/koreader.service.spec.ts`
  - `frontend/src/app/features/settings/device-settings/component/koreader-settings/koreader-settings-component.spec.ts`
  - `frontend/src/app/features/metadata/component/multi-book-metadata-editor/multi-book-metadata-editor-component.spec.ts`
  - `frontend/src/app/features/metadata/component/bulk-metadata-update/bulk-metadata-update-component.spec.ts`
  - `frontend/src/app/features/book/components/book-sender/book-sender.component.spec.ts`
  - `frontend/src/app/features/book/components/series-page/series-page.component.spec.ts`
  - `frontend/src/app/features/settings/metadata-settings/metadata-settings-component.spec.ts`
  - `frontend/src/app/features/bookdrop/component/bookdrop-pattern-extract-dialog/bookdrop-pattern-extract-dialog.component.spec.ts`
  - `frontend/src/app/features/settings/user-profile-dialog/user-profile-dialog.component.spec.ts`

### Batch 30
- Scope: simple dialog, reader footer, kobo settings, public review settings, and book-card-lite now have real tests; the surrounding shell-heavy reader/email/file-attach surfaces were converted to explicit seam notes.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Remaining generic TODO stubs: `39`
- Files:
  - `frontend/src/app/features/bookdrop/component/bookdrop-finalize-result-dialog/bookdrop-finalize-result-dialog.component.spec.ts`
  - `frontend/src/app/features/readers/cbx-reader/layout/footer/cbx-footer.component.spec.ts`
  - `frontend/src/app/features/settings/device-settings/component/kobo-sync-settings/kobo.service.spec.ts`
  - `frontend/src/app/features/settings/metadata-settings/public-reviews-settings/public-reviews-settings-component.spec.ts`
  - `frontend/src/app/features/book/components/book-card-lite/book-card-lite-component.spec.ts`
  - `frontend/src/app/features/readers/cbx-reader/layout/sidebar/cbx-sidebar.component.spec.ts`
  - `frontend/src/app/features/readers/ebook-reader/dialogs/settings-dialog.component.spec.ts`
  - `frontend/src/app/features/settings/email-v2/email-v2-recipient/email-v2-recipient.component.spec.ts`
  - `frontend/src/app/features/settings/email-v2/email-v2-provider/email-v2-provider.component.spec.ts`
  - `frontend/src/app/features/book/components/book-file-attacher/book-file-attacher.component.spec.ts`
  - `frontend/src/app/shared/layout/component/layout-topbar/app.topbar.component.spec.ts`
  - `frontend/src/app/shared/components/book-uploader/book-uploader.component.spec.ts`
  - `frontend/src/app/features/settings/custom-fonts/custom-fonts.component.spec.ts`

### Batch 16
- Scope: layout-heavy shells converted from generic placeholders to seam-specific skipped specs.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/shared/layout/component/theme-configurator/theme-configurator.component.spec.ts`
  - `frontend/src/app/shared/layout/component/layout-main/app.layout.component.spec.ts`
  - `frontend/src/app/shared/layout/component/layout-sidebar/app.sidebar.component.spec.ts`
  - `frontend/src/app/shared/layout/component/layout-menu/app.menu.component.spec.ts`
  - `frontend/src/app/shared/layout/component/layout-menu/app.menuitem.component.spec.ts`

### Batch 17
- Scope: real coverage for shared standalone form and dialog components with direct control-flow assertions.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/shared/components/change-password/change-password.component.spec.ts`
  - `frontend/src/app/shared/components/icon-display/icon-display.component.spec.ts`
  - `frontend/src/app/shared/components/setup/setup.component.spec.ts`
  - `frontend/src/app/shared/layout/component/theme-configurator/upload-dialog/upload-dialog.component.spec.ts`
  - `frontend/src/app/shared/layout/component/layout-menu/version-changelog-dialog/version-changelog-dialog.component.spec.ts`

### Batch 18
- Scope: one real shared progress widget, one real stats HTTP wrapper, and seam-specific replacements for query-runtime-heavy services/components.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/shared/components/metadata-progress-widget/metadata-progress-widget-component.spec.ts`
  - `frontend/src/app/features/settings/user-management/user-stats.service.spec.ts`
  - `frontend/src/app/features/book/service/shelf.service.spec.ts`
  - `frontend/src/app/features/book/service/library.service.spec.ts`
  - `frontend/src/app/shared/components/icon-picker/icon-picker-component.spec.ts`

### Batch 19
- Scope: bookdrop API/control-flow coverage plus one seam-specific replacement for a menu-command-heavy service.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/features/bookdrop/service/bookdrop-file-api.service.spec.ts`
  - `frontend/src/app/features/bookdrop/service/bookdrop-file.service.spec.ts`
  - `frontend/src/app/features/bookdrop/service/bookdrop.service.spec.ts`
  - `frontend/src/app/features/book/service/book-metadata.service.spec.ts`
  - `frontend/src/app/features/book/service/library-shelf-menu.service.spec.ts`

### Batch 20
- Scope: shared utility components with real coverage plus seam-specific replacements for reader/chart-heavy surfaces that still lack clean injectable seams.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/shared/components/cover-generator/cover-generator.component.spec.ts`
  - `frontend/src/app/shared/components/unified-notification-popover/unified-notification-popover-component.spec.ts`
  - `frontend/src/app/shared/components/file-mover/file-mover-component.spec.ts`
  - `frontend/src/app/features/readers/pdf-reader/pdf-reader.component.spec.ts`
  - `frontend/src/app/features/readers/cbx-reader/cbx-reader.component.spec.ts`
  - `frontend/src/app/features/readers/audiobook-player/audiobook-player.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/charts/genre-stats-chart/genre-stats-chart.component.spec.ts`

### Batch 21
- Scope: book cache/control-flow services with real coverage, one real series-card component spec, and seam-specific replacements for query-runtime and invalid-template surfaces.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/features/book/service/book-socket.service.spec.ts`
  - `frontend/src/app/features/book/service/book-metadata-manage.service.spec.ts`
  - `frontend/src/app/features/series-browser/components/series-card/series-card.component.spec.ts`
  - `frontend/src/app/features/book/service/book.service.spec.ts`
  - `frontend/src/app/features/series-browser/components/series-scroller/series-scroller.component.spec.ts`

### Batch 22
- Scope: reader-preference and naming settings with real control-flow coverage, plus seam-specific replacements for browser-heavy settings shells and dialogs.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/features/settings/reader-preferences/cbx-reader-preferences/cbx-reader-preferences-component.spec.ts`
  - `frontend/src/app/features/settings/global-preferences/metadata-match-weights/metadata-match-weights-component.spec.ts`
  - `frontend/src/app/features/settings/file-naming-pattern/file-naming-pattern.component.spec.ts`
  - `frontend/src/app/features/settings/reader-preferences/pdf-reader-preferences/pdf-reader-preferences-component.spec.ts`
  - `frontend/src/app/features/settings/view-preferences-parent/sidebar-sorting-preferences/sidebar-sorting-preferences.component.spec.ts`
  - `frontend/src/app/features/settings/global-preferences/global-preferences.component.spec.ts`
  - `frontend/src/app/features/settings/custom-fonts/font-upload-dialog/font-upload-dialog.component.spec.ts`
  - `frontend/src/app/features/settings/audit-logs/audit-logs.component.spec.ts`
  - `frontend/src/app/features/settings/view-preferences-parent/view-preferences/view-preferences.component.spec.ts`
  - `frontend/src/app/features/settings/global-preferences/metadata-provider-settings/metadata-provider-settings.component.spec.ts`

### Batch 23
- Scope: dashboard and stats visualization surfaces converted from generic placeholders to explicit seam notes where real coverage would require chart, canvas, scroll, or dashboard orchestration adapters.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/features/dashboard/components/dashboard-scroller/dashboard-scroller.component.spec.ts`
  - `frontend/src/app/features/dashboard/components/dashboard-settings/dashboard-settings.component.spec.ts`
  - `frontend/src/app/features/dashboard/components/main-dashboard/main-dashboard.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/charts/reading-session-timeline/reading-session-timeline.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/charts/session-archetypes-chart/session-archetypes-chart.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/charts/favorite-days-chart/favorite-days-chart.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/charts/book-flow-chart/book-flow-chart.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/charts/completion-timeline-chart/completion-timeline-chart.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/charts/peak-hours-chart/peak-hours-chart.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/charts/reading-session-heatmap/reading-session-heatmap.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/charts/read-status-chart/read-status-chart.component.spec.ts`
  - `frontend/src/app/features/notebook/components/notebook/notebook.component.spec.ts`

### Batch 24
- Scope: additional user-stats chart placeholders converted into explicit seam notes for chart-runtime, effect, callback, and aggregation boundaries instead of generic TODO stubs.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/features/stats/component/user-stats/charts/page-turner-chart/page-turner-chart.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/charts/reading-progress-chart/reading-progress-chart.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/charts/completion-race-chart/completion-race-chart.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/charts/rating-taste-chart/rating-taste-chart.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/charts/reading-clock-chart/reading-clock-chart.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/charts/personal-rating-chart/personal-rating-chart.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/charts/reading-heatmap-chart/reading-heatmap-chart.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/charts/reading-habits-chart/reading-habits-chart.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/charts/series-progress-chart/series-progress-chart.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/charts/book-length-chart/book-length-chart.component.spec.ts`

### Batch 25
- Scope: author-browser, book dialog, user-management, and additional stats surfaces converted from generic placeholders into explicit seam notes where real coverage would require routed dialog stacks, Prime widget runtime, or chart adapters.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/features/author-browser/components/author-photo-search/author-photo-search.component.spec.ts`
  - `frontend/src/app/features/author-browser/components/author-card/author-card.component.spec.ts`
  - `frontend/src/app/features/author-browser/components/author-browser/author-browser.component.spec.ts`
  - `frontend/src/app/features/author-browser/components/author-detail/author-detail.component.spec.ts`
  - `frontend/src/app/features/author-browser/components/author-editor/author-editor.component.spec.ts`
  - `frontend/src/app/features/author-browser/components/author-match/author-match.component.spec.ts`
  - `frontend/src/app/features/book/components/book-searcher/book-searcher.component.spec.ts`
  - `frontend/src/app/features/book/components/duplicate-merger/duplicate-merger.component.spec.ts`
  - `frontend/src/app/features/book/components/add-physical-book-dialog/add-physical-book-dialog.component.spec.ts`
  - `frontend/src/app/features/book/components/shelf-assigner/shelf-assigner.component.spec.ts`
  - `frontend/src/app/features/settings/user-management/user-management.component.spec.ts`
  - `frontend/src/app/features/settings/user-management/create-user-dialog/create-user-dialog.component.spec.ts`
  - `frontend/src/app/features/settings/user-management/content-restrictions-editor/content-restrictions-editor.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/charts/publication-era-chart/publication-era-chart.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/charts/reading-debt-chart/reading-debt-chart.component.spec.ts`

### Batch 26
- Scope: simple reader dialogs and header upgraded to real specs, while library-stats, reader-shell, and bookdrop orchestration placeholders were replaced with seam-specific notes.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/features/readers/cbx-reader/dialogs/cbx-shortcuts-help.component.spec.ts`
  - `frontend/src/app/features/readers/cbx-reader/dialogs/cbx-note-dialog.component.spec.ts`
  - `frontend/src/app/features/readers/ebook-reader/dialogs/note-dialog.component.spec.ts`
  - `frontend/src/app/features/readers/ebook-reader/dialogs/metadata-dialog.component.spec.ts`
  - `frontend/src/app/features/readers/cbx-reader/layout/header/cbx-header.component.spec.ts`
  - `frontend/src/app/features/stats/component/library-stats/library-stats.component.spec.ts`
  - `frontend/src/app/features/bookdrop/component/bookdrop-bulk-edit-dialog/bookdrop-bulk-edit-dialog.component.spec.ts`
  - `frontend/src/app/features/series-browser/components/series-browser/series-browser.component.spec.ts`
  - `frontend/src/app/features/stats/component/library-stats/charts/metadata-score-chart/metadata-score-chart.component.spec.ts`
  - `frontend/src/app/features/bookdrop/component/bookdrop-files-widget/bookdrop-files-widget.component.spec.ts`
  - `frontend/src/app/features/readers/ebook-reader/ebook-reader.component.spec.ts`
  - `frontend/src/app/features/stats/component/library-stats/charts/book-formats-chart/book-formats-chart.component.spec.ts`

### Batch 27
- Scope: remaining library-stats chart placeholders plus ebook and CBX shell surfaces converted from generic TODO stubs into explicit chart/runtime seam notes.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Files:
  - `frontend/src/app/features/stats/component/library-stats/charts/top-items-chart/top-items-chart.component.spec.ts`
  - `frontend/src/app/features/stats/component/library-stats/charts/page-count-chart/page-count-chart.component.spec.ts`
  - `frontend/src/app/features/stats/component/library-stats/charts/language-chart/language-chart.component.spec.ts`
  - `frontend/src/app/features/stats/component/library-stats/charts/publication-timeline-chart/publication-timeline-chart.component.spec.ts`
  - `frontend/src/app/features/stats/component/library-stats/charts/reading-journey-chart/reading-journey-chart.component.spec.ts`
  - `frontend/src/app/features/stats/component/library-stats/charts/author-universe-chart/author-universe-chart.component.spec.ts`
  - `frontend/src/app/features/stats/component/library-stats/charts/publication-trend-chart/publication-trend-chart.component.spec.ts`
  - `frontend/src/app/features/readers/ebook-reader/dialogs/shortcuts-help.component.spec.ts`
  - `frontend/src/app/features/readers/ebook-reader/layout/header/header.component.spec.ts`
  - `frontend/src/app/features/readers/cbx-reader/layout/quick-settings/cbx-quick-settings.component.spec.ts`

### Batch 31
- Scope: branch-heavy book helper/services upgraded to real specs, while book filter/table and upload dialog placeholders were replaced with explicit component seam notes.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Remaining generic TODO stubs after batch: `29`
- Files:
  - `frontend/src/app/features/book/components/book-reviews/book-review-service.spec.ts`
  - `frontend/src/app/features/book/components/book-browser/book-dialog-helper.service.spec.ts`
  - `frontend/src/app/features/book/components/book-browser/book-filter-orchestration.service.spec.ts`
  - `frontend/src/app/features/book/components/book-browser/book-card-overlay-preference.service.spec.ts`
  - `frontend/src/app/features/book/components/book-browser/book-filter/book-filter.service.spec.ts`
  - `frontend/src/app/features/book/components/book-browser/book-filter/book-filter.component.spec.ts`
  - `frontend/src/app/features/book/components/book-browser/book-table/book-table.component.spec.ts`
  - `frontend/src/app/features/book/components/book-reviews/book-reviews.component.spec.ts`
  - `frontend/src/app/features/book/components/additional-file-uploader/additional-file-uploader.component.spec.ts`
  - `frontend/src/app/features/book/components/bulk-isbn-import-dialog/bulk-isbn-import-dialog.component.spec.ts`

### Batch 32
- Scope: finished the remaining generic frontend coverage stubs by adding direct class or HTTP tests for the cheap service surfaces and replacing the heavy Angular or PrimeNG or reader-shell placeholders with explicit seam-noted skipped specs.
- Status: completed.
- Validation:
  - `just ui typecheck`
  - `just ui lint`
  - `just ui test`
- Remaining generic TODO stubs after batch: `0`
- Files:
  - `frontend/src/app/features/settings/device-settings/component/hardcover-settings/hardcover-sync-settings.service.spec.ts`
  - `frontend/src/app/features/book/components/book-browser/filters/sidebar-filter-toggle-pref.service.spec.ts`
  - `frontend/src/app/features/readers/ebook-reader/features/annotations/annotation.service.spec.ts`
  - `frontend/src/app/features/readers/ebook-reader/index.spec.ts`
  - `frontend/src/app/features/readers/ebook-reader/shared/icon.component.spec.ts`
  - `frontend/src/app/features/settings/reader-preferences/reader-preferences.component.spec.ts`
  - `frontend/src/app/features/stats/component/user-stats/user-stats.component.spec.ts`
  - `frontend/src/app/features/book/components/book-browser/book-browser.component.spec.ts`
  - `frontend/src/app/features/book/components/book-browser/lock-unlock-metadata-dialog/lock-unlock-metadata-dialog.component.spec.ts`
  - `frontend/src/app/features/book/components/book-browser/sorting/multi-sort-popover/multi-sort-popover.component.spec.ts`
  - `frontend/src/app/features/book/components/shelf-creator/shelf-creator.component.spec.ts`
  - `frontend/src/app/features/book/components/shelf-edit-dialog/shelf-edit-dialog.component.spec.ts`
  - `frontend/src/app/features/metadata/component/book-metadata-center/book-metadata-center.component.spec.ts`
  - `frontend/src/app/features/metadata/component/book-metadata-center/metadata-editor/metadata-editor.component.spec.ts`
  - `frontend/src/app/features/metadata/component/book-metadata-center/metadata-searcher/metadata-searcher.component.spec.ts`
  - `frontend/src/app/features/metadata/component/book-metadata-center/metadata-viewer/metadata-tabs/metadata-tabs.component.spec.ts`
  - `frontend/src/app/features/readers/ebook-reader/core/view-manager.service.spec.ts`
  - `frontend/src/app/features/readers/ebook-reader/layout/footer/footer.component.spec.ts`
  - `frontend/src/app/features/readers/ebook-reader/layout/header/quick-settings.component.spec.ts`
  - `frontend/src/app/features/readers/ebook-reader/layout/panel/panel.component.spec.ts`
  - `frontend/src/app/features/readers/ebook-reader/layout/sidebar/sidebar.component.spec.ts`
  - `frontend/src/app/features/readers/ebook-reader/shared/selection-popup.component.spec.ts`
  - `frontend/src/app/features/settings/device-settings/component/hardcover-settings/hardcover-settings-component.spec.ts`
  - `frontend/src/app/features/settings/device-settings/component/kobo-sync-settings/kobo-sync-settings-component.spec.ts`
  - `frontend/src/app/features/settings/email-v2/create-email-provider-dialog/create-email-provider-dialog.component.spec.ts`
  - `frontend/src/app/features/settings/email-v2/create-email-recipient-dialog/create-email-recipient-dialog.component.spec.ts`
  - `frontend/src/app/features/settings/email-v2/email-v2.component.spec.ts`
  - `frontend/src/app/features/settings/opds-settings/opds-settings.spec.ts`
  - `frontend/src/app/features/settings/reader-preferences/epub-reader-preferences/epub-reader-preferences-component.spec.ts`

## Proposed Commit Shapes

These are controller-friendly Conventional Commit shapes for the logged batches. Each body is grouped markdown that can be used directly or merged into larger follow-up commits.

### Batch 1
- Subject: `test(frontend): cover pure models and config contracts`
- Body:
  - `- Coverage: add type-level specs for pure frontend config, model, and enum surfaces under core, author-browser, book, metadata, readers, and shared.`
  - `- Intent: replace placeholder specs with cheap deterministic assertions that lock down static contract behavior without runtime seams.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 2
- Subject: `test(frontend): cover request payload and contract models`
- Body:
  - `- Coverage: add specs for additional book, metadata, notebook, readers, series-browser, and shared request or model contracts.`
  - `- Intent: keep the stub-replacement pass moving through deterministic type-bearing files before shifting into heavier service and component surfaces.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 3
- Subject: `test(frontend): cover query keys and barrel exports`
- Body:
  - `- Coverage: add tests for shared and settings query keys, email contracts, metadata exports, and audiobook barrel exports.`
  - `- Intent: close out low-risk placeholder specs where real value comes from locking contract shape and export stability.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 4
- Subject: `test(frontend): cover larger model defaults and dashboard contracts`
- Body:
  - `- Coverage: add specs for the main app-settings, dashboard, and book models.`
  - `- Intent: finish the remaining pure model stubs before shifting toward control-flow and service behavior.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 5
- Subject: `test(frontend): cover setup and guard utilities`
- Body:
  - `- Coverage: add deterministic tests for setup services, setup guards, security guards, websocket helpers, and small filter helpers.`
  - `- Intent: replace placeholder coverage with branch-bearing guard and bootstrap assertions that do not require runtime code changes.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 6
- Subject: `test(frontend): cover lightweight shared services`
- Body:
  - `- Coverage: add tests for page title, settings helper, icon picker, metadata-match weights, and notification event services.`
  - `- Intent: convert low-friction service placeholders into real control-flow coverage before moving to HTTP-heavy wrappers.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 7
- Subject: `test(frontend): cover shared HTTP service wrappers`
- Body:
  - `- Coverage: add request and response tests for shared annotation, note, bookmark, reading-session, websocket, and group-mapping services.`
  - `- Intent: replace generic HTTP-wrapper stubs with real endpoint-shape coverage that can fail honestly when request contracts drift.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 8
- Subject: `test(frontend): cover metadata utilities and book HTTP helpers`
- Body:
  - `- Coverage: add tests for metadata form and utility services, file operations, book cover helpers, metadata progress, and metadata-task behavior.`
  - `- Intent: push coverage into utility and helper layers that affect metadata control flow without needing heavy component harnesses.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 9
- Subject: `test(frontend): cover shared utilities and document seam-heavy specs`
- Body:
  - `- Coverage: add real tests for theme, PDF annotation, audiobook session, and config helpers while converting the remaining runtime-heavy shared stubs into explicit seam notes.`
  - `- Intent: separate deterministic coverage wins from surfaces that still need browser or runtime harnesses later.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 10
- Subject: `test(frontend): cover layout upload and reader-session services`
- Body:
  - `- Coverage: add real tests for layout service, background upload, audiobook service, icon service, and reading-session behavior.`
  - `- Intent: grow coverage through shared layout and reader utilities without changing runtime code.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 11
- Subject: `test(frontend): cover lightweight settings wrappers`
- Body:
  - `- Coverage: add tests for directory-picker utilities, audit-log service behavior, and the low-friction library-loading path.`
  - `- Intent: keep the pass focused on cheap deterministic seams while documenting the remaining dynamic component seam explicitly.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 12
- Subject: `test(frontend): cover book service control flow`
- Body:
  - `- Coverage: add real service tests for CBX reader and book patch flows and replace the user-service placeholder with a seam-aware spec.`
  - `- Intent: increase branch coverage in book service layers while acknowledging the query-heavy user-service runtime boundary.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 13
- Subject: `test(frontend): cover small shared standalone components`
- Body:
  - `- Coverage: add direct component behavior tests for external links, empty states, and tag rendering.`
  - `- Intent: clear straightforward standalone component stubs without spending time on decorative template-only churn.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 14
- Subject: `test(frontend): cover library loading and local shared components`
- Body:
  - `- Coverage: add tests for library-loading, live-notification, and directory-picker components using mocked local dependencies.`
  - `- Intent: pick off component surfaces that have real behavior but do not require heavy application orchestration.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 15
- Subject: `test(frontend): document shared runtime seams`
- Body:
  - `- Coverage: replace remaining shared and library runtime-heavy placeholders with explicit seam-noted skipped specs.`
  - `- Intent: make the remaining blocker surfaces auditable instead of leaving generic TODO stubs behind.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 16
- Subject: `test(frontend): document layout shell seams`
- Body:
  - `- Coverage: convert layout-heavy shell placeholders into seam-specific skipped specs.`
  - `- Intent: record the need for mounted layout harnesses without spending time on brittle shallow shell tests.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 17
- Subject: `test(frontend): cover shared forms and dialogs`
- Body:
  - `- Coverage: add real tests for shared standalone form and dialog components with direct control-flow assertions.`
  - `- Intent: capture meaningful component behavior where the harness cost is still low enough to justify real tests.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 18
- Subject: `test(frontend): cover progress and stats wrappers`
- Body:
  - `- Coverage: add real tests for a shared progress widget and a stats HTTP wrapper while documenting heavier query-driven seams.`
  - `- Intent: keep coverage growth honest by splitting easy wrappers from runtime-heavy component and service surfaces.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 19
- Subject: `test(frontend): cover bookdrop API branches`
- Body:
  - `- Coverage: add bookdrop API and control-flow coverage and replace the remaining menu-command-heavy service stub with a seam note.`
  - `- Intent: grow branch coverage in bookdrop services while avoiding low-yield template churn.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 20
- Subject: `test(frontend): cover shared utility components`
- Body:
  - `- Coverage: add real tests for shared utility components and convert heavier reader or chart placeholders into explicit seam-noted skips.`
  - `- Intent: continue replacing generic placeholders while keeping runtime-heavy chart and reader shells out of the main path.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 21
- Subject: `test(frontend): cover book cache and series card behavior`
- Body:
  - `- Coverage: add real tests for book cache and control-flow services plus the series-card component, and document the remaining query-runtime seams.`
  - `- Intent: prioritize behavior-bearing book surfaces instead of spreading effort over decorative render-only coverage.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 22
- Subject: `test(frontend): cover reader preference settings flows`
- Body:
  - `- Coverage: add real tests for reader-preference and naming settings while replacing browser-heavy settings shells and dialogs with seam notes.`
  - `- Intent: move settings coverage upward without inventing runtime seams for the stubborn UI-heavy surfaces.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 23
- Subject: `test(frontend): document dashboard and stats visualization seams`
- Body:
  - `- Coverage: replace dashboard and stats visualization placeholders with explicit chart, canvas, scroll, and orchestration seam notes.`
  - `- Intent: make the expensive visualization blockers explicit instead of keeping them as generic unowned TODO stubs.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 24
- Subject: `test(frontend): document remaining user stats chart seams`
- Body:
  - `- Coverage: convert another wave of user-stats chart placeholders into explicit runtime seam notes.`
  - `- Intent: keep the coverage pass honest by documenting chart-runtime blockers without pretending that render smoke tests would help.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 25
- Subject: `test(frontend): document author-browser and dialog seams`
- Body:
  - `- Coverage: replace author-browser, book-dialog, user-management, and additional stats placeholders with routed dialog or widget seam notes.`
  - `- Intent: record the real blocker surfaces cleanly and avoid low-yield retries on complex dialog stacks.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 26
- Subject: `test(frontend): cover reader dialogs and note library-stats seams`
- Body:
  - `- Coverage: add real tests for simple reader dialogs and header behavior and document the remaining library-stats, reader-shell, and bookdrop orchestration seams.`
  - `- Intent: combine cheap reader wins with explicit notes for surfaces that still need mounted runtime harnesses.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 27
- Subject: `test(frontend): document remaining library stats and reader shell seams`
- Body:
  - `- Coverage: replace the remaining library-stats chart placeholders and ebook or CBX shell stubs with explicit chart and runtime seam notes.`
  - `- Intent: finish the chart-heavy placeholder cleanup in a durable, auditable way.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 28
- Subject: `test(frontend): document settings and metadata shell seams`
- Body:
  - `- Coverage: convert another wave of settings, book, metadata, and stats shells from generic TODO stubs into explicit seam-documented specs.`
  - `- Intent: keep the pass moving through high-friction surfaces without faking useful coverage.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 29
- Subject: `test(frontend): cover deterministic settings services`
- Body:
  - `- Coverage: add real tests for KOReader and metadata settings surfaces where the behavior is deterministic, and document the remaining dialog-heavy seams.`
  - `- Intent: take the cheap service and settings wins while isolating the harder modal and query-driven blockers.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 30
- Subject: `test(frontend): cover selected reader and settings widgets`
- Body:
  - `- Coverage: add real tests for a simple dialog, the CBX footer, kobo settings service behavior, public review settings, and book-card-lite.`
  - `- Intent: harvest another set of low-friction component and service wins while documenting the surrounding reader and email shell seams.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 31
- Subject: `test(frontend): cover book helper and review services`
- Body:
  - `- Coverage: add real tests for book review, dialog helper, orchestration, and overlay-preference services, and convert the remaining book browser and upload shells into explicit seam notes.`
  - `- Intent: push branch coverage into real book control flow instead of spreading effort over decorative component smoke tests.`
  - `- Validation: run just ui typecheck, just ui lint, and just ui test.`

### Batch 32
- Subject: `test(frontend): finish replacing generic spec stubs`
- Body:
  - `- Coverage: close out the remaining generic TODO stubs with real HTTP or class-level specs where cheap, and explicit seam-noted skipped specs where heavier Angular, PrimeNG, or reader-shell harnesses are still required.`
  - `- Result: bring the generic TODO stub count under frontend src app to zero while preserving the no-runtime-change rule.`
  - `- Validation: run just ui typecheck, just ui lint, just ui test, and finish with just ui check.`
