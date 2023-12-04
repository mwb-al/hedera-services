/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.records;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.includingDeduction;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.getNonFeeDeduction;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uncheckedSubmit;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class DuplicateManagementTests extends HapiSuite {
    private static final Logger log = LogManager.getLogger(DuplicateManagementTests.class);
    private static final String REPEATED = "repeated";
    private static final String TXN_ID = "txnId";
    private static final String TO = "0.0.3";
    private static final String CIVILIAN = "civilian";

    public static void main(String... args) {
        new DuplicateManagementTests().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            usesUnclassifiableIfNoClassifiableAvailable(),
            hasExpectedDuplicates(),
            classifiableTakesPriorityOverUnclassifiable(),
        });
    }

    private HapiSpec hasExpectedDuplicates() {
        return defaultHapiSpec("HasExpectedDuplicates")
                .given(
                        cryptoCreate(CIVILIAN).balance(ONE_HUNDRED_HBARS),
                        usableTxnIdNamed(TXN_ID).payerId(CIVILIAN))
                .when(
                        uncheckedSubmit(cryptoCreate(REPEATED)
                                        .payingWith(CIVILIAN)
                                        .txnId(TXN_ID))
                                .payingWith(CIVILIAN)
                                .fee(ONE_HBAR)
                                .hasPrecheck(NOT_SUPPORTED),
                        uncheckedSubmit(
                                cryptoCreate(REPEATED).payingWith(CIVILIAN).txnId(TXN_ID)),
                        uncheckedSubmit(
                                cryptoCreate(REPEATED).payingWith(CIVILIAN).txnId(TXN_ID)),
                        uncheckedSubmit(
                                cryptoCreate(REPEATED).payingWith(CIVILIAN).txnId(TXN_ID)),
                        sleepFor(1_000L))
                .then(
                        getReceipt(TXN_ID)
                                .andAnyDuplicates()
                                .payingWith(CIVILIAN)
                                .hasPriorityStatus(SUCCESS)
                                .hasDuplicateStatuses(DUPLICATE_TRANSACTION, DUPLICATE_TRANSACTION),
                        getTxnRecord(TXN_ID)
                                .payingWith(CIVILIAN)
                                .via("cheapTxn")
                                .assertingNothingAboutHashes()
                                .hasPriority(recordWith().status(SUCCESS)),
                        getTxnRecord(TXN_ID)
                                .andAnyDuplicates()
                                .payingWith(CIVILIAN)
                                .via("costlyTxn")
                                .assertingNothingAboutHashes()
                                .hasPriority(recordWith().status(SUCCESS))
                                .hasDuplicates(inOrder(
                                        recordWith().status(DUPLICATE_TRANSACTION),
                                        recordWith().status(DUPLICATE_TRANSACTION))),
                        sleepFor(1_000L),
                        withOpContext((spec, opLog) -> {
                            var cheapGet = getTxnRecord("cheapTxn").assertingNothingAboutHashes();
                            var costlyGet = getTxnRecord("costlyTxn").assertingNothingAboutHashes();
                            allRunFor(spec, cheapGet, costlyGet);
                            var payer = spec.registry().getAccountID(CIVILIAN);
                            var cheapRecord = cheapGet.getResponseRecord();
                            var costlyRecord = costlyGet.getResponseRecord();
                            opLog.info("cheapRecord: {}", cheapRecord);
                            opLog.info("costlyRecord: {}", costlyRecord);
                            var cheapPrice = getNonFeeDeduction(cheapRecord).orElse(0);
                            var costlyPrice = getNonFeeDeduction(costlyRecord).orElse(0);
                            assertEquals(
                                    3 * cheapPrice,
                                    costlyPrice,
                                    String.format(
                                            "Costly (%d) should be 3x more expensive than" + " cheap (%d)!",
                                            costlyPrice, cheapPrice));
                        }));
    }

    private HapiSpec usesUnclassifiableIfNoClassifiableAvailable() {
        return defaultHapiSpec("UsesUnclassifiableIfNoClassifiableAvailable")
                .given(
                        newKeyNamed("wrongKey"),
                        cryptoCreate(CIVILIAN),
                        usableTxnIdNamed(TXN_ID).payerId(CIVILIAN),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, TO, 100_000_000L)))
                .when(
                        uncheckedSubmit(cryptoCreate("nope")
                                .payingWith(CIVILIAN)
                                .txnId(TXN_ID)
                                .signedBy("wrongKey")),
                        sleepFor(1_000L))
                .then(
                        getReceipt(TXN_ID).hasPriorityStatus(INVALID_PAYER_SIGNATURE),
                        getTxnRecord(TXN_ID)
                                .assertingNothingAboutHashes()
                                .hasPriority(recordWith()
                                        .status(INVALID_PAYER_SIGNATURE)
                                        .transfers(includingDeduction("node payment", TO))));
    }

    private HapiSpec classifiableTakesPriorityOverUnclassifiable() {
        return defaultHapiSpec("ClassifiableTakesPriorityOverUnclassifiable")
                .given(
                        cryptoCreate(CIVILIAN).balance(100 * 100_000_000L),
                        usableTxnIdNamed(TXN_ID).payerId(CIVILIAN),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, TO, 100_000_000L)))
                .when(
                        uncheckedSubmit(cryptoCreate("nope")
                                        .txnId(TXN_ID)
                                        .payingWith(CIVILIAN)
                                        .setNode("0.0.4"))
                                .logged(),
                        uncheckedSubmit(cryptoCreate("sure")
                                .txnId(TXN_ID)
                                .payingWith(CIVILIAN)
                                .setNode(TO)),
                        sleepFor(1_000L))
                .then(
                        getReceipt(TXN_ID)
                                .andAnyDuplicates()
                                .logged()
                                .hasPriorityStatus(SUCCESS)
                                .hasDuplicateStatuses(INVALID_NODE_ACCOUNT),
                        getTxnRecord(TXN_ID)
                                .assertingNothingAboutHashes()
                                .andAnyDuplicates()
                                .hasPriority(recordWith().status(SUCCESS))
                                .hasDuplicates(inOrder(recordWith().status(INVALID_NODE_ACCOUNT))));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
