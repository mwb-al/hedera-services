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

package com.hedera.services.bdd.suites.crypto;

import static com.hedera.services.bdd.junit.TestTags.CRYPTO;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_START;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;

import com.hedera.services.bdd.junit.HapiTests;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Transaction;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite
@Tag(CRYPTO)
public class CryptoCornerCasesSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CryptoCornerCasesSuite.class);
    private static final String NEW_PAYEE = "newPayee";

    public static void main(String... args) {
        new CryptoCornerCasesSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                invalidNodeAccount(),
                invalidTransactionBody(),
                invalidTransactionPayerAccountNotFound(),
                invalidTransactionMemoTooLong(),
                invalidTransactionDuration(),
                invalidTransactionStartTime());
    }

    private static Transaction removeTransactionBody(Transaction txn) {
        return txn.toBuilder()
                .setBodyBytes(Transaction.getDefaultInstance().getBodyBytes())
                .build();
    }

    @HapiTests
    private HapiSpec invalidTransactionBody() {
        return defaultHapiSpec("InvalidTransactionBody")
                .given()
                .when()
                .then(cryptoCreate(NEW_PAYEE)
                        .balance(10000L)
                        .withProtoStructure(HapiSpecSetup.TxnProtoStructure.OLD) // Ensure legacy construction so
                        // removeTransactionBody() works
                        .scrambleTxnBody(CryptoCornerCasesSuite::removeTransactionBody)
                        .hasPrecheckFrom(INVALID_TRANSACTION_BODY, INVALID_TRANSACTION));
    }

    private static Transaction replaceTxnNodeAccount(Transaction txn) {
        AccountID badNodeAccount = AccountID.newBuilder()
                .setAccountNum(2000)
                .setRealmNum(0)
                .setShardNum(0)
                .build();
        return TxnUtils.replaceTxnNodeAccount(txn, badNodeAccount);
    }

    @HapiTests
    private HapiSpec invalidNodeAccount() {
        return defaultHapiSpec("InvalidNodeAccount")
                .given()
                .when()
                .then(cryptoCreate(NEW_PAYEE)
                        .balance(10000L)
                        .scrambleTxnBody(CryptoCornerCasesSuite::replaceTxnNodeAccount)
                        .hasPrecheckFrom(INVALID_NODE_ACCOUNT, INVALID_TRANSACTION));
    }

    private static Transaction replaceTxnDuration(Transaction txn) {
        return TxnUtils.replaceTxnDuration(txn, -1L);
    }

    @HapiTests
    private HapiSpec invalidTransactionDuration() {
        return defaultHapiSpec("InvalidTransactionDuration")
                .given()
                .when()
                .then(cryptoCreate(NEW_PAYEE)
                        .balance(10000L)
                        .scrambleTxnBody(CryptoCornerCasesSuite::replaceTxnDuration)
                        .hasPrecheckFrom(INVALID_TRANSACTION_DURATION, INVALID_TRANSACTION));
    }

    private static Transaction replaceTxnMemo(Transaction txn) {
        String newMemo = RandomStringUtils.randomAlphanumeric(120);
        return TxnUtils.replaceTxnMemo(txn, newMemo);
    }

    @HapiTests
    private HapiSpec invalidTransactionMemoTooLong() {
        return defaultHapiSpec("InvalidTransactionMemoTooLong")
                .given()
                .when()
                .then(cryptoCreate(NEW_PAYEE)
                        .balance(10000L)
                        .scrambleTxnBody(CryptoCornerCasesSuite::replaceTxnMemo)
                        .hasPrecheckFrom(MEMO_TOO_LONG, INVALID_TRANSACTION));
    }

    private static Transaction replaceTxnPayerAccount(Transaction txn) {
        AccountID badPayerAccount = AccountID.newBuilder()
                .setShardNum(0)
                .setRealmNum(0)
                .setAccountNum(999999)
                .build();
        return TxnUtils.replaceTxnPayerAccount(txn, badPayerAccount);
    }

    @HapiTests
    private HapiSpec invalidTransactionPayerAccountNotFound() {
        return defaultHapiSpec("InvalidTransactionDuration")
                .given()
                .when()
                .then(cryptoCreate(NEW_PAYEE)
                        .balance(10000L)
                        .scrambleTxnBody(CryptoCornerCasesSuite::replaceTxnPayerAccount)
                        .hasPrecheckFrom(PAYER_ACCOUNT_NOT_FOUND, INVALID_TRANSACTION));
    }

    private static Transaction replaceTxnStartTtime(Transaction txn) {
        long newStartTimeSecs = Instant.now(Clock.systemUTC()).getEpochSecond() + 100L;
        return TxnUtils.replaceTxnStartTime(txn, newStartTimeSecs, 0);
    }

    @HapiTests
    private HapiSpec invalidTransactionStartTime() {
        return defaultHapiSpec("InvalidTransactionStartTime")
                .given()
                .when()
                .then(cryptoCreate(NEW_PAYEE)
                        .balance(10000L)
                        .scrambleTxnBody(CryptoCornerCasesSuite::replaceTxnStartTtime)
                        .hasPrecheckFrom(INVALID_TRANSACTION_START, INVALID_TRANSACTION));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
