# Build an APK on GitHub

1. Open **Actions → Build APK → Run workflow** in this fork.
2. Under **Use workflow from**, select the branch to build. Select `fix/adb-stability-shizuku-uad` to test the unmerged ADB/Shizuku/UAD changes, or `master` for the default branch.
3. Leave **Source branch, tag or commit** blank to build the selected branch. Optionally enter a branch, tag, or commit to override the source.
4. Click **Run workflow**. When it succeeds, open the run and download the `AppManager-debug-…` artifact from **Artifacts** or the download link in the job summary.
5. Extract the ZIP and install the APK. SHA-256 checksums are included.

The workflow runs `:app:testDebugUnitTest` and builds `:app:assembleDebug` with Java 21 and recursive submodules. An APK is uploaded only when tests and the build pass. The APK uses the repository's development key and installs as **AM Debug**, alongside the regular app. No signing secrets need to be configured. The source commit is included in the artifact and APK filenames, and downloads are retained for 30 days.

The workflow is available on `master` and `fix/adb-stability-shizuku-uad`. GitHub requires the workflow file to exist in the branch selected under **Use workflow from**. For another branch that does not contain the file, keep the workflow branch on `master` and enter that branch in the source-ref field instead.

Editing this workflow also runs an automatic build to validate the change; ordinary source pushes do not trigger this APK workflow.
