// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.app.Dialog;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.ipc.ShizukuBackend;
import rikka.shizuku.Shizuku;

/** Retains the permission flow across rotation using Fragment results. */
public class ShizukuPermissionDialog extends DialogFragment {
    public static final String RESULT = "shizuku_permission";
    public static final String GRANTED = "granted";
    private static final int REQUEST_CODE = 7319;
    private boolean mRequested;
    private boolean mFinished;
    private final Shizuku.OnRequestPermissionResultListener mListener = (code, result) -> {
        if (code == REQUEST_CODE) finish(result == PackageManager.PERMISSION_GRANTED);
    };

    public static void show(@NonNull FragmentManager manager) {
        if (manager.findFragmentByTag(RESULT) == null) {
            new ShizukuPermissionDialog().show(manager, RESULT);
        }
    }

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setCancelable(false);
        mRequested = state != null && state.getBoolean("requested");
        Shizuku.addRequestPermissionResultListener(mListener);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("requested", mRequested);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle state) {
        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.shizuku_mode)
                .setMessage(R.string.shizuku_permission_message)
                .setPositiveButton(R.string.action_continue, null)
                .setNegativeButton(R.string.cancel, (dialog, which) -> finish(false))
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog) requireDialog();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                if (!ShizukuBackend.isAvailable()) {
                    finish(false);
                } else if (ShizukuBackend.hasPermission()) {
                    finish(true);
                } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                    dialog.setMessage(getString(R.string.shizuku_permission_denied));
                } else {
                    mRequested = true;
                    Shizuku.requestPermission(REQUEST_CODE);
                }
            } catch (RuntimeException e) {
                finish(false);
            }
        });
        // Permission may have been granted while this Activity was being recreated.
        if (mRequested && ShizukuBackend.hasPermission()) finish(true);
    }

    private void finish(boolean granted) {
        if (mFinished || !isAdded()) return;
        mFinished = true;
        Bundle result = new Bundle();
        result.putBoolean(GRANTED, granted);
        getParentFragmentManager().setFragmentResult(RESULT, result);
        dismissAllowingStateLoss();
    }

    @Override
    public void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(mListener);
        super.onDestroy();
    }
}
