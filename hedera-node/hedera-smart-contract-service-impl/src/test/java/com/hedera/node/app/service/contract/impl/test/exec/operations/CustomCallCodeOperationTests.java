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

package com.hedera.node.app.service.contract.impl.test.exec.operations;

import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.*;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;

import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import com.hedera.node.app.service.contract.impl.exec.operations.CustomCallCodeOperation;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomCallCodeOperationTests {

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private AddressChecks addressChecks;

    @Mock
    private MessageFrame frame;

    @Mock
    private WorldUpdater worldUpdater;

    @Mock
    private EVM evm;

    private CustomCallCodeOperation subject;

    @BeforeEach
    void setUp() {
        subject = new CustomCallCodeOperation(gasCalculator, addressChecks);
    }

    @Test
    void catchesUnderflowWhenStackIsEmpty() {
        given(frame.getStackItem(1)).willThrow(FixedStack.UnderflowException.class);
        final var expected = new Operation.OperationResult(0L, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
        assertSameResult(expected, subject.execute(frame, evm));
    }

    @Test
    void rejectsMissingNonSystemAddress() {
        doCallRealMethod().when(addressChecks).isNeitherSystemNorPresent(any(), any());
        givenWellKnownFrameWith(1L, NON_SYSTEM_LONG_ZERO_ADDRESS, 2L);
        final var expected = new Operation.OperationResult(REQUIRED_GAS, INVALID_SOLIDITY_ADDRESS);
        assertSameResult(expected, subject.execute(frame, evm));
    }

    @Test
    void permitsSystemAddress() {
        doCallRealMethod().when(addressChecks).isNeitherSystemNorPresent(any(), any());
        given(addressChecks.isSystemAccount(NON_SYSTEM_LONG_ZERO_ADDRESS)).willReturn(true);
        givenWellKnownFrameWith(1L, NON_SYSTEM_LONG_ZERO_ADDRESS, 2L);
        given(frame.stackSize()).willReturn(7);
        final var expected = new Operation.OperationResult(REQUIRED_GAS, INSUFFICIENT_GAS);
        assertSameResult(expected, subject.execute(frame, evm));
    }

    private void givenWellKnownFrameWith(final long value, final Address to, final long gas) {
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getStackItem(0)).willReturn(Bytes32.leftPad(Bytes.ofUnsignedLong(gas)));
        given(frame.getStackItem(1)).willReturn(to);
        given(frame.getStackItem(2)).willReturn(Bytes32.leftPad(Bytes.ofUnsignedLong(value)));
        given(frame.getStackItem(3)).willReturn(Bytes32.leftPad(Bytes.ofUnsignedLong(3)));
        given(frame.getStackItem(4)).willReturn(Bytes32.leftPad(Bytes.ofUnsignedLong(4)));
        given(frame.getStackItem(5)).willReturn(Bytes32.leftPad(Bytes.ofUnsignedLong(5)));
        given(frame.getStackItem(6)).willReturn(Bytes32.leftPad(Bytes.ofUnsignedLong(6)));
        given(gasCalculator.callOperationGasCost(
                        any(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), any(), any(), any()))
                .willReturn(REQUIRED_GAS);
    }
}
