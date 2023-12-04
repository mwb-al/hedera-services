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

package com.hedera.node.app.service.contract.impl.test.utils;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.node.app.service.contract.impl.utils.SystemContractUtils;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class SystemContractUtilsTests {
    private static final long gasUsed = 0L;
    private static final Bytes result = Bytes.EMPTY;
    private static final ContractID contractID =
            ContractID.newBuilder().contractNum(111).build();
    private static final String errorMessage = ResponseCodeEnum.FAIL_INVALID.name();

    @Test
    void validateSuccessfulContractResults() {
        final var expected = ContractFunctionResult.newBuilder()
                .gasUsed(gasUsed)
                .contractCallResult(tuweniToPbjBytes(result))
                .contractID(contractID)
                .build();
        final var actual = SystemContractUtils.contractFunctionResultSuccessFor(gasUsed, result, contractID);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void validateFailedContractResults() {
        final var expected = ContractFunctionResult.newBuilder()
                .gasUsed(gasUsed)
                .errorMessage(errorMessage)
                .contractID(contractID)
                .build();
        final var actual = SystemContractUtils.contractFunctionResultFailedFor(gasUsed, errorMessage, contractID);
        assertThat(actual).isEqualTo(expected);
    }
}
