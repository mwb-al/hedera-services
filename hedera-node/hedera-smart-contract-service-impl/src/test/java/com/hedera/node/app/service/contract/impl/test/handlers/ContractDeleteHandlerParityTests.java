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

package com.hedera.node.app.service.contract.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.test.factories.scenarios.ContractCreateScenarios.MISC_ADMIN_KT;
import static com.hedera.test.factories.scenarios.ContractCreateScenarios.RECEIVER_SIG_KT;
import static com.hedera.test.factories.scenarios.ContractDeleteScenarios.CONTRACT_DELETE_IMMUTABLE_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractDeleteScenarios.CONTRACT_DELETE_MISSING_ACCOUNT_BENEFICIARY_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractDeleteScenarios.CONTRACT_DELETE_MISSING_CONTRACT_BENEFICIARY_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractDeleteScenarios.CONTRACT_DELETE_XFER_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractDeleteScenarios.CONTRACT_DELETE_XFER_CONTRACT_SCENARIO;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.handlers.ContractDeleteHandler;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContractDeleteHandlerParityTests {
    private ReadableAccountStore accountStore;
    private final ContractDeleteHandler subject = new ContractDeleteHandler();

    @BeforeEach
    void setUp() {
        accountStore = AdapterUtils.wellKnownKeyLookupAt();
    }

    @Test
    void getsContractDeleteImmutable() throws PreCheckException {
        final var theTxn = txnFrom(CONTRACT_DELETE_IMMUTABLE_SCENARIO);
        final var context = new FakePreHandleContext(accountStore, theTxn);
        assertThrowsPreCheck(() -> subject.preHandle(context), MODIFYING_IMMUTABLE_CONTRACT);
    }

    @Test
    void getsContractDelete() throws PreCheckException {
        final var theTxn = txnFrom(CONTRACT_DELETE_XFER_ACCOUNT_SCENARIO);
        final var context = new FakePreHandleContext(accountStore, theTxn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(context.requiredNonPayerKeys())
                .containsExactlyInAnyOrder(MISC_ADMIN_KT.asPbjKey(), RECEIVER_SIG_KT.asPbjKey());
    }

    @Test
    void getsContractDeleteMissingAccountBeneficiary() throws PreCheckException {
        final var theTxn = txnFrom(CONTRACT_DELETE_MISSING_ACCOUNT_BENEFICIARY_SCENARIO);
        final var context = new FakePreHandleContext(accountStore, theTxn);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_TRANSFER_ACCOUNT_ID);
    }

    @Test
    void getsContractDeleteMissingContractBeneficiary() throws PreCheckException {
        final var theTxn = txnFrom(CONTRACT_DELETE_MISSING_CONTRACT_BENEFICIARY_SCENARIO);
        final var context = new FakePreHandleContext(accountStore, theTxn);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_CONTRACT_ID);
    }

    @Test
    void getsContractDeleteContractXfer() throws PreCheckException {
        final var theTxn = txnFrom(CONTRACT_DELETE_XFER_CONTRACT_SCENARIO);
        final var context = new FakePreHandleContext(accountStore, theTxn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(context.requiredNonPayerKeys()).containsExactlyInAnyOrder(MISC_ADMIN_KT.asPbjKey());
    }

    private TransactionBody txnFrom(final TxnHandlingScenario scenario) {
        try {
            return toPbj(scenario.platformTxn().getTxn());
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
