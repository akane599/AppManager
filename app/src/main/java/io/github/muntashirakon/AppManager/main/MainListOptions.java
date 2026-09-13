// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.debloat.DebloatObject;
import io.github.muntashirakon.AppManager.filters.FilterItem;
import io.github.muntashirakon.AppManager.filters.options.AppTypeOption;
import io.github.muntashirakon.AppManager.filters.options.BackupOption;
import io.github.muntashirakon.AppManager.filters.options.BloatwareOption;
import io.github.muntashirakon.AppManager.filters.options.ComponentsOption;
import io.github.muntashirakon.AppManager.filters.options.FreezeOption;
import io.github.muntashirakon.AppManager.filters.options.InstalledOption;
import io.github.muntashirakon.AppManager.filters.options.RunningAppsOption;
import io.github.muntashirakon.AppManager.filters.options.TrackersOption;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.misc.ListOptions;
import io.github.muntashirakon.AppManager.profiles.ProfileManager;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.users.UserInfo;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.adapters.SelectedArrayAdapter;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;

public class MainListOptions extends ListOptions {
    public static final String TAG = MainListOptions.class.getSimpleName();

    @IntDef(value = {
            SORT_BY_DOMAIN,
            SORT_BY_APP_LABEL,
            SORT_BY_PACKAGE_NAME,
            SORT_BY_LAST_UPDATE,
            SORT_BY_SHARED_ID,
            SORT_BY_TARGET_SDK,
            SORT_BY_SHA,
            SORT_BY_FROZEN_APP,
            SORT_BY_BLOCKED_COMPONENTS,
            SORT_BY_BACKUP,
            SORT_BY_TRACKERS,
            SORT_BY_LAST_ACTION,
            SORT_BY_INSTALLATION_DATE,
            SORT_BY_TOTAL_SIZE,
            SORT_BY_DATA_USAGE,
            SORT_BY_OPEN_COUNT,
            SORT_BY_SCREEN_TIME,
            SORT_BY_LAST_USAGE_TIME,
            SORT_BY_DEBLOAT_RATING,
            SORT_BY_BACKUP_TIME,
            SORT_BY_VERSION_CODE,
            SORT_BY_APP_SIZE,
            SORT_BY_APP_DATA_SIZE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SortOrder {
    }

    public static final int SORT_BY_DOMAIN = 0;  // User/system app
    public static final int SORT_BY_APP_LABEL = 1;
    public static final int SORT_BY_PACKAGE_NAME = 2;
    public static final int SORT_BY_LAST_UPDATE = 3;
    public static final int SORT_BY_SHARED_ID = 4;
    public static final int SORT_BY_TARGET_SDK = 5;
    public static final int SORT_BY_SHA = 6;  // Signature
    public static final int SORT_BY_FROZEN_APP = 7;
    public static final int SORT_BY_BLOCKED_COMPONENTS = 8;
    public static final int SORT_BY_BACKUP = 9;
    public static final int SORT_BY_TRACKERS = 10;
    public static final int SORT_BY_LAST_ACTION = 11;
    public static final int SORT_BY_INSTALLATION_DATE = 12;
    public static final int SORT_BY_TOTAL_SIZE = 13;
    public static final int SORT_BY_DATA_USAGE = 14;
    public static final int SORT_BY_OPEN_COUNT = 15;
    public static final int SORT_BY_SCREEN_TIME = 16;
    public static final int SORT_BY_LAST_USAGE_TIME = 17;
    // These IDs are persisted. Append new options without renumbering existing ones.
    public static final int SORT_BY_DEBLOAT_RATING = 18;
    public static final int SORT_BY_BACKUP_TIME = 19;
    public static final int SORT_BY_VERSION_CODE = 20;
    public static final int SORT_BY_APP_SIZE = 21;
    public static final int SORT_BY_APP_DATA_SIZE = 22;

    @IntDef(flag = true, value = {
            FILTER_NO_FILTER,
            FILTER_USER_APPS,
            FILTER_SYSTEM_APPS,
            FILTER_FROZEN_APPS,
            FILTER_UNFROZEN_APPS,
            FILTER_APPS_WITH_RULES,
            FILTER_APPS_WITH_ACTIVITIES,
            FILTER_APPS_WITH_BACKUPS,
            FILTER_RUNNING_APPS,
            FILTER_APPS_WITH_SPLITS,
            FILTER_INSTALLED_APPS,
            FILTER_UNINSTALLED_APPS,
            FILTER_APPS_WITHOUT_BACKUPS,
            FILTER_APPS_WITH_KEYSTORE,
            FILTER_APPS_WITH_SAF,
            FILTER_APPS_WITH_SSAID,
            FILTER_STOPPED_APPS,
            FILTER_DEBLOATABLE_APPS,
            FILTER_UAD_RECOMMENDED,
            FILTER_UAD_ADVANCED,
            FILTER_UAD_EXPERT,
            FILTER_UAD_UNSAFE,
            FILTER_APPS_WITH_TRACKERS,
            FILTER_APPS_WITHOUT_TRACKERS,
            FILTER_APPS_WITHOUT_ACTIVITIES,
            FILTER_UPDATED_SYSTEM_APPS,
            FILTER_DEBUGGABLE_APPS,
            FILTER_PERSISTENT_APPS,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Filter {
    }

    public static final int FILTER_NO_FILTER = 0;
    public static final int FILTER_USER_APPS = 1;
    public static final int FILTER_SYSTEM_APPS = 1 << 1;
    public static final int FILTER_FROZEN_APPS = 1 << 2;
    public static final int FILTER_APPS_WITH_RULES = 1 << 3;
    public static final int FILTER_APPS_WITH_ACTIVITIES = 1 << 4;
    public static final int FILTER_APPS_WITH_BACKUPS = 1 << 5;
    public static final int FILTER_RUNNING_APPS = 1 << 6;
    public static final int FILTER_APPS_WITH_SPLITS = 1 << 7;
    public static final int FILTER_INSTALLED_APPS = 1 << 8;
    public static final int FILTER_UNINSTALLED_APPS = 1 << 9;
    public static final int FILTER_APPS_WITHOUT_BACKUPS = 1 << 10;
    public static final int FILTER_APPS_WITH_KEYSTORE = 1 << 11;
    public static final int FILTER_APPS_WITH_SAF = 1 << 12;
    public static final int FILTER_APPS_WITH_SSAID = 1 << 13;
    public static final int FILTER_STOPPED_APPS = 1 << 14;
    public static final int FILTER_UNFROZEN_APPS = 1 << 15;
    public static final int FILTER_DEBLOATABLE_APPS = 1 << 16;
    public static final int FILTER_UAD_RECOMMENDED = 1 << 17;
    public static final int FILTER_UAD_ADVANCED = 1 << 18;
    public static final int FILTER_UAD_EXPERT = 1 << 19;
    public static final int FILTER_UAD_UNSAFE = 1 << 20;
    public static final int FILTER_APPS_WITH_TRACKERS = 1 << 21;
    public static final int FILTER_APPS_WITHOUT_TRACKERS = 1 << 22;
    public static final int FILTER_APPS_WITHOUT_ACTIVITIES = 1 << 23;
    public static final int FILTER_UPDATED_SYSTEM_APPS = 1 << 24;
    public static final int FILTER_DEBUGGABLE_APPS = 1 << 25;
    public static final int FILTER_PERSISTENT_APPS = 1 << 26;

    // For now, just generate FilterItem
    @NonNull
    public static FilterItem getFilterItemFromFlags(int flags) {
        FilterItem filterItem = new FilterItem();
        // One option unions the selected ratings; FilterItem ANDs it with the other filters.
        int removalFlags = 0;
        if ((flags & FILTER_UAD_RECOMMENDED) != 0) removalFlags |= DebloatObject.REMOVAL_SAFE;
        if ((flags & FILTER_UAD_ADVANCED) != 0) removalFlags |= DebloatObject.REMOVAL_REPLACE;
        if ((flags & FILTER_UAD_EXPERT) != 0) removalFlags |= DebloatObject.REMOVAL_CAUTION;
        if ((flags & FILTER_UAD_UNSAFE) != 0) removalFlags |= DebloatObject.REMOVAL_UNSAFE;
        if (removalFlags != 0 || (flags & FILTER_DEBLOATABLE_APPS) != 0) {
            BloatwareOption option = new BloatwareOption();
            if (removalFlags != 0) {
                option.setKeyValue("removal", String.valueOf(removalFlags));
            }
            filterItem.addFilterOption(option);
        }
        if ((flags & FILTER_APPS_WITH_TRACKERS) != 0) {
            TrackersOption option = new TrackersOption();
            option.setKeyValue("ge", "1");
            filterItem.addFilterOption(option);
        }
        if ((flags & FILTER_APPS_WITHOUT_TRACKERS) != 0) {
            TrackersOption option = new TrackersOption();
            option.setKeyValue("eq", "0");
            filterItem.addFilterOption(option);
        }
        // Flags
        int appTypeWithFlags = 0;
        if ((flags & FILTER_USER_APPS) != 0) {
            appTypeWithFlags |= AppTypeOption.APP_TYPE_USER;
        }
        if ((flags & FILTER_SYSTEM_APPS) != 0) {
            appTypeWithFlags |= AppTypeOption.APP_TYPE_SYSTEM;
        }
        if ((flags & FILTER_UPDATED_SYSTEM_APPS) != 0) {
            appTypeWithFlags |= AppTypeOption.APP_TYPE_UPDATED_SYSTEM;
        }
        if ((flags & FILTER_DEBUGGABLE_APPS) != 0) {
            appTypeWithFlags |= AppTypeOption.APP_TYPE_DEBUGGABLE;
        }
        if ((flags & FILTER_PERSISTENT_APPS) != 0) {
            appTypeWithFlags |= AppTypeOption.APP_TYPE_PERSISTENT;
        }
        if ((flags & FILTER_FROZEN_APPS) != 0) {
            FreezeOption option = new FreezeOption();
            option.setKeyValue("frozen", null);
            filterItem.addFilterOption(option);
        }
        if ((flags & FILTER_UNFROZEN_APPS) != 0) {
            FreezeOption option = new FreezeOption();
            option.setKeyValue("unfrozen", null);
            filterItem.addFilterOption(option);
        }
        if ((flags & FILTER_APPS_WITH_RULES) != 0) {
            appTypeWithFlags |= AppTypeOption.APP_TYPE_WITH_RULES;
        }
        if ((flags & FILTER_APPS_WITH_ACTIVITIES) != 0) {
            ComponentsOption option = new ComponentsOption();
            option.setKeyValue("with_type", String.valueOf(ComponentsOption.COMPONENT_TYPE_ACTIVITY));
            filterItem.addFilterOption(option);
        }
        if ((flags & FILTER_APPS_WITHOUT_ACTIVITIES) != 0) {
            ComponentsOption option = new ComponentsOption();
            option.setKeyValue("without_type", String.valueOf(ComponentsOption.COMPONENT_TYPE_ACTIVITY));
            filterItem.addFilterOption(option);
        }
        if ((flags & FILTER_APPS_WITH_BACKUPS) != 0) {
            BackupOption option = new BackupOption();
            option.setKeyValue("backups", null);
            filterItem.addFilterOption(option);
        }
        if ((flags & FILTER_RUNNING_APPS) != 0) {
            RunningAppsOption option = new RunningAppsOption();
            option.setKeyValue("running", null);
            filterItem.addFilterOption(option);
        }
        if ((flags & FILTER_APPS_WITH_SPLITS) != 0) {
            // TODO: 7/28/25
        }
        if ((flags & FILTER_INSTALLED_APPS) != 0) {
            InstalledOption option = new InstalledOption();
            option.setKeyValue("installed", null);
            filterItem.addFilterOption(option);
        }
        if ((flags & FILTER_UNINSTALLED_APPS) != 0) {
            InstalledOption option = new InstalledOption();
            option.setKeyValue("uninstalled", null);
            filterItem.addFilterOption(option);
        }
        if ((flags & FILTER_APPS_WITHOUT_BACKUPS) != 0) {
            BackupOption option = new BackupOption();
            option.setKeyValue("no_backups", null);
            filterItem.addFilterOption(option);
        }
        if ((flags & FILTER_APPS_WITH_KEYSTORE) != 0) {
            appTypeWithFlags |= AppTypeOption.APP_TYPE_KEYSTORE;
        }
        if ((flags & FILTER_APPS_WITH_SAF) != 0) {
            // TODO: 7/28/25
        }
        if ((flags & FILTER_APPS_WITH_SSAID) != 0) {
            appTypeWithFlags |= AppTypeOption.APP_TYPE_SSAID;
        }
        if ((flags & FILTER_STOPPED_APPS) != 0) {
            appTypeWithFlags |= AppTypeOption.APP_TYPE_STOPPED;
        }
        if (appTypeWithFlags > 0) {
            AppTypeOption appTypeWithFlagsOption = new AppTypeOption();
            appTypeWithFlagsOption.setKeyValue("with_flags", String.valueOf(appTypeWithFlags));
            filterItem.addFilterOption(appTypeWithFlagsOption);
        }
        return filterItem;
    }

    private final List<String> mProfileNames = new ArrayList<>();
    private Future<?> mProfileSuggestionsResult;
    @Nullable
    private SelectedArrayAdapter<String> mAdapter;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity activity = (MainActivity) requireActivity();
        LocalServices.state().observe(getViewLifecycleOwner(), ignored -> refreshOptions());
        profileNameSpinner.setOnItemClickListener((parent, view1, position, id) -> {
            if (mAdapter == null || activity.viewModel == null) {
                return;
            }
            if (position == 0) {
                // No profiles
                activity.viewModel.setFilterProfileName(null);
            } else {
                activity.viewModel.setFilterProfileName(mAdapter.getItem(position));
            }
        });
        mProfileSuggestionsResult = ThreadUtils.postOnBackgroundThread(() -> {
            mProfileNames.clear();
            mProfileNames.add(getString(R.string.no_profiles));
            mProfileNames.addAll(ProfileManager.getProfileNames());
            mAdapter = new SelectedArrayAdapter<>(activity,
                    io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item_small,
                    mProfileNames);
            if (isDetached() || ThreadUtils.isInterrupted()) return;
            activity.runOnUiThread(() -> {
                profileNameSpinner.setAdapter(mAdapter);
                if (activity.viewModel != null) {
                    String selectedProfile = activity.viewModel.getFilterProfileName();
                    if (TextUtils.isEmpty(selectedProfile)) {
                        profileNameSpinner.setSelection(0);
                    } else {
                        int i = mProfileNames.indexOf(selectedProfile);
                        if (i < 0) {
                           i = 0;
                        }
                        profileNameSpinner.setSelection(i);
                    }
                }
            });
        });
        selectUserView.setVisibility(Users.getUsersIds().length <= 1 ? View.GONE : View.VISIBLE);
        selectUserView.setOnClickListener(v -> {
            List<UserInfo> userInfoList = Users.getUsers();
            List<Integer> userIdList = new ArrayList<>(userInfoList.size());
            CharSequence[] userInfoReadable = new CharSequence[userInfoList.size()];
            int i = 0;
            for (UserInfo userInfo : userInfoList) {
                userInfoReadable[i] = userInfo.toLocalizedString(requireContext());
                userIdList.add(userInfo.id);
                ++i;
            }
            List<Integer> selections;
            if (activity.viewModel != null) {
                int[] selectedUsers = activity.viewModel.getSelectedUsers();
                if (selectedUsers != null) {
                    selections = new ArrayList<>();
                    for (int userId : selectedUsers) {
                        selections.add(userId);
                    }
                } else selections = userIdList;
            } else selections = userIdList;
            new SearchableMultiChoiceDialogBuilder<>(requireContext(), userIdList, userInfoReadable)
                    .setTitle(R.string.filter)
                    .setNegativeButton(R.string.close, null)
                    .addSelections(selections)
                    .showSelectAll(true)
                    .hideSearchBar(true)
                    .setPositiveButton(R.string.filter, (dialog, which, selectedItems) -> {
                        if (activity.viewModel != null) {
                            if (selectedItems.size() == userInfoList.size()) {
                                // All users
                                activity.viewModel.setSelectedUsers(null);
                            } else {
                                activity.viewModel.setSelectedUsers(ArrayUtils.convertToIntArray(selectedItems));
                            }
                        }
                    })
                    .show();
        });
    }

    private void refreshOptions() {
        if (getView() != null) {
            reloadUi();
        }
    }

    @Override
    public void onDestroy() {
        if (mProfileSuggestionsResult != null) {
            mProfileSuggestionsResult.cancel(true);
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public LinkedHashMap<Integer, Integer> getSortIdLocaleMap() {
        return new LinkedHashMap<Integer, Integer>() {{
            put(SORT_BY_DOMAIN, R.string.sort_by_domain);
            put(SORT_BY_APP_LABEL, R.string.sort_by_app_label);
            put(SORT_BY_PACKAGE_NAME, R.string.sort_by_package_name);
            put(SORT_BY_LAST_UPDATE, R.string.sort_by_last_update);
            put(SORT_BY_SHARED_ID, R.string.sort_by_shared_user_id);
            put(SORT_BY_TARGET_SDK, R.string.sort_by_target_sdk);
            put(SORT_BY_SHA, R.string.sort_by_sha);
            put(SORT_BY_FROZEN_APP, R.string.sort_by_frozen_app);
            put(SORT_BY_BLOCKED_COMPONENTS, R.string.sort_by_blocked_components);
            put(SORT_BY_BACKUP, R.string.sort_by_backup);
            put(SORT_BY_TRACKERS, R.string.trackers);
            put(SORT_BY_LAST_ACTION, R.string.last_actions);
            put(SORT_BY_INSTALLATION_DATE, R.string.sort_by_installation_date);
            put(SORT_BY_DEBLOAT_RATING, R.string.sort_by_debloat_rating);
            put(SORT_BY_BACKUP_TIME, R.string.sort_by_backup_time);
            put(SORT_BY_VERSION_CODE, R.string.sort_by_version_code);
            if (FeatureController.isUsageAccessEnabled()) {
                put(SORT_BY_TOTAL_SIZE, R.string.sort_by_total_size);
                put(SORT_BY_APP_SIZE, R.string.sort_by_app_size);
                put(SORT_BY_APP_DATA_SIZE, R.string.sort_by_app_data_size);
                put(SORT_BY_DATA_USAGE, R.string.sort_by_data_usage);
                put(SORT_BY_OPEN_COUNT, R.string.sort_by_times_opened);
                put(SORT_BY_SCREEN_TIME, R.string.sort_by_screen_time);
                put(SORT_BY_LAST_USAGE_TIME, R.string.sort_by_last_used);
            }
        }};
    }

    @Nullable
    @Override
    public LinkedHashMap<Integer, Integer> getFilterFlagLocaleMap() {
        return new LinkedHashMap<Integer, Integer>() {{
            put(FILTER_USER_APPS, R.string.filter_user_apps);
            put(FILTER_SYSTEM_APPS, R.string.filter_system_apps);
            put(FILTER_FROZEN_APPS, R.string.filter_frozen_apps);
            put(FILTER_UNFROZEN_APPS, R.string.filter_unfrozen_apps);
            put(FILTER_STOPPED_APPS, R.string.filter_force_stopped_apps);
            put(FILTER_INSTALLED_APPS, R.string.installed_apps);
            put(FILTER_UNINSTALLED_APPS, R.string.uninstalled_apps);
            put(FILTER_APPS_WITH_RULES, R.string.filter_apps_with_rules);
            put(FILTER_APPS_WITH_ACTIVITIES, R.string.filter_apps_with_activities);
            put(FILTER_APPS_WITH_BACKUPS, R.string.filter_apps_with_backups);
            put(FILTER_APPS_WITHOUT_BACKUPS, R.string.filter_apps_without_backups);
            put(FILTER_RUNNING_APPS, R.string.filter_running_apps);
            put(FILTER_DEBLOATABLE_APPS, R.string.filter_debloatable_apps);
            put(FILTER_UAD_RECOMMENDED, R.string.filter_uad_recommended);
            put(FILTER_UAD_ADVANCED, R.string.filter_uad_advanced);
            put(FILTER_UAD_EXPERT, R.string.filter_uad_expert);
            put(FILTER_UAD_UNSAFE, R.string.filter_uad_unsafe);
            put(FILTER_APPS_WITH_TRACKERS, R.string.filter_apps_with_trackers);
            put(FILTER_APPS_WITHOUT_TRACKERS, R.string.filter_apps_without_trackers);
            put(FILTER_APPS_WITHOUT_ACTIVITIES, R.string.filter_apps_without_activities);
            put(FILTER_UPDATED_SYSTEM_APPS, R.string.filter_updated_system_apps);
            put(FILTER_DEBUGGABLE_APPS, R.string.filter_debuggable_apps);
            put(FILTER_PERSISTENT_APPS, R.string.filter_persistent_apps);
            put(FILTER_APPS_WITH_SPLITS, R.string.filter_apps_with_splits);
            if (Ops.isWorkingUidRoot()) {
                put(FILTER_APPS_WITH_KEYSTORE, R.string.filter_apps_with_keystore);
                put(FILTER_APPS_WITH_SAF, R.string.filter_apps_with_saf);
                put(FILTER_APPS_WITH_SSAID, R.string.filter_apps_with_ssaid);
            }
        }};
    }

    @Nullable
    @Override
    public LinkedHashMap<Integer, Integer> getOptionIdLocaleMap() {
        return null;
    }

    @Override
    public boolean enableProfileNameInput() {
        return true;
    }

    @Override
    public boolean enableSelectUser() {
        return true;
    }

    @Override
    public int getFilterDescription() {
        return R.string.main_filter_description;
    }
}
