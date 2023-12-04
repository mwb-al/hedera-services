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

package com.hedera.services.bdd.suites.freeze;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForNodesToFreeze;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import com.hedera.services.bdd.suites.perf.crypto.CryptoTransferLoadTests;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CryptoTransferThenFreezeTests extends CryptoTransferLoadTests {
    private static final Logger log = LogManager.getLogger(CryptoTransferThenFreezeTests.class);

    public static void main(String... args) {
        parseArgs(args);

        CryptoTransferThenFreezeTests suite = new CryptoTransferThenFreezeTests();
        suite.runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(runCryptoTransfers(), freezeAfterTransfers());
    }

    private HapiSpec freezeAfterTransfers() {
        PerfTestLoadSettings settings = new PerfTestLoadSettings();
        return defaultHapiSpec("FreezeAfterTransfers")
                .given(
                        withOpContext(
                                (spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
                        logIt(ignore -> settings.toString()))
                .when(freezeOnly().startingIn(30).seconds().payingWith(GENESIS))
                .then(
                        // wait for the nodes to freeze (fails if they don't freeze)
                        waitForNodesToFreeze(75));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
