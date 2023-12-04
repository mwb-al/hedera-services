/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.merkle;

import com.hedera.node.app.service.mono.state.migration.TokenStateTranslator;
import com.hedera.test.serde.EqualityType;
import com.hedera.test.serde.SelfSerializableDataTests;
import com.hedera.test.utils.SeededPropertySource;
import edu.umd.cs.findbugs.annotations.NonNull;

public class MerkleTokenSerdeTests extends SelfSerializableDataTests<MerkleToken> {
    public static final int NUM_TEST_CASES = 2 * MIN_TEST_CASES_PER_VERSION;

    @Override
    protected Class<MerkleToken> getType() {
        return MerkleToken.class;
    }

    @Override
    protected int getNumTestCasesFor(int version) {
        return NUM_TEST_CASES;
    }

    @Override
    protected MerkleToken getExpectedObject(
            final SeededPropertySource propertySource, @NonNull final EqualityType equalityType) {
        final var strictExpectation = getExpectedObject(propertySource);
        final var pbjToken = TokenStateTranslator.tokenFromMerkle(strictExpectation);
        return TokenStateTranslator.merkleTokenFromToken(pbjToken);
    }

    @Override
    protected MerkleToken getExpectedObject(@NonNull final SeededPropertySource propertySource) {
        return propertySource.nextToken();
    }
}
