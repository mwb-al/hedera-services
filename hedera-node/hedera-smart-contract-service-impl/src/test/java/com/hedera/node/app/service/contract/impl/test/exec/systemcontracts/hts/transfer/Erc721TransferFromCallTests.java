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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.transfer;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.B_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asEvmAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc721TransferFromCall;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.HtsCallTestBase;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class Erc721TransferFromCallTests extends HtsCallTestBase {
    private static final org.hyperledger.besu.datatypes.Address FRAME_SENDER_ADDRESS = EIP_1014_ADDRESS;
    private static final Address FROM_ADDRESS = ConversionUtils.asHeadlongAddress(EIP_1014_ADDRESS.toArray());
    private static final Address TO_ADDRESS =
            ConversionUtils.asHeadlongAddress(asEvmAddress(B_NEW_ACCOUNT_ID.accountNumOrThrow()));

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private VerificationStrategy verificationStrategy;

    @Mock
    private CryptoTransferRecordBuilder recordBuilder;

    private Erc721TransferFromCall subject;

    @Test
    void happyPathSucceedsWithEmptyBytes() {
        givenSynthIdHelperForToAccount();
        given(systemContractOperations.dispatch(
                        any(TransactionBody.class),
                        eq(verificationStrategy),
                        eq(SENDER_ID),
                        eq(CryptoTransferRecordBuilder.class)))
                .willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(ResponseCodeEnum.SUCCESS);

        subject = subjectFor(1L);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(Bytes.EMPTY, result.getOutput());
    }

    @Test
    void unhappyPathRevertsWithReason() {
        givenSynthIdHelperForToAccount();
        given(systemContractOperations.dispatch(
                        any(TransactionBody.class),
                        eq(verificationStrategy),
                        eq(SENDER_ID),
                        eq(CryptoTransferRecordBuilder.class)))
                .willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);

        subject = subjectFor(1L);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.REVERT, result.getState());
        assertEquals(Bytes.wrap(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO.protoName().getBytes()), result.getOutput());
    }

    private void givenSynthIdHelperForToAccount() {
        given(addressIdConverter.convertCredit(TO_ADDRESS)).willReturn(B_NEW_ACCOUNT_ID);
    }

    private Erc721TransferFromCall subjectFor(final long serialNo) {
        return new Erc721TransferFromCall(
                serialNo,
                FROM_ADDRESS,
                TO_ADDRESS,
                NON_FUNGIBLE_TOKEN_ID,
                verificationStrategy,
                mockEnhancement(),
                gasCalculator,
                SENDER_ID,
                addressIdConverter);
    }
}
