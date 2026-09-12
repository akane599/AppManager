// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.debloat;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.List;

public class UadListParserTest {
    @Test
    public void preservesPackageKeyDescriptionRelationshipsAndLabels() {
        List<DebloatObject> list = UadListParser.parse("{\"com.example.app\":{"
                + "\"list\":\"Oem\",\"removal\":\"Advanced\",\"description\":\"Read [this] first.\\nSecond line.\","
                + "\"dependencies\":[\"com.example.dependency\"],\"neededBy\":[\"com.example.dependent\"],"
                + "\"labels\":[\"mim\"]}}");
        DebloatObject app = list.get(0);
        assertEquals("com.example.app", app.packageName);
        assertEquals("oem", app.type);
        assertEquals("OEM", app.getListLabel());
        assertEquals("Read [this] first.\nSecond line.", app.getDescription());
        assertArrayEquals(new String[]{"com.example.dependency"}, app.getDependencies());
        assertArrayEquals(new String[]{"com.example.dependent"}, app.getRequiredBy());
        assertArrayEquals(new String[]{"mim"}, app.getTags());
        assertEquals(DebloatObject.REMOVAL_REPLACE, app.getRemoval());
    }

    @Test
    public void preservesAllFourRiskLevelsAndFailsClosedForUnknownRatings() {
        String[] ratings = {"Recommended", "Advanced", "Expert", "Unsafe", "Unknown"};
        int[] expected = {DebloatObject.REMOVAL_SAFE, DebloatObject.REMOVAL_REPLACE,
                DebloatObject.REMOVAL_CAUTION, DebloatObject.REMOVAL_UNSAFE, DebloatObject.REMOVAL_UNSAFE};
        for (int i = 0; i < ratings.length; i++) {
            DebloatObject app = UadListParser.parse("{\"pkg\":{\"removal\":\"" + ratings[i] + "\"}}").get(0);
            assertEquals(expected[i], app.getRemoval());
        }
        assertEquals(DebloatObject.REMOVAL_UNSAFE, UadListParser.parse("{\"pkg\":{}}").get(0).getRemoval());
    }

    @Test
    public void assignsStableDistinctIdsAndHandlesMissingOptionalMetadata() {
        List<DebloatObject> list = UadListParser.parse("{\"first\":{},\"second\":{},\"absent\":null}");
        assertEquals(2, list.size());
        assertEquals(0, list.get(0).getId());
        assertEquals(1, list.get(1).getId());
        assertEquals("misc", list.get(0).type);
        assertEquals("", list.get(0).getDescription());
        assertEquals(0, list.get(0).getDependencies().length);
    }
}
