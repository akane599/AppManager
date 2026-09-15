// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

/** Shared mode IDs and localized labels for the picker and settings summaries. */
final class OperationModeLabels {
    static final List<String> MODES = Collections.unmodifiableList(Arrays.asList(
            Ops.MODE_AUTO,
            Ops.MODE_ROOT,
            Ops.MODE_ADB_OVER_TCP,
            Ops.MODE_ADB_WIFI,
            Ops.MODE_NO_ROOT,
            Ops.MODE_SHIZUKU));

    private OperationModeLabels() {
    }

    @NonNull
    static String[] getLabels(@NonNull Context context) {
        // Keep the existing five-entry translated arrays compatible with Shizuku.
        String[] labels = Arrays.copyOf(context.getResources().getStringArray(R.array.modes), MODES.size());
        labels[MODES.indexOf(Ops.MODE_SHIZUKU)] = context.getString(R.string.shizuku_mode);
        for (int i = 0; i < labels.length; ++i) {
            if (labels[i] == null) labels[i] = context.getString(R.string.state_unknown);
        }
        return labels;
    }

    @NonNull
    static String getLabel(@NonNull Context context, @Nullable String mode) {
        int index = MODES.indexOf(mode);
        return index >= 0 ? getLabels(context)[index] : context.getString(R.string.state_unknown);
    }
}
