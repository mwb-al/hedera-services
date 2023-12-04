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

package com.hedera.node.app.service.contract.impl.test.exec;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.hevm.HederaEvmVersion.VERSION_038;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ETH_DATA_WITH_TO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.HEVM_CREATION;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SUCCESS_RESULT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.assertFailsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.CallOutcome;
import com.hedera.node.app.service.contract.impl.exec.ContextTransactionProcessor;
import com.hedera.node.app.service.contract.impl.exec.TransactionProcessor;
import com.hedera.node.app.service.contract.impl.hevm.ActionSidecarContentTracer;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.hevm.HydratedEthTxData;
import com.hedera.node.app.service.contract.impl.infra.HevmTransactionFactory;
import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContextTransactionProcessorTests {
    private static final Configuration CONFIGURATION = HederaTestConfigBuilder.createConfig();

    @Mock
    private HandleContext context;

    @Mock
    private HederaEvmContext hederaEvmContext;

    @Mock
    private ActionSidecarContentTracer tracer;

    @Mock
    private HevmTransactionFactory hevmTransactionFactory;

    @Mock
    private TransactionProcessor processor;

    @Mock
    private RootProxyWorldUpdater baseProxyWorldUpdater;

    @Mock
    private Supplier<HederaWorldUpdater> feesOnlyUpdater;

    @Test
    void callsComponentInfraAsExpectedForValidEthTx() {
        final var contractsConfig = CONFIGURATION.getConfigData(ContractsConfig.class);
        final var processors = Map.of(VERSION_038, processor);
        final var subject = new ContextTransactionProcessor(
                HydratedEthTxData.successFrom(ETH_DATA_WITH_TO_ADDRESS),
                context,
                contractsConfig,
                CONFIGURATION,
                hederaEvmContext,
                tracer,
                baseProxyWorldUpdater,
                hevmTransactionFactory,
                feesOnlyUpdater,
                processors);

        given(context.body()).willReturn(TransactionBody.DEFAULT);
        given(hevmTransactionFactory.fromHapiTransaction(TransactionBody.DEFAULT))
                .willReturn(HEVM_CREATION);
        given(processor.processTransaction(
                        HEVM_CREATION, baseProxyWorldUpdater, feesOnlyUpdater, hederaEvmContext, tracer, CONFIGURATION))
                .willReturn(SUCCESS_RESULT);

        final var protoResult = SUCCESS_RESULT.asProtoResultOf(ETH_DATA_WITH_TO_ADDRESS, baseProxyWorldUpdater);
        final var expectedResult =
                new CallOutcome(protoResult, SUCCESS, HEVM_CREATION.contractId(), SUCCESS_RESULT.gasPrice());
        assertEquals(expectedResult, subject.call());
    }

    @Test
    void callsComponentInfraAsExpectedForNonEthTx() {
        final var contractsConfig = CONFIGURATION.getConfigData(ContractsConfig.class);
        final var processors = Map.of(VERSION_038, processor);
        final var subject = new ContextTransactionProcessor(
                null,
                context,
                contractsConfig,
                CONFIGURATION,
                hederaEvmContext,
                tracer,
                baseProxyWorldUpdater,
                hevmTransactionFactory,
                feesOnlyUpdater,
                processors);

        given(context.body()).willReturn(TransactionBody.DEFAULT);
        given(hevmTransactionFactory.fromHapiTransaction(TransactionBody.DEFAULT))
                .willReturn(HEVM_CREATION);
        given(processor.processTransaction(
                        HEVM_CREATION, baseProxyWorldUpdater, feesOnlyUpdater, hederaEvmContext, tracer, CONFIGURATION))
                .willReturn(SUCCESS_RESULT);

        final var protoResult = SUCCESS_RESULT.asProtoResultOf(null, baseProxyWorldUpdater);
        final var expectedResult =
                new CallOutcome(protoResult, SUCCESS, HEVM_CREATION.contractId(), SUCCESS_RESULT.gasPrice());
        assertEquals(expectedResult, subject.call());
    }

    @Test
    void failsImmediatelyIfEthTxInvalid() {
        final var contractsConfig = CONFIGURATION.getConfigData(ContractsConfig.class);
        final var processors = Map.of(VERSION_038, processor);
        final var subject = new ContextTransactionProcessor(
                HydratedEthTxData.failureFrom(INVALID_ETHEREUM_TRANSACTION),
                context,
                contractsConfig,
                CONFIGURATION,
                hederaEvmContext,
                tracer,
                baseProxyWorldUpdater,
                hevmTransactionFactory,
                feesOnlyUpdater,
                processors);

        assertFailsWith(INVALID_ETHEREUM_TRANSACTION, subject::call);
    }
}
