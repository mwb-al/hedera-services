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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.test.factories.scenarios.TokenUnpauseScenarios.VALID_UNPAUSE_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_PAUSE_KT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.TokenUnpauseHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.ParityTestBase;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenUnpauseHandlerParityTests extends ParityTestBase {
    private TokenUnpauseHandler subject;

    @BeforeEach
    public void setUp() {
        super.setUp();
        subject = new TokenUnpauseHandler();
    }

    @Test
    void tokenUnpauseWithValidExtantTokenScenario() throws PreCheckException {
        final var theTxn = txnFrom(VALID_UNPAUSE_WITH_EXTANT_TOKEN);

        final var context = new FakePreHandleContext(readableAccountStore, theTxn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(context);

        assertEquals(1, context.requiredNonPayerKeys().size());
        assertThat(context.requiredNonPayerKeys(), containsInAnyOrder(TOKEN_PAUSE_KT.asPbjKey()));
    }
}
