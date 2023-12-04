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

package com.swirlds.platform.state.manager;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.platform.components.state.output.StateHasEnoughSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateManager;
import java.time.Instant;
import java.util.HashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SignedStateManager: Sequential Signatures Test")
public class SequentialSignaturesTests extends AbstractSignedStateManagerTests {

    // Note: this unit test was long and complex, so it was split into its own class.
    // As such, this test was designed differently than it would be designed if it were sharing
    // the class file with other tests.
    // DO NOT ADD ADDITIONAL UNIT TESTS TO THIS CLASS!

    private final int roundAgeToSign = 3;

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
     * <p>
     * This consumer is provided by the wiring layer, so it should release the resource when finished.
     */
    private StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer() {
        return ss -> {
            assertEquals(highestRound.get() - roundAgeToSign, ss.getRound(), "unexpected round completed");
            stateHasEnoughSignaturesCount.getAndIncrement();
        };
    }

    @Test
    @DisplayName("Sequential Signatures Test")
    void sequentialSignaturesTest() throws InterruptedException {
        this.roundsToKeepAfterSigning = 4;
        final SignedStateManager manager = new SignedStateManagerBuilder(buildStateConfig())
                .stateLacksSignaturesConsumer(stateLacksSignaturesConsumer())
                .stateHasEnoughSignaturesConsumer(stateHasEnoughSignaturesConsumer())
                .build();

        assertNull(manager.getFirstStateTimestamp());
        assertEquals(-1, manager.getFirstStateRound());
        Instant firstTimestamp = null;
        final long firstRound = 0;

        // Create a series of signed states.
        final int count = 100;
        for (int round = 0; round < count; round++) {
            final SignedState signedState = new RandomSignedStateGenerator(random)
                    .setAddressBook(addressBook)
                    .setRound(round)
                    .setSignatures(new HashMap<>())
                    .build();

            signedStates.put((long) round, signedState);
            highestRound.set(round);

            manager.addState(signedState);

            if (round == 0) {
                firstTimestamp = signedState
                        .getState()
                        .getPlatformState()
                        .getPlatformData()
                        .getConsensusTimestamp();
            }
            assertEquals(firstTimestamp, manager.getFirstStateTimestamp());
            assertEquals(firstRound, manager.getFirstStateRound());

            // Add some signatures to one of the previous states
            final long roundToSign = round - roundAgeToSign;
            addSignature(manager, roundToSign, addressBook.getNodeId(0));
            addSignature(manager, roundToSign, addressBook.getNodeId(1));
            addSignature(manager, roundToSign, addressBook.getNodeId(2));
            if (random.nextBoolean()) {
                addSignature(manager, roundToSign, addressBook.getNodeId(1));
                addSignature(manager, roundToSign, addressBook.getNodeId(1));
            }

            try (final ReservedSignedState lastState = manager.getLatestImmutableState("test")) {
                assertSame(signedState, lastState.get(), "last signed state has unexpected value");
            }
            try (final ReservedSignedState lastCompletedState = manager.getLatestSignedState("test")) {
                if (roundToSign >= 0) {
                    assertSame(
                            signedStates.get(roundToSign), lastCompletedState.get(), "unexpected last completed state");
                } else {
                    assertNull(lastCompletedState.getNullable(), "no states should be completed yet");
                }
            }

            validateCallbackCounts(0, Math.max(0, round - roundAgeToSign + 1));
        }

        final long lastCompletedRound;
        try (final ReservedSignedState wrapper = manager.getLatestSignedState("test")) {
            lastCompletedRound = wrapper.get().getRound();
        }

        for (int round = 0; round < count; round++) {
            final long finalRound = round;
            try (final ReservedSignedState wrapper = manager.find(state -> state.getRound() == finalRound, "test")) {

                final SignedState foundState = wrapper.getNullable();

                if (round < count - roundsToKeepAfterSigning - 1) {
                    assertNull(foundState);
                    continue;
                }
                assertNotNull(foundState);

                if (foundState.getRound() <= lastCompletedRound) {
                    assertTrue(foundState.isComplete());
                } else {
                    assertFalse(foundState.isComplete());
                }
            }
        }

        // Check reservation counts.
        validateReservationCounts(round -> round < signedStates.size() - roundsToKeepAfterSigning - 1);

        // We don't expect any further callbacks. But wait a little while longer in case there is something unexpected.
        SECONDS.sleep(1);

        validateCallbackCounts(0, count - roundAgeToSign);
    }
}
