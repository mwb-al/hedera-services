/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform;

import com.swirlds.common.internal.ConfigurationException;
import com.swirlds.platform.config.legacy.LegacyConfigProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * FUTURE WORK: More tests can currently not be written since the ApplicationDefinitionLoader internally tries to load jar
 * files from the classpath.
 */
class ApplicationDefinitionLoaderTests {

    @Test
    void testNullParam() {
        Assertions.assertThrows(NullPointerException.class, () -> ApplicationDefinitionLoader.load(null));
    }

    @Test
    void testEmptyParams() {
        // given
        final LegacyConfigProperties configProperties = new LegacyConfigProperties();

        // then
        Assertions.assertThrows(
                ConfigurationException.class,
                () -> ApplicationDefinitionLoader.load(configProperties),
                "Configuration properties must contain application definition");
    }
}
