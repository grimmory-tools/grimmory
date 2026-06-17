# GrimmLink Release Checklist

Final QA checklist before promoting to `main` and tagging a release.

## Backend

- [ ] Build passes (`./gradlew assemble` or equivalent)
- [ ] GrimmLink tests pass (`./gradlew test --tests "org.booklore.grimmlink.*"`)
- [ ] Full backend tests pass (`./gradlew test`)
- [ ] DB migration clean install (fresh database from scratch)
- [ ] DB migration upgrade from previous fork version
- [ ] No unexpected `AccessDenied` noise in server logs
- [ ] OPF/RDF malformed files do not stop metadata refresh

## KOReader / GrimmLink

- [ ] Auth works — login with username + auth key
- [ ] Progress push works — KOReader sends percentage, server stores it
- [ ] WebUI shows KOReader reading percentage
- [ ] PDF bridge works — PDF progress syncs between KOReader and built-in reader
- [ ] Reading sessions push/query works
- [ ] Shelf sync works — regular and magic shelves
- [ ] Metadata push works — ratings, bookmarks, annotations
- [ ] Metadata pull works — pulled metadata reaches another device/session
- [ ] Pull again does not duplicate metadata

## Release

- [ ] `develop` is clean (no stray commits, no unmerged WIP)
- [ ] Release branch merged into `develop`
- [ ] `develop` tested as a whole
- [ ] PR opened from `develop` to `main`
- [ ] Tag pushed or auto-created (`vX.Y.Z`)
- [ ] Image build verified (Docker build + smoke test)
