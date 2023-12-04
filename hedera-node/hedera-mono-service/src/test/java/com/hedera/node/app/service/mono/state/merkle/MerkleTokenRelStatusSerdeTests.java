/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.hedera.node.app.service.mono.state.migration.TokenRelationStateTranslator;
import com.hedera.test.serde.EqualityType;
import com.hedera.test.serde.SelfSerializableDataTests;
import com.hedera.test.utils.SeededPropertySource;
import edu.umd.cs.findbugs.annotations.NonNull;

public class MerkleTokenRelStatusSerdeTests extends SelfSerializableDataTests<MerkleTokenRelStatus> {
    @Override
    protected Class<MerkleTokenRelStatus> getType() {
        return MerkleTokenRelStatus.class;
    }

    @Override
    protected MerkleTokenRelStatus getExpectedObject(final SeededPropertySource propertySource) {
        return propertySource.nextMerkleTokenRelStatus();
    }

    @Override
    protected MerkleTokenRelStatus getExpectedObject(
            final int version, final int testCaseNo, @NonNull final EqualityType equalityType) {
        var expected = super.getExpectedObject(version, testCaseNo);
        if (version < MerkleTokenRelStatus.RELEASE_0250_VERSION) {
            expected.setNext(0);
            expected.setPrev(0);
        }
        final var pbjTokenRelation = TokenRelationStateTranslator.tokenRelationFromMerkleTokenRelStatus(expected);
        final var merkleTokenRelStatus =
                TokenRelationStateTranslator.merkleTokenRelStatusFromTokenRelation(pbjTokenRelation);
        return merkleTokenRelStatus;
    }
}
