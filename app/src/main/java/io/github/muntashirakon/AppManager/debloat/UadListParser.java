// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads UAD-ng's package-keyed JSON without rewriting its descriptions or labels. */
public final class UadListParser {
    private UadListParser() {
    }

    @NonNull
    public static List<DebloatObject> parse(@NonNull String json) {
        JsonObject packages = JsonParser.parseString(json).getAsJsonObject();
        Gson gson = new Gson();
        List<DebloatObject> result = new ArrayList<>(packages.size());
        for (Map.Entry<String, JsonElement> entry : packages.entrySet()) {
            DebloatObject item = gson.fromJson(entry.getValue(), DebloatObject.class);
            if (item == null) continue;
            item.packageName = entry.getKey();
            item.type = item.type == null ? "misc" : item.type.toLowerCase(Locale.ROOT);
            item.setId(result.size());
            result.add(item);
        }
        return result;
    }
}
