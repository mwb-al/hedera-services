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

package com.hedera.services.bdd.suites.regression;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.infrastructure.OpProvider.UNIQUE_PAYER_ACCOUNT;
import static com.hedera.services.bdd.spec.infrastructure.OpProvider.UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.suites.leaky.LeakyCryptoTestsSuite.*;
import static com.hedera.services.bdd.suites.regression.factories.IdFuzzingProviderFactory.*;
import static java.util.stream.Collectors.joining;

import com.hedera.services.bdd.junit.HapiTests;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * We want to make this suite exercise all forms of identity a Hedera account may have, under all
 * possible circumstances. (This could take us a while to do.)
 *
 * <p>See <a href="https://github.com/hashgraph/hedera-services/issues/4565">#4565</a> for details.
 */
@HapiTestSuite
public class AddressAliasIdFuzzing extends HapiSuite {
    private static final Logger log = LogManager.getLogger(AddressAliasIdFuzzing.class);

    private static final String PROPERTIES = "id-fuzzing.properties";
    public static final String ATOMIC_CRYPTO_TRANSFER = "contracts.precompile.atomicCryptoTransfer.enabled";
    private final AtomicInteger maxOpsPerSec = new AtomicInteger(1);
    private final AtomicInteger maxPendingOps = new AtomicInteger(Integer.MAX_VALUE);
    private final AtomicInteger backoffSleepSecs = new AtomicInteger(Integer.MAX_VALUE);

    public static void main(String... args) {
        new AddressAliasIdFuzzing().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(addressAliasIdFuzzing(), transferToKeyFuzzing());
    }

    // FileUpdate resolves to UNKNOWN
    private HapiSpec addressAliasIdFuzzing() {
        final Map<String, String> existingProps = new LinkedHashMap<>();
        return propertyPreservingHapiSpec("AddressAliasIdFuzzing")
                .preserving(
                        CHAIN_ID_PROP, LAZY_CREATE_PROPERTY_NAME, CONTRACTS_EVM_VERSION_PROP, ATOMIC_CRYPTO_TRANSFER)
                .given(
                        getFileContents(APP_PROPERTIES).addingConfigListTo(existingProps),
                        withOpContext((spec, opLog) -> log.info(
                                "Before initOperations() properties are\n\t{}",
                                existingProps.entrySet().stream()
                                        .map(e -> e.getKey() + "=" + e.getValue())
                                        .collect(joining("\n\t")))))
                .when(initOperations())
                .then(runWithProvider(idFuzzingWith(PROPERTIES))
                        .lasting(10L, TimeUnit.SECONDS)
                        .maxOpsPerSec(maxOpsPerSec::get)
                        .maxPendingOps(maxPendingOps::get)
                        .backoffSleepSecs(backoffSleepSecs::get));
    }

    @HapiTests
    private HapiSpec transferToKeyFuzzing() {
        return defaultHapiSpec("TransferToKeyFuzzing")
                .given(cryptoCreate(UNIQUE_PAYER_ACCOUNT)
                        .balance(UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE)
                        .withRecharging())
                .when()
                .then(runWithProvider(idTransferToRandomKeyWith(PROPERTIES)).lasting(10L, TimeUnit.SECONDS));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
