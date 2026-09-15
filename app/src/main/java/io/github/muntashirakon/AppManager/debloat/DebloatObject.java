// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.utils.AppDb;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;

public class DebloatObject {
    @IntDef({REMOVAL_SAFE, REMOVAL_REPLACE, REMOVAL_CAUTION, REMOVAL_UNSAFE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Removal {
    }

    public static final int REMOVAL_SAFE = 1;
    public static final int REMOVAL_REPLACE = 1 << 1;
    public static final int REMOVAL_CAUTION = 1 << 2;
    public static final int REMOVAL_UNSAFE = 1 << 3;

    @SerializedName("id")
    public String packageName;
    @SerializedName("label")
    @Nullable
    private String mInternalLabel;
    @SerializedName(value = "tags", alternate = {"labels"})
    @Nullable
    private String[] mTags;
    @SerializedName("dependencies")
    @Nullable
    private String[] mDependencies;
    @SerializedName(value = "required_by", alternate = {"neededBy"})
    @Nullable
    private String[] mRequiredBy;
    // Possible values: aosp, carrier, google, misc, oem, pending
    @SerializedName(value = "type", alternate = {"list"})
    public String type;
    @SerializedName("description")
    private String mDescription;
    @SerializedName("web")
    @Nullable
    private String[] mWebRefs;
    @SerializedName("removal")
    private String mRemoval;
    @SerializedName("warning")
    @Nullable
    private String mWarning;
    @SerializedName("suggestions")
    @Nullable
    private String mSuggestionId;

    // Runtime display/install state is not part of the dataset. In particular, Gson must
    // not build reflective adapters for Drawable and its private framework internals.
    private transient int mId;

    @Nullable
    private transient Drawable mIcon;
    @Nullable
    private transient CharSequence mLabel;
    @Nullable
    private transient int[] mUsers;
    private transient boolean mInstalled;
    @Nullable
    private transient Boolean mSystemApp = null;
    @Nullable
    private transient Boolean mFrozen = null;
    @Nullable
    private transient List<SuggestionObject> mSuggestions;

    public void setId(int id) {
        mId = id;
    }

    public int getId() {
        return mId;
    }

    @NonNull
    public String[] getDependencies() {
        return ArrayUtils.defeatNullable(mDependencies);
    }

    @NonNull
    public String[] getRequiredBy() {
        return ArrayUtils.defeatNullable(mRequiredBy);
    }

    @Removal
    public int getRemoval() {
        // Keep persisted filter bits compatible, but use UAD's original risk levels.
        if (mRemoval == null) return REMOVAL_UNSAFE;
        switch (mRemoval.toLowerCase(java.util.Locale.ROOT)) {
            case "recommended":
            case "safe":
                return REMOVAL_SAFE;
            case "advanced":
            case "replace":
                return REMOVAL_REPLACE;
            case "expert":
            case "caution":
                return REMOVAL_CAUTION;
            default:
                // Unknown classifications must never be presented as safe.
                return REMOVAL_UNSAFE;
        }
    }

    @androidx.annotation.StringRes
    public int getRemovalLabel() {
        switch (getRemoval()) {
            case REMOVAL_SAFE: return io.github.muntashirakon.AppManager.R.string.uad_recommended;
            case REMOVAL_REPLACE: return io.github.muntashirakon.AppManager.R.string.uad_advanced;
            case REMOVAL_CAUTION: return io.github.muntashirakon.AppManager.R.string.uad_expert;
            default: return io.github.muntashirakon.AppManager.R.string.uad_unsafe;
        }
    }

    @NonNull
    public String[] getTags() {
        return ArrayUtils.defeatNullable(mTags);
    }

    @NonNull
    public String getListLabel() {
        if ("aosp".equals(type) || "oem".equals(type)) return type.toUpperCase(java.util.Locale.ROOT);
        if (type == null || type.isEmpty()) return "Misc";
        return Character.toUpperCase(type.charAt(0)) + type.substring(1);
    }

    @Nullable
    public String getWarning() {
        return mWarning;
    }

    public String getDescription() {
        return mDescription != null ? mDescription : "";
    }

    @NonNull
    public String[] getWebRefs() {
        return ArrayUtils.defeatNullable(mWebRefs);
    }

    @Nullable
    public String getSuggestionId() {
        return mSuggestionId;
    }

    @Nullable
    public List<SuggestionObject> getSuggestions() {
        return mSuggestions;
    }

    public void setSuggestions(@Nullable List<SuggestionObject> suggestions) {
        mSuggestions = suggestions;
    }

    @Nullable
    public CharSequence getLabel() {
        return mLabel != null ? mLabel : mInternalLabel;
    }
    @NonNull
    public CharSequence getLabelOrPackageName() {
        CharSequence label = mLabel != null ? mLabel : mInternalLabel;
        return label != null ? label : packageName;
    }

    @Nullable
    public Drawable getIcon() {
        return mIcon;
    }

    @Nullable
    public int[] getUsers() {
        return mUsers;
    }

    private void addUser(int userId) {
        if (mUsers == null) {
            mUsers = new int[]{userId};
        } else {
            mUsers = ArrayUtils.appendInt(mUsers, userId);
        }
    }

    public boolean isInstalled() {
        return mInstalled;
    }

    public boolean isSystemApp() {
        return Boolean.TRUE.equals(mSystemApp);
    }

    public boolean isUserApp() {
        return Boolean.FALSE.equals(mSystemApp);
    }

    public boolean isFrozen() {
        return Boolean.TRUE.equals(mFrozen);
    }

    public void fillInstallInfo(@NonNull Context context, @NonNull AppDb appDb) {
        PackageManager pm = context.getPackageManager();
        List<SuggestionObject> suggestionObjects = getSuggestions();
        if (suggestionObjects != null) {
            for (SuggestionObject suggestionObject : suggestionObjects) {
                List<App> apps = appDb.getAllApplications(suggestionObject.packageName);
                for (App app : apps) {
                    if (app.isInstalled) {
                        suggestionObject.addUser(app.userId);
                    }
                }
            }
        }
        // Update application data
        mInstalled = false;
        mUsers = null;
        mSystemApp = null;
        mFrozen = null;
        List<App> apps = appDb.getAllApplications(packageName);
        for (App app : apps) {
            mInstalled = app.isInstalled;
            addUser(app.userId);
            mSystemApp = app.isSystemApp();
            mFrozen = !app.isEnabled;
            mLabel = app.packageLabel;
            if (getIcon() == null) {
                try {
                    ApplicationInfo ai = PackageManagerCompat.getApplicationInfo(packageName,
                            MATCH_UNINSTALLED_PACKAGES | MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, app.userId);
                    mInstalled = (ai.flags & ApplicationInfo.FLAG_INSTALLED) != 0;
                    mSystemApp = ApplicationInfoCompat.isSystemApp(ai);
                    mLabel = ai.loadLabel(pm);
                    mIcon = ai.loadIcon(pm);
                    mFrozen = FreezeUtils.isFrozen(ai);
                } catch (RemoteException | PackageManager.NameNotFoundException ignore) {
                }
            }
        }
    }
}
