# ADB startup, Shizuku, and UAD-ng

The package database must not depend on a successful privileged metadata query. Current-user package enumeration falls back to the app's PackageManager when a privileged query fails. Unavailable or unselected profiles retain their cached entries. Failed individual queries are not interpreted as uninstallations. Optional metadata and backup enumeration failures are logged while the basic package list is retained. Tracker matching uses a Java fallback when the native accelerator cannot load; search/close operations are serialized to prevent freeing native state during a scan.

Service connections use a separate callback and completion latch per attempt. Unbinding clears stale binders; late callbacks cannot complete a newer attempt. Capability reads never wait for a binding lock on the UI thread. Loss of the ADB transport does not discard still-live Binder services, and old ADB broadcasts cannot replace Shizuku/root sessions. Rebinding preserves the launch identity, and forced transitions restore direct-root state after cleanup. Proxy transactions restore Binder calling identity even when the target throws.

## Shizuku

Start Shizuku (or Sui) and select **Settings → Mode of operation → Shizuku**. Approve App Manager in the Shizuku permission prompt. Android 6+ and Shizuku API 12+ are required. If permission was permanently denied, enable App Manager in Shizuku's authorized-app list before retrying.

The integration uses the [official Shizuku user-service API](https://github.com/RikkaApps/Shizuku-API), supplying both IAMService and the remote filesystem from one process. Existing package, permission, shell, and filesystem operations use that backend. Root capabilities remain dependent on Shizuku's actual UID; Shizuku started through ADB has shell permissions. App Manager does not start Shizuku or bypass Android/OEM permission restrictions. No separate App Manager ADB pairing is required for this mode.

## UAD-ng dataset

The bundled `app/src/main/assets/uad_lists.json` is an unmodified snapshot of [Universal Android Debloater Next Generation](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation), commit `64465c850c7ed36329e67165ac08501abffb218e`, retrieved 2026-09-12. It contains 5,372 entries and is distributed under the upstream GPL-3.0 license (see this repository's GPL license and the upstream LICENSE).

Source URL: https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/refs/heads/main/resources/assets/uad_lists.json

SHA-256: `0fd756ad820ee34e282c32500b55f0d032cd495ece50266bb9be6176a465d7ef`

Descriptions, dependencies, neededBy relationships, and labels are preserved. Lists use bracketed badges such as `[OEM]` and ratings use UAD's exact names: Recommended, Advanced, Expert, Unsafe. The existing filter bits remain compatible (Safe → Recommended, Replace → Advanced, Caution → Expert, Unsafe → Unsafe). Unknown/missing ratings are treated as Unsafe. Ratings are guidance, and selecting a category never automatically removes packages.

To update, download the source JSON and run `python3 scripts/update_uad_list.py /path/to/uad_lists.json`; update the recorded upstream commit and hash here. The importer validates all entries before replacing the asset and preserves the original bytes. The app uses the bundled file offline.

## Device regression checks

1. Clear the debug app's data, select ADB mode, pair/connect, and verify the app list loads without a no-root detour.
2. Repeat after force-stop, reboot/re-pair, wireless-debugging restart, a changed connection port, and screen rotation during connection.
3. Toggle no-root → ADB → root (where available) → Shizuku. Confirm reported UID and actual capabilities agree.
4. Try an OEM denying shell permission grants/usage access, a locked work profile, and a denied cross-user query. Basic current-user apps should remain visible, and cached inaccessible profiles should not be deleted.
5. Grant, deny, and permanently deny Shizuku authorization; rotate during the prompt. Stop/restart Shizuku and reconnect. Check shell-started and root-started Shizuku independently.
6. In the debloater, inspect each UAD rating/category and a labeled entry; verify description/dependencies, search, and filter behavior. No packages should be preselected for removal.
