// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.servermanager;

import static org.junit.Assert.*;
import org.junit.Test;
import io.github.muntashirakon.AppManager.server.common.ConfigParams;

public class ConfigParamsTest {
    @Test
    public void settingUidDoesNotReplaceClasspath() {
        ConfigParams params = new ConfigParams();
        params.put(ConfigParams.PARAM_CLASSPATH, "/data/local/tmp/am.jar");
        params.put(ConfigParams.PARAM_UID, "2000");
        assertEquals("2000", params.getUid());
        assertEquals("/data/local/tmp/am.jar", params.getClassPath());
    }

    @Test
    public void diagnosticStringDoesNotExposeAuthenticationToken() {
        ConfigParams params = new ConfigParams();
        params.put(ConfigParams.PARAM_TOKEN, "private-test-token");
        assertFalse(params.toString().contains("private-test-token"));
        assertEquals("private-test-token", params.getToken());
    }
}
