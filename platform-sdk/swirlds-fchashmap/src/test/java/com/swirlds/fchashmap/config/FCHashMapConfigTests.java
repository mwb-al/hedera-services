/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.fchashmap.config;

import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.source.ConfigSource;
import com.swirlds.config.extensions.sources.ThreadCountPropertyConfigSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FCHashMapConfigTests {
    @Test
    void testConfigSource() {
        // given
        final ConfigSource configSource = new ThreadCountPropertyConfigSource();
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(configSource)
                .withConfigDataTypes(FCHashMapConfig.class)
                .build();
        final FCHashMapConfig fcHashMapConfig = configuration.getConfigData(FCHashMapConfig.class);

        // when
        final int result = fcHashMapConfig.rebuildThreadCount();

        // then
        Assertions.assertEquals(Runtime.getRuntime().availableProcessors(), result);
    }
}
