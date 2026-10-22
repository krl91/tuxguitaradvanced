# Quickstart: validating spec 015

1. Unit: `cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa && mvn -e test -pl ../../TuxGuitar-practice -am -Dtest='BuildIdentityTest,TitleLayoutDefaultTest,BuildInfoWiringTest,BuildInfoStringsTest' -Dsurefire.failIfNoSpecifiedTests=false`.
2. Local macOS build (`mvn -e clean verify -P native-modules`), open About: title `TuxGuitarAdvanced dev`, short commit, `modified` if the tree is dirty; Copy button then paste (US4).
3. Main window title (default layout): `TuxGuitarAdvanced dev - <file>`.
4. Verifier self-tests: `misc/test-verify-version.sh` and `misc/test-verify-release.sh` (each wrong value is detected; cases needing tools absent on this machine run in CI).
5. Release: dispatch with a labelled tag (needs the owner's go); read the 9 places in the four packages with `misc/verify-version.sh` and the release with `misc/verify-release.sh` (draft mode, then published mode); open About of the macOS package.
6. A `.tg` saved by the new build has the same header stamp as one saved before (SC-005).
Windows/Linux are validated by CI only; the report must say so.
