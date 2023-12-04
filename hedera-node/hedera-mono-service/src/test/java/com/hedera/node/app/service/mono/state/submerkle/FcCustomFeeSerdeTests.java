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

package com.hedera.node.app.service.mono.state.submerkle;

import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.test.serde.EqualityType;
import com.hedera.test.serde.SelfSerializableDataTests;
import com.hedera.test.utils.SeededPropertySource;
import edu.umd.cs.findbugs.annotations.NonNull;

public class FcCustomFeeSerdeTests extends SelfSerializableDataTests<FcCustomFee> {
    @Override
    protected Class<FcCustomFee> getType() {
        return FcCustomFee.class;
    }

    @Override
    protected FcCustomFee getExpectedObject(final SeededPropertySource propertySource) {
        return propertySource.nextCustomFee();
    }

    @Override
    protected FcCustomFee getExpectedObject(int version, int testCaseNo, @NonNull final EqualityType equalityType) {
        final var propertySource = SeededPropertySource.forSerdeTest(version, testCaseNo);
        final var nextFee = propertySource.nextCustomFee();
        if (version < FcCustomFee.RELEASE_0310_VERSION) {
            nextFee.setAllCollectorsAreExempt(false);
        }
        final var seededFee = nextFee;
        final var pbjFee = PbjConverter.fromFcCustomFee(seededFee);
        final var merkleFcCustomFee = FcCustomFee.fromGrpc(PbjConverter.fromPbj(pbjFee));
        return merkleFcCustomFee;
    }
}
