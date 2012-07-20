/**
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.informantproject.test;

import static org.fest.assertions.api.Assertions.assertThat;

import java.util.Random;

import org.informantproject.test.api.LevelOne;
import org.informantproject.testkit.AppUnderTest;
import org.informantproject.testkit.Config.PluginConfig;
import org.informantproject.testkit.InformantContainer;
import org.informantproject.testkit.Trace;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public class PluginPropertyTest {

    private static final String PLUGIN_ID = "org.informantproject:informant-integration-tests";

    private static final Random random = new Random();

    private static InformantContainer container;

    @BeforeClass
    public static void setUp() throws Exception {
        container = InformantContainer.create();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        container.closeAndDeleteFiles();
    }

    @Test
    public void shouldUpdateAndReadBackPluginConfig() throws Exception {
        // given
        String randomText = "Level " + random.nextLong();
        boolean randomBoolean = random.nextBoolean();
        PluginConfig randomPluginConfig = container.getInformant().getPluginConfig(PLUGIN_ID);
        randomPluginConfig.setProperty("alternateDescription", randomText);
        randomPluginConfig.setProperty("starredDescription", randomBoolean);
        container.getInformant().storePluginProperties(PLUGIN_ID,
                randomPluginConfig.getPropertiesJson());
        // when
        PluginConfig pluginConfig = container.getInformant().getPluginConfig(PLUGIN_ID);
        // then
        assertThat((String) pluginConfig.getProperty("alternateDescription")).isEqualTo(randomText);
        assertThat((Boolean) pluginConfig.getProperty("starredDescription")).isEqualTo(
                randomBoolean);
    }

    @Test
    public void shouldReadDefaultPropertyValue() throws Exception {
        // when
        PluginConfig pluginConfig = container.getInformant().getPluginConfig(PLUGIN_ID);
        // then
        assertThat((String) pluginConfig.getProperty("hasDefaultVal")).isEqualTo("one");
    }

    @Test
    public void shouldClearPluginProperty() throws Exception {
        // given
        PluginConfig pluginConfig = container.getInformant().getPluginConfig(PLUGIN_ID);
        pluginConfig.setProperty("alternateDescription", "a non-null value");
        container.getInformant().storePluginProperties(PLUGIN_ID, pluginConfig.getPropertiesJson());
        // when
        pluginConfig = container.getInformant().getPluginConfig(PLUGIN_ID);
        pluginConfig.setProperty("alternateDescription", null);
        container.getInformant().storePluginProperties(PLUGIN_ID, pluginConfig.getPropertiesJson());
        // then
        pluginConfig = container.getInformant().getPluginConfig(PLUGIN_ID);
        assertThat(pluginConfig.getProperty("alternateDescription")).isNull();
    }

    @Test
    public void shouldReadAlternateDescription() throws Exception {
        // given
        container.getInformant().setThresholdMillis(0);
        PluginConfig pluginConfig = container.getInformant().getPluginConfig(
                "org.informantproject:informant-integration-tests");
        pluginConfig.setProperty("alternateDescription", "Level 1");
        pluginConfig.setProperty("starredDescription", false);
        container.getInformant().storePluginProperties("org.informantproject"
                + ":informant-integration-tests", pluginConfig.getPropertiesJson());
        // when
        container.executeAppUnderTest(SimpleApp.class);
        // then
        Trace trace = container.getInformant().getLastTrace();
        assertThat(trace.getDescription()).isEqualTo("Level 1");
    }

    @Test
    public void shouldReadStarredDescription() throws Exception {
        // given
        container.getInformant().setThresholdMillis(0);
        PluginConfig pluginConfig = container.getInformant().getPluginConfig(
                "org.informantproject:informant-integration-tests");
        pluginConfig.setProperty("alternateDescription", null);
        pluginConfig.setProperty("starredDescription", true);
        container.getInformant().storePluginProperties("org.informantproject"
                + ":informant-integration-tests", pluginConfig.getPropertiesJson());
        // when
        container.executeAppUnderTest(SimpleApp.class);
        // then
        Trace trace = container.getInformant().getLastTrace();
        assertThat(trace.getDescription()).isEqualTo("Level One*");
    }

    public static class SimpleApp implements AppUnderTest {
        public void executeApp() throws InterruptedException {
            new LevelOne().call("a", "b");
        }
    }
}
