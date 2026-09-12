# Build an APK on GitHub

1. Open **Actions → Build APK → Run workflow** in this fork.
2. Keep the workflow branch set to `master` (the default branch).
3. Leave **Source branch, tag or commit** blank to build `master`, or enter another branch, tag, or commit from this repository. To test the unmerged ADB/Shizuku/UAD changes, enter `fix/adb-stability-shizuku-uad`.
4. Click **Run workflow**. When it succeeds, open the run and download the `AppManager-debug-…` artifact from **Artifacts** or the download link in the job summary.
5. Extract the ZIP and install the APK. SHA-256 checksums are included.

The workflow builds `:app:assembleDebug` with Java 21 and recursive submodules. The APK uses the repository's development key and installs as **AM Debug**, alongside the regular app. No signing secrets need to be configured. The source commit is included in the artifact and APK filenames, and downloads are retained for 30 days.

The workflow is available on `master` so the manual trigger appears in GitHub. The source-ref field can build branches that do not contain the workflow file themselves. Editing this workflow on `master` also runs an automatic build to validate the change; ordinary source pushes do not trigger this APK workflow.
