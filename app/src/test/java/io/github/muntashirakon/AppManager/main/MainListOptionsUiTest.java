// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.fragment.app.FragmentActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.LinkedHashMap;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.ListOptions;
import io.github.muntashirakon.test.shadows.ShadowOpsDependencies.ShadowUsers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, shadows = ShadowUsers.class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class MainListOptionsUiTest {
    private ActivityController<HostActivity> controller;
    private TestOptions options;
    private final Actions actions = new Actions();

    public static class HostActivity extends FragmentActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            setTheme(io.github.muntashirakon.ui.R.style.AppTheme);
            super.onCreate(savedInstanceState);
            setContentView(new FrameLayout(this));
        }
    }

    // Exercise the shared sheet with the real main-list option maps, without loading the app database.
    public static class TestOptions extends ListOptions {
        private final MainListOptions definitions = new MainListOptions();

        @Override
        public LinkedHashMap<Integer, Integer> getSortIdLocaleMap() {
            return definitions.getSortIdLocaleMap();
        }

        @Override
        public LinkedHashMap<Integer, Integer> getFilterFlagLocaleMap() {
            return definitions.getFilterFlagLocaleMap();
        }

        @Override
        public LinkedHashMap<Integer, Integer> getOptionIdLocaleMap() {
            return null;
        }

        @Override
        public int getFilterDescription() {
            return definitions.getFilterDescription();
        }
    }

    @Before
    public void setUp() {
        ShadowUsers.remoteUid = android.os.Process.myUid();
        actions.flags = MainListOptions.FILTER_RUNNING_APPS;
        actions.sort = MainListOptions.SORT_BY_DEBLOAT_RATING;
        controller = Robolectric.buildActivity(HostActivity.class).setup();
        options = new TestOptions();
        options.setListOptionActions(actions);
        controller.get().getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, options).commitNow();
    }

    @After
    public void tearDown() {
        if (controller != null) controller.pause().stop().destroy();
    }

    @Test
    public void ratingChipsCanBeSelectedTogetherWithRunningAndSurviveRefresh() {
        ChipGroup filters = options.requireView().findViewById(R.id.filter_options);
        Chip recommended = filters.findViewById(MainListOptions.FILTER_UAD_RECOMMENDED);
        Chip advanced = filters.findViewById(MainListOptions.FILTER_UAD_ADVANCED);
        recommended.performClick();
        advanced.performClick();
        int expected = MainListOptions.FILTER_RUNNING_APPS | MainListOptions.FILTER_UAD_RECOMMENDED
                | MainListOptions.FILTER_UAD_ADVANCED;
        assertEquals(expected, actions.flags);
        options.reloadUi();
        assertEquals(expected, actions.flags);
        assertTrue(((Chip) filters.findViewById(MainListOptions.FILTER_RUNNING_APPS)).isChecked());
        assertTrue(((Chip) filters.findViewById(MainListOptions.FILTER_UAD_RECOMMENDED)).isChecked());
        assertTrue(((Chip) filters.findViewById(MainListOptions.FILTER_UAD_ADVANCED)).isChecked());
        assertEquals(View.VISIBLE, options.requireView().findViewById(R.id.filter_description).getVisibility());
    }

    @Test
    public void refreshingOptionsPreservesSortAndNeverSavesNoId() {
        options.reloadUi();
        ChipGroup sorts = options.requireView().findViewById(R.id.sort_options);
        assertEquals(MainListOptions.SORT_BY_DEBLOAT_RATING, actions.sort);
        assertEquals(actions.sort, sorts.getCheckedChipId());
        ((Chip) sorts.findViewById(MainListOptions.SORT_BY_BACKUP_TIME)).performClick();
        assertEquals(MainListOptions.SORT_BY_BACKUP_TIME, actions.sort);
        sorts.clearCheck();
        assertEquals(MainListOptions.SORT_BY_BACKUP_TIME, actions.sort);
        options.reloadUi();
        assertEquals(MainListOptions.SORT_BY_BACKUP_TIME, sorts.getCheckedChipId());
    }

    private static class Actions implements ListOptions.ListOptionActions {
        int flags;
        int sort;

        @Override
        public int getSortBy() {
            return sort;
        }

        @Override
        public void setSortBy(int sortBy) {
            assertTrue(sortBy != View.NO_ID);
            sort = sortBy;
        }

        @Override
        public boolean hasFilterFlag(int flag) {
            return (flags & flag) != 0;
        }

        @Override
        public void addFilterFlag(int flag) {
            flags |= flag;
        }

        @Override
        public void removeFilterFlag(int flag) {
            flags &= ~flag;
        }
    }
}
