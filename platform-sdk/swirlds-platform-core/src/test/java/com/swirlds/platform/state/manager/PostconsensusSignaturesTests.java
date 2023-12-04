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

package com.swirlds.platform.state.manager;

import static com.swirlds.platform.state.manager.SignedStateManagerTestUtils.buildReallyFakeSignature;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.transaction.internal.StateSignatureTransaction;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.platform.components.state.output.StateHasEnoughSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SignedStateManager#handlePostconsensusSignatureTransaction}
 */
class PostconsensusSignaturesTests extends AbstractSignedStateManagerTests {

    private final AddressBook addressBook = new RandomAddressBookGenerator(random)
            .setSize(4)
            .setWeightDistributionStrategy(RandomAddressBookGenerator.WeightDistributionStrategy.BALANCED)
            .build();

    /**
     * Called on each state as it gets too old without collecting enough signatures.
     * <p>
     * This consumer is provided by the wiring layer, so it should release the resource when finished.
     */
    private StateLacksSignaturesConsumer stateLacksSignaturesConsumer() {
        // No state is unsigned in this test. If this method is called then the test is expected to fail.
        return ss -> stateLacksSignaturesCount.getAndIncrement();
    }

    /**
     * Called on each state as it gathers enough signatures to be complete.
     */
    private StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer() {
        return ss -> stateHasEnoughSignaturesCount.getAndIncrement();
    }

    @Test
    @DisplayName("Postconsensus signatures")
    void postconsensusSignatureTests() throws InterruptedException {
        final int count = 100;
        final StateConfig stateConfig = buildStateConfig();

        final SignedStateManager manager = new SignedStateManagerBuilder(stateConfig)
                .stateLacksSignaturesConsumer(stateLacksSignaturesConsumer())
                .stateHasEnoughSignaturesConsumer(stateHasEnoughSignaturesConsumer())
                .build();

        // Create a series of signed states.
        final List<SignedState> states = new ArrayList<>();
        for (int round = 0; round < count; round++) {
            final SignedState signedState = new RandomSignedStateGenerator(random)
                    .setAddressBook(addressBook)
                    .setRound(round)
                    .setSignatures(new HashMap<>())
                    .build();
            states.add(signedState);
        }

        for (int round = 0; round < count; round++) {
            final SignedState signedState = states.get(round);

            signedStates.put((long) round, signedState);
            highestRound.set(round);

            manager.addState(signedState);

            for (int node = 0; node < addressBook.getSize(); node++) {
                manager.handlePostconsensusSignatureTransaction(
                        addressBook.getNodeId(node),
                        new StateSignatureTransaction(
                                round,
                                buildReallyFakeSignature(),
                                states.get(round).getState().getHash()));
            }

            try (final ReservedSignedState lastState = manager.getLatestImmutableState("test")) {
                assertSame(signedState, lastState.get(), "last signed state has unexpected value");
            }
            try (final ReservedSignedState lastCompletedState = manager.getLatestSignedState("test")) {
                assertSame(signedStates.get((long) round), lastCompletedState.get(), "unexpected last completed state");
            }

            validateCallbackCounts(0, round + 1);
        }

        // Check reservation counts.
        validateReservationCounts(round -> round < signedStates.size() - 1);

        // We don't expect any further callbacks. But wait a little while longer in case there is something unexpected.
        SECONDS.sleep(1);

        validateCallbackCounts(0, count);
    }
}
