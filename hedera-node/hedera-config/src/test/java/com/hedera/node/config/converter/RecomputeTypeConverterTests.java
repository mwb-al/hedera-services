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

package com.hedera.node.config.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.node.app.service.mono.ledger.accounts.staking.StakeStartupHelper.RecomputeType;
import org.junit.jupiter.api.Test;

class RecomputeTypeConverterTests {

    @Test
    void testNullParam() {
        // given
        final RecomputeTypeConverter converter = new RecomputeTypeConverter();

        // then
        assertThatThrownBy(() -> converter.convert(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testInvalidParam() {
        // given
        final RecomputeTypeConverter converter = new RecomputeTypeConverter();

        // then
        assertThatThrownBy(() -> converter.convert("null")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testValidParam() {
        // given
        final RecomputeTypeConverter converter = new RecomputeTypeConverter();

        // when
        final RecomputeType entityType = converter.convert("NODE_STAKES");

        // then
        assertThat(entityType).isEqualTo(RecomputeType.NODE_STAKES);
    }
}
