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

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyDoesNotThrow;
import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.platform.state.manager.SignedStateManagerTestUtils.buildFakeSignature;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.StateConfig_;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.transaction.internal.StateSignatureTransaction;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.test.framework.config.TestConfigBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;

/**
 * Boilerplate implementation for SignedStateManager tests.
 */
public class AbstractSignedStateManagerTests {

    protected final Random random = getRandomPrintSeed();

    protected AtomicInteger stateLacksSignaturesCount = new AtomicInteger();
    protected AtomicInteger stateHasEnoughSignaturesCount = new AtomicInteger();

    protected final Map<Long /* round */, SignedState> signedStates = new ConcurrentHashMap<>();
    protected final AtomicLong highestRound = new AtomicLong(-1);
    protected final int roundsToKeepForSigning = 5;
    protected final int futureStateSignatureRounds = 16;
    protected int roundsToKeepAfterSigning = 0;

    /**
     * true if an error occurs on a notification thread
     */
    protected final AtomicBoolean error = new AtomicBoolean(false);

    protected StateConfig buildStateConfig() {
        final Configuration configuration = new TestConfigBuilder()
                .withValue(StateConfig_.ROUNDS_TO_KEEP_FOR_SIGNING, roundsToKeepForSigning)
                .withValue(StateConfig_.MAX_AGE_OF_FUTURE_STATE_SIGNATURES, futureStateSignatureRounds)
                .withValue(StateConfig_.ROUNDS_TO_KEEP_AFTER_SIGNING, roundsToKeepAfterSigning)
                .getOrCreateConfig();

        return configuration.getConfigData(StateConfig.class);
    }

    @AfterEach
    protected void afterEach() {
        assertFalse(error.get(), "error detected");
    }

    /**
     * Add a signature for a node on a state from a given round.
     */
    protected void addSignature(
            @NonNull final SignedStateManager manager, final long round, @NonNull final NodeId nodeId) {
        Objects.requireNonNull(manager, "manager must not be null");
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        final SignedState signedState = signedStates.get(round);

        if (signedState == null) {
            // We are being asked to sign a non-existent round.
            return;
        }

        final AddressBook addressBook = signedState.getAddressBook();
        final Hash hash = signedState.getState().getHash();

        // Although we normally want to avoid rebuilding the dispatcher over and over, the slight
        // performance overhead is worth the convenience during unit tests

        final StateSignatureTransaction transaction = new StateSignatureTransaction(
                round, buildFakeSignature(addressBook.getAddress(nodeId).getSigPublicKey(), hash), hash);

        manager.handlePreconsensusSignatureTransaction(nodeId, transaction);
    }

    /**
     * Validate that callbacks were correctly invoked. Will wait up to 1 second for callbacks to properly be invoked.
     */
    protected void validateCallbackCounts(
            final int expectedStateLacksSignaturesCount, final int expectedStateHasEnoughSignaturesCount) {

        assertEventuallyDoesNotThrow(
                () -> {
                    assertEquals(
                            expectedStateLacksSignaturesCount,
                            stateLacksSignaturesCount.get(),
                            "unexpected number of callbacks");
                    assertEquals(
                            expectedStateHasEnoughSignaturesCount,
                            stateHasEnoughSignaturesCount.get(),
                            "unexpected number of callbacks");
                },
                Duration.ofSeconds(1),
                "callbacks not correctly invoked");
    }

    protected void validateReservationCounts(final Predicate<Long> shouldRoundBePresent) {
        // Check reservation counts. Only the 5 most recent states should have reservations.
        for (final SignedState signedState : signedStates.values()) {
            final long round = signedState.getRound();
            if (shouldRoundBePresent.test(round)) {
                assertEquals(-1, signedState.getReservationCount(), "state should have no reservations");
            } else {
                if (round == highestRound.get()) {
                    // the most recent state has an extra reservation
                    assertEquals(2, signedState.getReservationCount(), "unexpected reservation count");
                } else {
                    assertEquals(1, signedState.getReservationCount(), "unexpected reservation count");
                }
            }
        }
    }
}
