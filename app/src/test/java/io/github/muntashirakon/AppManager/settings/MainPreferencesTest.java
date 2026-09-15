// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Application;
import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.test.shadows.ShadowOpsDependencies.ShadowUsers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, shadows = ShadowUsers.class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
public class MainPreferencesTest {
    private static final String FRAGMENT_TAG = "main_preferences";
    private ActivityController<HostActivity> controller;

    public static class HostActivity extends FragmentActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            setTheme(io.github.muntashirakon.ui.R.style.AppTheme);
            super.onCreate(savedInstanceState);
            setContentView(new FrameLayout(this));
        }
    }

    @Before
    public void setUp() {
        Ops.setMode(Ops.MODE_NO_ROOT);
        ShadowUsers.remoteUid = android.os.Process.myUid();
    }

    @After
    public void tearDown() {
        if (controller != null) controller.pause().stop().destroy();
    }

    @Test
    public void settingsResumeAndRecreateAfterShizukuPermissionGrant() {
        launchPreferences();
        // A permission activity stops Settings. Its return starts the existing fragment again.
        controller.pause().stop();
        Ops.setMode(Ops.MODE_SHIZUKU);
        ShadowUsers.remoteUid = Ops.SHELL_UID;
        controller.restart().start().resume();
        assertShizukuSummary("ADB");

        controller.recreate();
        assertShizukuSummary("ADB");
        assertEquals(Ops.MODE_SHIZUKU, Ops.getMode());
    }

    @Test
    @Config(qualifiers = "fr")
    public void settingsOpenInShizukuRootModeWithTranslatedLabels() {
        Ops.setMode(Ops.MODE_SHIZUKU);
        ShadowUsers.remoteUid = Ops.ROOT_UID;
        launchPreferences();
        assertShizukuSummary(controller.get().getString(R.string.root));
    }

    @Test
    public void unrecognizedModeLabelHasSafeFallback() {
        Application application = RuntimeEnvironment.getApplication();
        String unknown = application.getString(R.string.state_unknown);
        assertEquals(unknown, OperationModeLabels.getLabel(application, "future-mode"));
        assertEquals(unknown, OperationModeLabels.getLabel(application, null));
    }

    private void launchPreferences() {
        controller = Robolectric.buildActivity(HostActivity.class).setup();
        controller.get().getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, MainPreferences.getInstance(null, false), FRAGMENT_TAG)
                .commitNow();
    }

    private void assertShizukuSummary(String inferredMode) {
        MainPreferences preferences = (MainPreferences) controller.get().getSupportFragmentManager()
                .findFragmentByTag(FRAGMENT_TAG);
        assertNotNull(preferences);
        Preference mode = preferences.requirePreference("mode_of_operations");
        assertEquals(controller.get().getString(R.string.mode_of_op_with_inferred_mode_of_op,
                controller.get().getString(R.string.shizuku_mode), inferredMode), mode.getSummary());
    }
}
