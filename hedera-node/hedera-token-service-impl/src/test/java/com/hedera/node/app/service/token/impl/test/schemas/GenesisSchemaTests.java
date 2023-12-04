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

package com.hedera.node.app.service.token.impl.test.schemas;

import static com.hedera.node.app.service.token.impl.TokenServiceImpl.ACCOUNTS_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.ALIASES_KEY;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.spi.HapiUtils.EMPTY_KEY_LIST;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.token.impl.schemas.TokenSchema;
import com.hedera.node.app.spi.fixtures.info.FakeNetworkInfo;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.state.EmptyReadableStates;
import com.hedera.node.app.spi.state.WritableSingletonState;
import com.hedera.node.app.spi.state.WritableSingletonStateBase;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.spi.throttle.HandleThrottleParser;
import com.hedera.node.app.spi.workflows.record.GenesisRecordsBuilder;
import com.hedera.node.app.workflows.handle.record.MigrationContextImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.bouncycastle.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class GenesisSchemaTests {

    private static final String GENESIS_KEY = "0aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92";
    private static final long EXPECTED_TREASURY_TINYBARS_BALANCE = 5000000000000000000L;
    private static final int NUM_SYSTEM_ACCOUNTS = 312;
    private static final long EXPECTED_ENTITY_EXPIRY = 1812637686L;
    private static final long TREASURY_ACCOUNT_NUM = 2L;
    private static final long NUM_RESERVED_SYSTEM_ENTITIES = 750L;
    private static final String EVM_ADDRESS_0 = "e261e26aecce52b3788fac9625896ffbc6bb4424";
    private static final String EVM_ADDRESS_1 = "ce16e8eb8f4bf2e65ba9536c07e305b912bafacf";
    private static final String EVM_ADDRESS_2 = "f39fd6e51aad88f6f4ce6ab8827279cfffb92266";
    private static final String EVM_ADDRESS_3 = "70997970c51812dc3a010c7d01b50e0d17dc79c8";
    private static final String EVM_ADDRESS_4 = "7e5f4552091a69125d5dfcb7b8c2659029395bdf";
    private static final String EVM_ADDRESS_5 = "a04a864273e77be6fe500ad2f5fad320d9168bb6";
    private static final String[] EVM_ADDRESSES = {
        EVM_ADDRESS_0, EVM_ADDRESS_1, EVM_ADDRESS_2, EVM_ADDRESS_3, EVM_ADDRESS_4, EVM_ADDRESS_5
    };
    private static final long BEGINNING_ENTITY_ID = 3000;

    @Mock
    private GenesisRecordsBuilder genesisRecordsBuilder;

    @Mock
    private HandleThrottleParser handleThrottling;

    @Captor
    private ArgumentCaptor<Map<Account, CryptoCreateTransactionBody.Builder>> sysAcctMapCaptor;

    @Captor
    private ArgumentCaptor<Map<Account, CryptoCreateTransactionBody.Builder>> stakingAcctMapCaptor;

    @Captor
    private ArgumentCaptor<Map<Account, CryptoCreateTransactionBody.Builder>> multiuseAcctMapCaptor;

    @Captor
    private ArgumentCaptor<Map<Account, CryptoCreateTransactionBody.Builder>> treasuryCloneMapCaptor;

    @Captor
    private ArgumentCaptor<Map<Account, CryptoCreateTransactionBody.Builder>> blocklistMapCaptor;

    private MapWritableKVState<AccountID, Account> accounts;
    private MapWritableKVState<Bytes, AccountID> aliases;
    private WritableStates newStates;
    private Configuration config;
    private NetworkInfo networkInfo;
    private WritableEntityIdStore entityIdStore;

    @BeforeEach
    void setUp() {
        accounts = MapWritableKVState.<AccountID, Account>builder(TokenServiceImpl.ACCOUNTS_KEY)
                .build();
        aliases = MapWritableKVState.<Bytes, AccountID>builder(ALIASES_KEY).build();

        newStates = newStatesInstance(accounts, aliases, newWritableEntityIdState());

        entityIdStore = new WritableEntityIdStore(newStates);

        networkInfo = new FakeNetworkInfo();

        config = buildConfig(NUM_SYSTEM_ACCOUNTS, true);
    }

    @Test
    void createsAllAccounts() {
        final var schema = new TokenSchema();
        final var migrationContext = new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                handleThrottling,
                entityIdStore);

        schema.migrate(migrationContext);

        // Verify created system accounts
        verify(genesisRecordsBuilder).systemAccounts(sysAcctMapCaptor.capture());
        final var sysAcctsResult = sysAcctMapCaptor.getValue();
        Assertions.assertThat(sysAcctsResult)
                .isNotNull()
                .hasSize(NUM_SYSTEM_ACCOUNTS)
                .allSatisfy((account, builder) -> {
                    verifySystemAccount(account);
                    verifyCryptoCreateBuilder(account, builder);
                });
        Assertions.assertThat(
                        sysAcctsResult.keySet().stream().map(Account::accountId).map(AccountID::accountNum))
                .allMatch(acctNum -> 1 <= acctNum && acctNum <= NUM_SYSTEM_ACCOUNTS);

        // Verify created staking accounts
        verify(genesisRecordsBuilder).stakingAccounts(stakingAcctMapCaptor.capture());
        final var stakingAcctsResult = stakingAcctMapCaptor.getValue();
        Assertions.assertThat(stakingAcctsResult).isNotNull().hasSize(2).allSatisfy((account, builder) -> {
            verifyStakingAccount(account);
            verifyCryptoCreateBuilder(account, builder);
        });
        Assertions.assertThat(stakingAcctsResult.keySet().stream()
                        .map(Account::accountId)
                        .map(AccountID::accountNum)
                        .toArray())
                .containsExactlyInAnyOrder(800L, 801L);

        // Verify created multipurpose accounts
        verify(genesisRecordsBuilder).miscAccounts(multiuseAcctMapCaptor.capture());
        final var multiuseAcctsResult = multiuseAcctMapCaptor.getValue();
        Assertions.assertThat(multiuseAcctsResult).isNotNull().hasSize(101).allSatisfy((account, builder) -> {
            verifyMultiUseAccount(account);
            verifyCryptoCreateBuilder(account, builder);
        });
        Assertions.assertThat(multiuseAcctsResult.keySet().stream()
                        .map(Account::accountId)
                        .map(AccountID::accountNum))
                .allMatch(acctNum -> 900 <= acctNum && acctNum <= 1000);

        // Verify created treasury clones
        verify(genesisRecordsBuilder).treasuryClones(treasuryCloneMapCaptor.capture());
        final var treasuryCloneAcctsResult = treasuryCloneMapCaptor.getValue();
        Assertions.assertThat(treasuryCloneAcctsResult).isNotNull().hasSize(388).allSatisfy((account, builder) -> {
            verifyTreasuryCloneAccount(account);
            verifyCryptoCreateBuilder(account, builder);
        });
        Assertions.assertThat(treasuryCloneAcctsResult.keySet().stream()
                        .map(Account::accountId)
                        .map(AccountID::accountNum))
                .allMatch(acctNum ->
                        Arrays.contains(TokenSchema.nonContractSystemNums(NUM_RESERVED_SYSTEM_ENTITIES), acctNum));

        // Verify created blocklist accounts
        verify(genesisRecordsBuilder).blocklistAccounts(blocklistMapCaptor.capture());
        final var blocklistAcctsResult = blocklistMapCaptor.getValue();
        Assertions.assertThat(blocklistAcctsResult).isNotNull().hasSize(6).allSatisfy((account, builder) -> {
            Assertions.assertThat(account).isNotNull();

            Assertions.assertThat(account.accountId().accountNum())
                    .isBetween(BEGINNING_ENTITY_ID, BEGINNING_ENTITY_ID + EVM_ADDRESSES.length);
            Assertions.assertThat(account.receiverSigRequired()).isTrue();
            Assertions.assertThat(account.declineReward()).isTrue();
            Assertions.assertThat(account.deleted()).isFalse();
            Assertions.assertThat(account.expirationSecond()).isEqualTo(EXPECTED_ENTITY_EXPIRY);
            Assertions.assertThat(account.autoRenewSeconds()).isEqualTo(EXPECTED_ENTITY_EXPIRY);
            Assertions.assertThat(account.smartContract()).isFalse();
            Assertions.assertThat(account.key()).isNotNull();
            Assertions.assertThat(account.alias()).isNotNull();

            verifyCryptoCreateBuilder(account, builder);
        });
    }

    @Test
    void someAccountsAlreadyExist() {
        final var schema = new TokenSchema();

        // We'll only configure 4 system accounts, half of which will already exist
        config = buildConfig(4, true);
        final var accts = new HashMap<AccountID, Account>();
        IntStream.rangeClosed(1, 2).forEach(i -> putNewAccount(i, accts));
        // One of the two staking accounts will already exist
        final var stakingAcctId = AccountID.newBuilder().accountNum(800L).build();
        accts.put(stakingAcctId, Account.newBuilder().accountId(stakingAcctId).build());
        // Half of the multipurpose accounts will already exist
        IntStream.rangeClosed(900, 950).forEach(i -> putNewAccount(i, accts));
        // All but five of the treasury clones will already exist
        IntStream.rangeClosed(200, 745).forEach(i -> {
            if (isRegularAcctNum(i)) putNewAccount(i, accts);
        });
        // Half of the blocklist accounts will already exist (simulated by the existence of alias mappings, not the
        // account objects)
        final var blocklistAccts = Map.of(
                Bytes.fromHex(EVM_ADDRESS_0), asAccount(BEGINNING_ENTITY_ID),
                Bytes.fromHex(EVM_ADDRESS_2), asAccount(BEGINNING_ENTITY_ID + 2),
                Bytes.fromHex(EVM_ADDRESS_4), asAccount(BEGINNING_ENTITY_ID + 4));
        newStates = newStatesInstance(
                new MapWritableKVState<>(ACCOUNTS_KEY, accts),
                new MapWritableKVState<>(ALIASES_KEY, blocklistAccts),
                newWritableEntityIdState());
        final var migrationContext = new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                handleThrottling,
                entityIdStore);

        schema.migrate(migrationContext);

        verify(genesisRecordsBuilder).systemAccounts(sysAcctMapCaptor.capture());
        final var sysAcctsResult = sysAcctMapCaptor.getValue();
        // Only system accts with IDs 3 and 4 should have been created
        Assertions.assertThat(sysAcctsResult).hasSize(2);

        verify(genesisRecordsBuilder).stakingAccounts(stakingAcctMapCaptor.capture());
        final var stakingAcctsResult = stakingAcctMapCaptor.getValue();
        // Only the staking acct with ID 801 should have been created
        Assertions.assertThat(stakingAcctsResult).hasSize(1);

        verify(genesisRecordsBuilder).miscAccounts(multiuseAcctMapCaptor.capture());
        final var multiuseAcctsResult = multiuseAcctMapCaptor.getValue();
        // Only multi-use accts with IDs 951-1000 should have been created
        Assertions.assertThat(multiuseAcctsResult).hasSize(50);

        verify(genesisRecordsBuilder).treasuryClones(treasuryCloneMapCaptor.capture());
        final var treasuryCloneAcctsResult = treasuryCloneMapCaptor.getValue();
        // Only treasury clones with IDs 746-750 should have been created
        Assertions.assertThat(treasuryCloneAcctsResult).hasSize(5);

        verify(genesisRecordsBuilder).blocklistAccounts(blocklistMapCaptor.capture());
        final var blocklistAcctsResult = blocklistMapCaptor.getValue();
        // Only half of the blocklist accts should have been created
        Assertions.assertThat(blocklistAcctsResult).hasSize(3);
    }

    @Test
    void allAccountsAlreadyExist() {
        final var schema = new TokenSchema();

        // All the system accounts will already exist
        final var accts = new HashMap<AccountID, Account>();
        IntStream.rangeClosed(1, NUM_SYSTEM_ACCOUNTS).forEach(i -> putNewAccount(i, accts));
        // Both of the two staking accounts will already exist
        IntStream.rangeClosed(800, 801).forEach(i -> putNewAccount(i, accts));
        // All the multipurpose accounts will already exist
        IntStream.rangeClosed(900, 1000).forEach(i -> putNewAccount(i, accts));
        // All the treasury clones will already exist
        IntStream.rangeClosed(200, 750).forEach(i -> {
            if (isRegularAcctNum(i)) putNewAccount(i, accts);
        });
        // All the blocklist accounts will already exist
        final var blocklistEvmAliasMappings = Map.of(
                Bytes.fromHex(EVM_ADDRESS_0), asAccount(BEGINNING_ENTITY_ID),
                Bytes.fromHex(EVM_ADDRESS_1), asAccount(BEGINNING_ENTITY_ID + 1),
                Bytes.fromHex(EVM_ADDRESS_2), asAccount(BEGINNING_ENTITY_ID + 2),
                Bytes.fromHex(EVM_ADDRESS_3), asAccount(BEGINNING_ENTITY_ID + 3),
                Bytes.fromHex(EVM_ADDRESS_4), asAccount(BEGINNING_ENTITY_ID + 4),
                Bytes.fromHex(EVM_ADDRESS_5), asAccount(BEGINNING_ENTITY_ID + 5));
        newStates = newStatesInstance(
                new MapWritableKVState<>(ACCOUNTS_KEY, accts),
                new MapWritableKVState<>(ALIASES_KEY, blocklistEvmAliasMappings),
                newWritableEntityIdState());
        final var migrationContext = new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                handleThrottling,
                entityIdStore);

        schema.migrate(migrationContext);

        verify(genesisRecordsBuilder).systemAccounts(emptyMap());
        verify(genesisRecordsBuilder).stakingAccounts(emptyMap());
        verify(genesisRecordsBuilder).miscAccounts(emptyMap());
        verify(genesisRecordsBuilder).treasuryClones(emptyMap());
        verify(genesisRecordsBuilder).blocklistAccounts(emptyMap());
    }

    @Test
    void blocklistNotEnabled() {
        final var schema = new TokenSchema();

        // None of the blocklist accounts will exist, but they shouldn't be created since blocklists aren't enabled
        config = buildConfig(NUM_SYSTEM_ACCOUNTS, false);
        final var migrationContext = new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                handleThrottling,
                entityIdStore);

        schema.migrate(migrationContext);

        verify(genesisRecordsBuilder).blocklistAccounts(emptyMap());
    }

    @Test
    void systemAccountsCreated() {
        final var schema = new TokenSchema();
        schema.migrate(new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                handleThrottling,
                entityIdStore));

        for (int i = 1; i <= 100; i++) {
            final var balance = i == 2 ? EXPECTED_TREASURY_TINYBARS_BALANCE : 0L;

            final var account = accounts.get(accountID(i));
            verifySystemAccount(account);
            assertThat(account.accountId()).isEqualTo(accountID(i));
            assertThat(account.keyOrThrow().ed25519OrThrow().toHex()).isEqualTo(GENESIS_KEY);
            assertBasicAccount(account, balance, EXPECTED_ENTITY_EXPIRY);
            assertThat(account.autoRenewSeconds()).isEqualTo(EXPECTED_ENTITY_EXPIRY);
        }
    }

    @Test
    void accountsBetweenFilesAndContracts() {
        final var schema = new TokenSchema();
        schema.migrate(new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                handleThrottling,
                entityIdStore));

        for (int i = 200; i < 350; i++) {
            final var account = accounts.get(accountID(i));
            assertThat(account).isNotNull();
            assertThat(account.accountId()).isEqualTo(accountID(i));
            assertThat(account.keyOrThrow().ed25519OrThrow().toHex()).isEqualTo(GENESIS_KEY);
            assertBasicAccount(account, 0, EXPECTED_ENTITY_EXPIRY);
        }
    }

    @Test
    void contractEntityIdsNotUsed() {
        final var schema = new TokenSchema();
        schema.migrate(new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                handleThrottling,
                entityIdStore));

        for (int i = 350; i < 400; i++) {
            assertThat(accounts.contains(accountID(i))).isFalse();
        }
    }

    @Test
    void accountsAfterContracts() {
        final var schema = new TokenSchema();
        schema.migrate(new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                handleThrottling,
                entityIdStore));

        for (int i = 400; i <= 750; i++) {
            final var account = accounts.get(accountID(i));
            assertThat(account).isNotNull();
            assertThat(account.accountId()).isEqualTo(accountID(i));
            assertThat(account.keyOrThrow().ed25519OrThrow().toHex()).isEqualTo(GENESIS_KEY);
            assertBasicAccount(account, 0, EXPECTED_ENTITY_EXPIRY);
        }
    }

    @Test
    void entityIdsBetweenSystemAccountsAndRewardAccountsAreEmpty() {
        final var schema = new TokenSchema();
        schema.migrate(new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                handleThrottling,
                entityIdStore));

        for (int i = 751; i < 800; i++) {
            assertThat(accounts.contains(accountID(i))).isFalse();
        }
    }

    @Test
    void stakingRewardAccounts() {
        final var schema = new TokenSchema();
        schema.migrate(new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                handleThrottling,
                entityIdStore));

        final var stakingRewardAccount = accounts.get(accountID(800));
        verifyStakingAccount(stakingRewardAccount);

        final var nodeRewardAccount = accounts.get(accountID(801));
        verifyStakingAccount(nodeRewardAccount);
    }

    @Test
    void entityIdsAfterRewardAccountsAreEmpty() {
        final var schema = new TokenSchema();
        schema.migrate(new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                handleThrottling,
                entityIdStore));

        for (int i = 802; i < 900; i++) {
            assertThat(accounts.contains(accountID(i))).isFalse();
        }
    }

    @Test
    void miscAccountsAfter900() {
        final var schema = new TokenSchema();
        schema.migrate(new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                handleThrottling,
                entityIdStore));

        for (int i = 900; i <= 1000; i++) {
            final var account = accounts.get(accountID(i));
            assertThat(account).isNotNull();
            assertThat(account.accountId()).isEqualTo(accountID(i));
            verifyMultiUseAccount(account);
        }
    }

    @Test
    void blocklistAccountIdsMatchEntityIds() {
        final var schema = new TokenSchema();
        schema.migrate(new MigrationContextImpl(
                EmptyReadableStates.INSTANCE,
                newStates,
                config,
                networkInfo,
                genesisRecordsBuilder,
                handleThrottling,
                entityIdStore));

        for (int i = 0; i < EVM_ADDRESSES.length; i++) {
            final var acctId = aliases.get(Bytes.fromHex(EVM_ADDRESSES[i]));
            assertThat(acctId).isEqualTo(accountID((int) BEGINNING_ENTITY_ID + i + 1));
        }
    }

    private void verifySystemAccount(final Account account) {
        assertThat(account).isNotNull();
        final long expectedBalance =
                account.accountId().accountNum() == TREASURY_ACCOUNT_NUM ? EXPECTED_TREASURY_TINYBARS_BALANCE : 0;
        assertBasicAccount(account, expectedBalance, EXPECTED_ENTITY_EXPIRY);
        assertThat(account.keyOrThrow().ed25519OrThrow().toHex()).isEqualTo(GENESIS_KEY);
        Assertions.assertThat(account.autoRenewSeconds()).isEqualTo(EXPECTED_ENTITY_EXPIRY);
    }

    private void verifyStakingAccount(final Account account) {
        assertBasicAccount(account, 0, 33197904000L);
        Assertions.assertThat(account.key()).isEqualTo(EMPTY_KEY_LIST);
    }

    private void verifyMultiUseAccount(final Account account) {
        assertBasicAccount(account, 0, EXPECTED_ENTITY_EXPIRY);
        assertThat(account.keyOrThrow().ed25519OrThrow().toHex()).isEqualTo(GENESIS_KEY);
    }

    private void verifyTreasuryCloneAccount(final Account account) {
        assertBasicAccount(account, 0, EXPECTED_ENTITY_EXPIRY);
        assertThat(account.keyOrThrow().ed25519OrThrow().toHex()).isEqualTo(GENESIS_KEY);
        Assertions.assertThat(account.autoRenewSeconds()).isEqualTo(EXPECTED_ENTITY_EXPIRY);
    }

    private void assertBasicAccount(Account account, long balance, long expiry) {
        assertThat(account).isNotNull();
        assertThat(account.tinybarBalance()).isEqualTo(balance);
        assertThat(account.alias()).isEqualTo(Bytes.EMPTY);
        assertThat(account.expirationSecond()).isEqualTo(expiry);
        assertThat(account.memo()).isEmpty();
        assertThat(account.deleted()).isFalse();
        assertThat(account.stakedToMe()).isZero();
        assertThat(account.stakePeriodStart()).isZero();
        assertThat(account.stakedId().kind()).isEqualTo(Account.StakedIdOneOfType.UNSET);
        assertThat(account.receiverSigRequired()).isFalse();
        assertThat(account.hasHeadNftId()).isFalse();
        assertThat(account.headNftSerialNumber()).isZero();
        assertThat(account.numberOwnedNfts()).isZero();
        assertThat(account.maxAutoAssociations()).isZero();
        assertThat(account.usedAutoAssociations()).isZero();
        assertThat(account.declineReward()).isTrue();
        assertThat(account.numberAssociations()).isZero();
        assertThat(account.smartContract()).isFalse();
        assertThat(account.numberPositiveBalances()).isZero();
        assertThat(account.ethereumNonce()).isZero();
        assertThat(account.stakeAtStartOfLastRewardedPeriod()).isZero();
        assertThat(account.hasAutoRenewAccountId()).isFalse();
        assertThat(account.contractKvPairsNumber()).isZero();
        assertThat(account.cryptoAllowances()).isEmpty();
        assertThat(account.approveForAllNftAllowances()).isEmpty();
        assertThat(account.tokenAllowances()).isEmpty();
        assertThat(account.numberTreasuryTitles()).isZero();
        assertThat(account.expiredAndPendingRemoval()).isFalse();
        assertThat(account.firstContractStorageKey()).isEqualTo(Bytes.EMPTY);
    }

    private AccountID accountID(int num) {
        return AccountID.newBuilder().accountNum(num).build();
    }

    /**
     * Compares the given account (already assumed to be correct) to the given crypto create
     * transaction body builder
     */
    private void verifyCryptoCreateBuilder(
            final Account acctResult, final CryptoCreateTransactionBody.Builder builderSubject) {
        Assertions.assertThat(builderSubject).isNotNull();
        Assertions.assertThat(builderSubject.build())
                .isEqualTo(CryptoCreateTransactionBody.newBuilder()
                        .key(acctResult.key())
                        .memo(acctResult.memo())
                        .declineReward(acctResult.declineReward())
                        .receiverSigRequired(acctResult.receiverSigRequired())
                        .initialBalance(acctResult.tinybarBalance())
                        .autoRenewPeriod(Duration.newBuilder()
                                .seconds(acctResult.autoRenewSeconds())
                                .build())
                        .alias(acctResult.alias())
                        .build());
    }

    private void putNewAccount(final long num, final HashMap<AccountID, Account> accts) {
        final var acctId = AccountID.newBuilder().accountNum(num).build();
        final var balance = num == TREASURY_ACCOUNT_NUM ? EXPECTED_TREASURY_TINYBARS_BALANCE : 0L;
        final var acct =
                Account.newBuilder().accountId(acctId).tinybarBalance(balance).build();
        accts.put(acctId, acct);
    }

    private Configuration buildConfig(final int numSystemAccounts, final boolean blocklistEnabled) {
        return HederaTestConfigBuilder.create()
                // Accounts Config
                .withValue("accounts.treasury", TREASURY_ACCOUNT_NUM)
                .withValue("accounts.stakingRewardAccount", 800L)
                .withValue("accounts.nodeRewardAccount", 801L)
                .withValue("accounts.blocklist.enabled", blocklistEnabled)
                .withValue("accounts.blocklist.path", "blocklist-parsing/test-evm-addresses-blocklist.csv")
                // Bootstrap Config
                .withValue("bootstrap.genesisPublicKey", "0x" + GENESIS_KEY)
                .withValue("bootstrap.system.entityExpiry", EXPECTED_ENTITY_EXPIRY)
                // Hedera Config
                .withValue("hedera.realm", 0L)
                .withValue("hedera.shard", 0L)
                // Ledger Config
                .withValue("ledger.numSystemAccounts", numSystemAccounts)
                .withValue("ledger.numReservedSystemEntities", NUM_RESERVED_SYSTEM_ENTITIES)
                .withValue("ledger.totalTinyBarFloat", EXPECTED_TREASURY_TINYBARS_BALANCE)
                .getOrCreateConfig();
    }

    private WritableSingletonState<EntityNumber> newWritableEntityIdState() {
        return new WritableSingletonStateBase<>(
                EntityIdService.ENTITY_ID_STATE_KEY, () -> new EntityNumber(BEGINNING_ENTITY_ID), c -> {});
    }

    private MapWritableStates newStatesInstance(
            final MapWritableKVState<AccountID, Account> accts,
            final MapWritableKVState<Bytes, AccountID> aliases,
            final WritableSingletonState<EntityNumber> entityIdState) {
        return MapWritableStates.builder()
                .state(accts)
                .state(aliases)
                .state(MapWritableKVState.builder(TokenServiceImpl.STAKING_INFO_KEY)
                        .build())
                .state(new WritableSingletonStateBase<>(
                        TokenServiceImpl.STAKING_NETWORK_REWARDS_KEY, () -> null, c -> {}))
                .state(entityIdState)
                .build();
    }

    /**
     * @return true if the given account number is NOT a staking account number or system contract
     */
    private boolean isRegularAcctNum(final long i) {
        // Skip the staking account nums
        if (Arrays.contains(new long[] {800, 801}, i)) return false;
        // Skip the system contract account nums
        return i < 350 || i > 399;
    }
}
