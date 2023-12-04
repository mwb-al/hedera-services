/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.leaky;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.node.app.hapi.utils.CommonUtils.asEvmAddress;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asTokenString;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountDetailsWith;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.SECP256K1;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ED25519_ON;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.keys.SigControl.SECP256K1_ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCustomCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumContractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uncheckedSubmit;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadSingleInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddressArray;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedHbarFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedHtsFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fractionalFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.royaltyFeeWithFallbackInHbarsInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.royaltyFeeWithFallbackInTokenInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.royaltyFeeWithoutFallbackInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.accountAmount;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.accountAmountAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.emptyChildRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingThree;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.resetToDefault;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.tokenTransferList;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.tokenTransferLists;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.Utils.eventSignatureOf;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.Utils.mirrorAddrWith;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.ACCOUNT_INFO;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.ACCOUNT_INFO_AFTER_CALL;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.CALL_TX;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.CALL_TX_REC;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.CONTRACTS_MAX_GAS_PER_SEC;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.CONTRACT_FROM;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.DEFAULT_MAX_AUTO_RENEW_PERIOD;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.DEPOSIT;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.PAY_RECEIVABLE_CONTRACT;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.RECEIVER_2;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.SIMPLE_UPDATE_CONTRACT;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.TRANSFERRING_CONTRACT;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.TRANSFER_TO_CALLER;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCreateSuite.EMPTY_CONSTRUCTOR_CONTRACT;
import static com.hedera.services.bdd.suites.contract.precompile.ApproveAllowanceSuite.ATTACK_CALL;
import static com.hedera.services.bdd.suites.contract.precompile.ApproveAllowanceSuite.CALL_TO;
import static com.hedera.services.bdd.suites.contract.precompile.ApproveAllowanceSuite.CONTRACTS_PERMITTED_DELEGATE_CALLERS;
import static com.hedera.services.bdd.suites.contract.precompile.ApproveAllowanceSuite.DELEGATE_ERC_CALLEE;
import static com.hedera.services.bdd.suites.contract.precompile.ApproveAllowanceSuite.DELEGATE_PRECOMPILE_CALLEE;
import static com.hedera.services.bdd.suites.contract.precompile.ApproveAllowanceSuite.PRETEND_ATTACKER;
import static com.hedera.services.bdd.suites.contract.precompile.ApproveAllowanceSuite.PRETEND_PAIR;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.ACCOUNT_2;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.AUTO_RENEW_PERIOD;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.CONTRACT_ADMIN_KEY;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.CREATE_TOKEN_WITH_ALL_CUSTOM_FEES_AVAILABLE;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.DEFAULT_AMOUNT_TO_SEND;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.ECDSA_KEY;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.ED25519KEY;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.EXISTING_TOKEN;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.EXPLICIT_CREATE_RESULT;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.FIRST_CREATE_TXN;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.MEMO;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.TOKEN_CREATE_CONTRACT;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.TOKEN_CREATE_CONTRACT_AS_KEY;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.TOKEN_NAME;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.TOKEN_SYMBOL;
import static com.hedera.services.bdd.suites.contract.precompile.CryptoTransferHTSSuite.TOTAL_SUPPLY;
import static com.hedera.services.bdd.suites.contract.precompile.CryptoTransferHTSSuite.TRANSFER_MULTIPLE_TOKENS;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.APPROVE;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.ERC_20_CONTRACT;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.RECIPIENT;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.TRANSFER;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.TRANSFER_FROM;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.TRANSFER_FROM_ACCOUNT_TXN;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.TRANSFER_SIGNATURE;
import static com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite.TRANSFER_SIG_NAME;
import static com.hedera.services.bdd.suites.contract.precompile.LazyCreateThroughPrecompileSuite.FIRST_META;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.ADMIN_KEY;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.FUNGIBLE_TOKEN;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.NON_FUNGIBLE_TOKEN;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.OWNER;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.ACCOUNT;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.LAZY_CREATION_ENABLED;
import static com.hedera.services.bdd.suites.ethereum.EthereumSuite.GAS_LIMIT;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.KNOWABLE_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.MULTI_KEY;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.SUPPLY_KEY;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.TRANSFER_TXN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.RECEIVER;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.SENDER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.hyperledger.besu.datatypes.Address.contractAddress;
import static org.hyperledger.besu.datatypes.Address.fromHexString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.hapi.utils.fee.FeeBuilder;
import com.hedera.services.bdd.junit.HapiTests;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.assertions.NonFungibleTransfers;
import com.hedera.services.bdd.spec.assertions.SomeFungibleTransfers;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenPauseStatus;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.utility.CommonUtils;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

@HapiTestSuite
@SuppressWarnings("java:S1192") // "string literal should not be duplicated" - this rule makes test suites worse
public class LeakyContractTestsSuite extends HapiSuite {
    public static final String CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT1 = "contracts.maxRefundPercentOfGasLimit";
    public static final String CREATE_TX = "createTX";
    public static final String CREATE_TX_REC = "createTXRec";
    public static final String FALSE = "false";
    public static final int GAS_TO_OFFER = 1_000_000;
    private static final Logger log = LogManager.getLogger(LeakyContractTestsSuite.class);
    private static final String PAYER = "payer";
    private static final String CONTRACTS_NONCES_EXTERNALIZATION_ENABLED = "contracts.nonces.externalization.enabled";
    private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE =
            KeyShape.threshOf(1, KeyShape.SIMPLE, DELEGATE_CONTRACT);
    private static final String CONTRACT_ALLOW_ASSOCIATIONS_PROPERTY = "contracts.allowAutoAssociations";
    private static final String TRANSFER_CONTRACT = "NonDelegateCryptoTransfer";
    private static final String CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS = "contracts.allowSystemUseOfHapiSigs";
    private static final String CRYPTO_TRANSFER = "CryptoTransfer";
    private static final String TOKEN_TRANSFER_CONTRACT = "TokenTransferContract";
    private static final String TRANSFER_TOKEN_PUBLIC = "transferTokenPublic";
    private static final String HEDERA_ALLOWANCES_IS_ENABLED = "hedera.allowances.isEnabled";

    public static void main(String... args) {
        new LeakyContractTestsSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                transferToCaller(),
                resultSizeAffectsFees(),
                payerCannotOverSendValue(),
                propagatesNestedCreations(),
                temporarySStoreRefundTest(),
                transferZeroHbarsToCaller(),
                canCallPendingContractSafely(),
                deletedContractsCannotBeUpdated(),
                createTokenWithInvalidRoyaltyFee(),
                autoAssociationSlotsAppearsInInfo(),
                createTokenWithInvalidFeeCollector(),
                fungibleTokenCreateWithFeesHappyPath(),
                gasLimitOverMaxGasLimitFailsPrecheck(),
                nonFungibleTokenCreateWithFeesHappyPath(),
                createMinChargeIsTXGasUsedByContractCreate(),
                createGasLimitOverMaxGasLimitFailsPrecheck(),
                contractCreationStoragePriceMatchesFinalExpiry(),
                createTokenWithInvalidFixedFeeWithERC721Denomination(),
                maxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller(),
                etx026AccountWithoutAliasCanMakeEthTxnsDueToAutomaticAliasCreation(),
                createMaxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller(),
                lazyCreateThroughPrecompileNotSupportedWhenFlagDisabled(),
                evmLazyCreateViaSolidityCall(),
                evmLazyCreateViaSolidityCallTooManyCreatesFails(),
                erc20TransferFromDoesNotWorkIfFlagIsDisabled(),
                rejectsCreationAndUpdateOfAssociationsWhenFlagDisabled(),
                whitelistPositiveCase(),
                whitelistNegativeCases(),
                requiresTopLevelSignatureOrApprovalDependingOnControllingProperty(),
                transferWorksWithTopLevelSignatures(),
                transferFailsWithIncorrectAmounts(),
                transferDontWorkWithoutTopLevelSignatures(),
                transferErc20TokenFromContractWithApproval(),
                transferErc20TokenFromErc721TokenFails(),
                contractCreateNoncesExternalizationHappyPath(),
                contractCreateFollowedByContractCallNoncesExternalization(),
                shouldReturnNullWhenContractsNoncesExternalizationFlagIsDisabled());
    }

    @HapiTests
    private HapiSpec transferErc20TokenFromErc721TokenFails() {
        return propertyPreservingHapiSpec("transferErc20TokenFromErc721TokenFails")
                .preserving(HEDERA_ALLOWANCES_IS_ENABLED)
                .given(
                        overriding(HEDERA_ALLOWANCES_IS_ENABLED, "true"),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_MILLION_HBARS),
                        cryptoCreate(RECIPIENT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
                        tokenAssociate(ACCOUNT, List.of(NON_FUNGIBLE_TOKEN)),
                        tokenAssociate(RECIPIENT, List.of(NON_FUNGIBLE_TOKEN)),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1).between(TOKEN_TREASURY, ACCOUNT))
                                .payingWith(ACCOUNT),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_20_CONTRACT,
                                        TRANSFER,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECIPIENT))),
                                        BigInteger.TWO)
                                .payingWith(ACCOUNT)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .via(TRANSFER_TXN)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(getTxnRecord(TRANSFER_TXN).andAllChildRecords().logged());
    }

    @HapiTests
    private HapiSpec transferErc20TokenFromContractWithApproval() {
        final var transferFromOtherContractWithSignaturesTxn = "transferFromOtherContractWithSignaturesTxn";
        final var nestedContract = "NestedERC20Contract";

        return propertyPreservingHapiSpec("TransferErc20TokenFromContractWithApproval")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS)
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(10 * ONE_MILLION_HBARS),
                        cryptoCreate(RECIPIENT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(35)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_20_CONTRACT, nestedContract),
                        newKeyNamed(TRANSFER_SIG_NAME).shape(SIMPLE.signedWith(ON)),
                        contractCreate(ERC_20_CONTRACT).adminKey(TRANSFER_SIG_NAME),
                        contractCreate(nestedContract).adminKey(TRANSFER_SIG_NAME),
                        overriding(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CRYPTO_TRANSFER))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(RECIPIENT, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(ERC_20_CONTRACT, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(nestedContract, List.of(FUNGIBLE_TOKEN)),
                        cryptoTransfer(TokenMovement.moving(20, FUNGIBLE_TOKEN)
                                        .between(TOKEN_TREASURY, ERC_20_CONTRACT))
                                .payingWith(ACCOUNT),
                        contractCall(
                                        ERC_20_CONTRACT,
                                        APPROVE,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(ERC_20_CONTRACT))),
                                        BigInteger.valueOf(20))
                                .gas(1_000_000)
                                .payingWith(ACCOUNT)
                                .alsoSigningWithFullPrefix(TRANSFER_SIG_NAME),
                        contractCall(
                                        ERC_20_CONTRACT,
                                        TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(ERC_20_CONTRACT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(nestedContract))),
                                        BigInteger.valueOf(5))
                                .via(TRANSFER_TXN)
                                .alsoSigningWithFullPrefix(TRANSFER_SIG_NAME)
                                .hasKnownStatus(SUCCESS),
                        contractCall(
                                        ERC_20_CONTRACT,
                                        TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(ERC_20_CONTRACT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(nestedContract))),
                                        BigInteger.valueOf(5))
                                .payingWith(ACCOUNT)
                                .alsoSigningWithFullPrefix(TRANSFER_SIG_NAME)
                                .via(transferFromOtherContractWithSignaturesTxn))))
                .then(
                        getContractInfo(ERC_20_CONTRACT).saveToRegistry(ERC_20_CONTRACT),
                        getContractInfo(nestedContract).saveToRegistry(nestedContract),
                        withOpContext((spec, log) -> {
                            final var sender = spec.registry()
                                    .getContractInfo(ERC_20_CONTRACT)
                                    .getContractID();
                            final var receiver = spec.registry()
                                    .getContractInfo(nestedContract)
                                    .getContractID();

                            var transferRecord = getTxnRecord(TRANSFER_TXN)
                                    .hasPriority(recordWith()
                                            .contractCallResult(resultWith()
                                                    .logs(inOrder(logWith()
                                                            .withTopicsInOrder(List.of(
                                                                    eventSignatureOf(TRANSFER_SIGNATURE),
                                                                    parsedToByteString(sender.getContractNum()),
                                                                    parsedToByteString(receiver.getContractNum())))
                                                            .longValue(5)))))
                                    .andAllChildRecords();

                            var transferFromOtherContractWithSignaturesTxnRecord = getTxnRecord(
                                            transferFromOtherContractWithSignaturesTxn)
                                    .hasPriority(recordWith()
                                            .contractCallResult(resultWith()
                                                    .logs(inOrder(logWith()
                                                            .withTopicsInOrder(List.of(
                                                                    eventSignatureOf(TRANSFER_SIGNATURE),
                                                                    parsedToByteString(sender.getContractNum()),
                                                                    parsedToByteString(receiver.getContractNum())))
                                                            .longValue(5)))))
                                    .andAllChildRecords();

                            allRunFor(spec, transferRecord, transferFromOtherContractWithSignaturesTxnRecord);
                        }),
                        childRecordsCheck(
                                TRANSFER_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_TRANSFER)
                                                        .withErcFungibleTransferStatus(true)))),
                        childRecordsCheck(
                                transferFromOtherContractWithSignaturesTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_TRANSFER)
                                                        .withErcFungibleTransferStatus(true)))),
                        getAccountBalance(ERC_20_CONTRACT).hasTokenBalance(FUNGIBLE_TOKEN, 10),
                        getAccountBalance(nestedContract).hasTokenBalance(FUNGIBLE_TOKEN, 10));
    }

    @HapiTests
    private HapiSpec transferDontWorkWithoutTopLevelSignatures() {
        final var transferTokenTxn = "transferTokenTxn";
        final var transferTokensTxn = "transferTokensTxn";
        final var transferNFTTxn = "transferNFTTxn";
        final var transferNFTsTxn = "transferNFTsTxn";
        final var contract = TOKEN_TRANSFER_CONTRACT;

        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaNftID = new AtomicReference<>();
        return propertyPreservingHapiSpec("transferDontWorkWithoutTopLevelSignatures")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS)
                .given(
                        // disable top level signatures for all functions
                        overriding(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, ""),
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(RECEIVER_2),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        tokenCreate(KNOWABLE_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(id -> vanillaNftID.set(asToken(id))),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN, KNOWABLE_TOKEN),
                        tokenAssociate(RECEIVER, VANILLA_TOKEN, KNOWABLE_TOKEN),
                        tokenAssociate(RECEIVER_2, VANILLA_TOKEN, KNOWABLE_TOKEN),
                        mintToken(
                                KNOWABLE_TOKEN,
                                List.of(
                                        copyFromUtf8("dark"),
                                        copyFromUtf8("matter"),
                                        copyFromUtf8("dark1"),
                                        copyFromUtf8("matter1"))),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)),
                        cryptoTransfer(movingUnique(KNOWABLE_TOKEN, 1, 2, 3, 4).between(TOKEN_TREASURY, ACCOUNT)),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when(
                        // Do transfers by calling contract from EOA, and should be failing with
                        // CONTRACT_REVERT_EXECUTED
                        withOpContext((spec, opLog) -> {
                            final var receiver1 =
                                    asHeadlongAddress(asAddress(spec.registry().getAccountID(RECEIVER)));
                            final var receiver2 =
                                    asHeadlongAddress(asAddress(spec.registry().getAccountID(RECEIVER_2)));
                            final var sender =
                                    asHeadlongAddress(asAddress(spec.registry().getAccountID(ACCOUNT)));
                            final var amount = 5L;

                            final var accounts = new Address[] {sender, receiver1, receiver2};
                            final var amounts = new long[] {-10L, 5L, 5L};
                            final var serials = new long[] {2L, 3L};
                            final var serial = 1L;
                            allRunFor(
                                    spec,
                                    contractCall(
                                                    contract,
                                                    TRANSFER_TOKEN_PUBLIC,
                                                    HapiParserUtil.asHeadlongAddress(asAddress(
                                                            spec.registry().getTokenID(VANILLA_TOKEN))),
                                                    sender,
                                                    receiver1,
                                                    amount)
                                            .payingWith(ACCOUNT)
                                            .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                            .gas(GAS_TO_OFFER)
                                            .via(transferTokenTxn),
                                    contractCall(
                                                    contract,
                                                    "transferTokensPublic",
                                                    HapiParserUtil.asHeadlongAddress(asAddress(
                                                            spec.registry().getTokenID(VANILLA_TOKEN))),
                                                    accounts,
                                                    amounts)
                                            .payingWith(ACCOUNT)
                                            .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                            .gas(GAS_TO_OFFER)
                                            .via(transferTokensTxn),
                                    contractCall(
                                                    contract,
                                                    "transferNFTPublic",
                                                    HapiParserUtil.asHeadlongAddress(asAddress(
                                                            spec.registry().getTokenID(KNOWABLE_TOKEN))),
                                                    sender,
                                                    receiver1,
                                                    serial)
                                            .payingWith(ACCOUNT)
                                            .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                            .gas(GAS_TO_OFFER)
                                            .via(transferNFTTxn),
                                    contractCall(
                                                    contract,
                                                    "transferNFTsPublic",
                                                    HapiParserUtil.asHeadlongAddress(asAddress(
                                                            spec.registry().getTokenID(KNOWABLE_TOKEN))),
                                                    new Address[] {sender, sender},
                                                    new Address[] {receiver2, receiver2},
                                                    serials)
                                            .payingWith(ACCOUNT)
                                            .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                            .gas(GAS_TO_OFFER)
                                            .via(transferNFTsTxn));
                        }))
                .then(
                        // Confirm the transactions fails with no top level signatures enabled
                        childRecordsCheck(
                                transferTokenTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(SPENDER_DOES_NOT_HAVE_ALLOWANCE)),
                        childRecordsCheck(
                                transferTokensTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(SPENDER_DOES_NOT_HAVE_ALLOWANCE)),
                        childRecordsCheck(
                                transferNFTTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(SPENDER_DOES_NOT_HAVE_ALLOWANCE)),
                        childRecordsCheck(
                                transferNFTsTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(SPENDER_DOES_NOT_HAVE_ALLOWANCE)),
                        // Confirm the balances are correct
                        getAccountInfo(RECEIVER).hasOwnedNfts(0),
                        getAccountBalance(RECEIVER).hasTokenBalance(VANILLA_TOKEN, 0),
                        getAccountInfo(RECEIVER_2).hasOwnedNfts(0),
                        getAccountBalance(RECEIVER_2).hasTokenBalance(VANILLA_TOKEN, 0),
                        getAccountInfo(ACCOUNT).hasOwnedNfts(4),
                        getAccountBalance(ACCOUNT).hasTokenBalance(VANILLA_TOKEN, 500L));
    }

    private HapiSpec transferWorksWithTopLevelSignatures() {
        final var transferTokenTxn = "transferTokenTxn";
        final var transferTokensTxn = "transferTokensTxn";
        final var transferNFTTxn = "transferNFTTxn";
        final var transferNFTsTxn = "transferNFTsTxn";
        final var contract = TOKEN_TRANSFER_CONTRACT;

        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaNftID = new AtomicReference<>();
        return propertyPreservingHapiSpec("transferWorksWithTopLevelSignatures")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        // enable top level signatures for
                        // transferToken/transferTokens/transferNft/transferNfts
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                CRYPTO_TRANSFER,
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                "10_000_000"),
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(RECEIVER_2),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        tokenCreate(KNOWABLE_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(id -> vanillaNftID.set(asToken(id))),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN, KNOWABLE_TOKEN),
                        tokenAssociate(RECEIVER, VANILLA_TOKEN, KNOWABLE_TOKEN),
                        tokenAssociate(RECEIVER_2, VANILLA_TOKEN, KNOWABLE_TOKEN),
                        mintToken(
                                KNOWABLE_TOKEN,
                                List.of(
                                        copyFromUtf8("dark"),
                                        copyFromUtf8("matter"),
                                        copyFromUtf8("dark1"),
                                        copyFromUtf8("matter1"))),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)),
                        cryptoTransfer(movingUnique(KNOWABLE_TOKEN, 1, 2, 3, 4).between(TOKEN_TREASURY, ACCOUNT)),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when(
                        // Do transfers by calling contract from EOA
                        withOpContext((spec, opLog) -> {
                            final var receiver1 =
                                    asHeadlongAddress(asAddress(spec.registry().getAccountID(RECEIVER)));
                            final var receiver2 =
                                    asHeadlongAddress(asAddress(spec.registry().getAccountID(RECEIVER_2)));
                            final var sender =
                                    asHeadlongAddress(asAddress(spec.registry().getAccountID(ACCOUNT)));
                            final var amount = 5L;

                            final var accounts = new Address[] {sender, receiver1, receiver2};
                            final var amounts = new long[] {-10L, 5L, 5L};
                            final var serials = new long[] {2L, 3L};
                            final var serial = 1L;
                            allRunFor(
                                    spec,
                                    contractCall(
                                                    contract,
                                                    TRANSFER_TOKEN_PUBLIC,
                                                    HapiParserUtil.asHeadlongAddress(asAddress(
                                                            spec.registry().getTokenID(VANILLA_TOKEN))),
                                                    sender,
                                                    receiver1,
                                                    amount)
                                            .payingWith(ACCOUNT)
                                            .gas(GAS_TO_OFFER)
                                            .via(transferTokenTxn),
                                    contractCall(
                                                    contract,
                                                    "transferTokensPublic",
                                                    HapiParserUtil.asHeadlongAddress(asAddress(
                                                            spec.registry().getTokenID(VANILLA_TOKEN))),
                                                    accounts,
                                                    amounts)
                                            .payingWith(ACCOUNT)
                                            .gas(GAS_TO_OFFER)
                                            .via(transferTokensTxn),
                                    contractCall(
                                                    contract,
                                                    "transferNFTPublic",
                                                    HapiParserUtil.asHeadlongAddress(asAddress(
                                                            spec.registry().getTokenID(KNOWABLE_TOKEN))),
                                                    sender,
                                                    receiver1,
                                                    serial)
                                            .payingWith(ACCOUNT)
                                            .gas(GAS_TO_OFFER)
                                            .via(transferNFTTxn),
                                    contractCall(
                                                    contract,
                                                    "transferNFTsPublic",
                                                    HapiParserUtil.asHeadlongAddress(asAddress(
                                                            spec.registry().getTokenID(KNOWABLE_TOKEN))),
                                                    new Address[] {sender, sender},
                                                    new Address[] {receiver2, receiver2},
                                                    serials)
                                            .payingWith(ACCOUNT)
                                            .gas(GAS_TO_OFFER)
                                            .via(transferNFTsTxn));
                        }))
                .then(
                        // Confirm the transactions succeeded
                        getTxnRecord(transferTokenTxn).logged(),
                        childRecordsCheck(
                                transferTokenTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .tokenTransfers(SomeFungibleTransfers.changingFungibleBalances()
                                                .including(VANILLA_TOKEN, ACCOUNT, -5L)
                                                .including(VANILLA_TOKEN, RECEIVER, 5L))),
                        childRecordsCheck(
                                transferTokensTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .tokenTransfers(SomeFungibleTransfers.changingFungibleBalances()
                                                .including(VANILLA_TOKEN, ACCOUNT, -10L)
                                                .including(VANILLA_TOKEN, RECEIVER, 5L)
                                                .including(VANILLA_TOKEN, RECEIVER_2, 5L))),
                        childRecordsCheck(
                                transferNFTTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))
                                        .tokenTransfers(NonFungibleTransfers.changingNFTBalances()
                                                .including(KNOWABLE_TOKEN, ACCOUNT, RECEIVER, 1L))),
                        childRecordsCheck(
                                transferNFTsTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))
                                        .tokenTransfers(NonFungibleTransfers.changingNFTBalances()
                                                .including(KNOWABLE_TOKEN, ACCOUNT, RECEIVER_2, 2L)
                                                .including(KNOWABLE_TOKEN, ACCOUNT, RECEIVER_2, 3L))),
                        // Confirm the balances are correct
                        getAccountInfo(RECEIVER).hasOwnedNfts(1),
                        getAccountBalance(RECEIVER).hasTokenBalance(VANILLA_TOKEN, 10L),
                        getAccountInfo(RECEIVER_2).hasOwnedNfts(2),
                        getAccountBalance(RECEIVER_2).hasTokenBalance(VANILLA_TOKEN, 5L),
                        getAccountInfo(ACCOUNT).hasOwnedNfts(1),
                        getAccountBalance(ACCOUNT).hasTokenBalance(VANILLA_TOKEN, 485L));
    }

    private HapiSpec transferFailsWithIncorrectAmounts() {
        final var transferTokenWithNegativeAmountTxn = "transferTokenWithNegativeAmountTxn";
        final var contract = TOKEN_TRANSFER_CONTRACT;

        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        return propertyPreservingHapiSpec("transferFailsWithIncorrectAmounts")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS)
                .given(
                        overriding(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CRYPTO_TRANSFER),
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(RECEIVER),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        tokenAssociate(RECEIVER, VANILLA_TOKEN),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when(withOpContext((spec, opLog) -> {
                    final var receiver1 =
                            asHeadlongAddress(asAddress(spec.registry().getAccountID(RECEIVER)));
                    final var sender =
                            asHeadlongAddress(asAddress(spec.registry().getAccountID(ACCOUNT)));

                    allRunFor(
                            spec,
                            // Call tokenTransfer with a negative amount
                            contractCall(
                                            contract,
                                            TRANSFER_TOKEN_PUBLIC,
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(VANILLA_TOKEN))),
                                            sender,
                                            receiver1,
                                            -1L)
                                    .payingWith(ACCOUNT)
                                    .gas(GAS_TO_OFFER)
                                    .via(transferTokenWithNegativeAmountTxn)
                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED));
                }))
                .then(
                        // Confirm the transactions succeeded
                        childRecordsCheck(transferTokenWithNegativeAmountTxn, CONTRACT_REVERT_EXECUTED));
    }

    HapiSpec payerCannotOverSendValue() {
        final var payerBalance = 666 * ONE_HBAR;
        final var overdraftAmount = payerBalance + ONE_HBAR;
        final var overAmbitiousPayer = "overAmbitiousPayer";
        final var uncheckedCC = "uncheckedCC";
        return defaultHapiSpec("PayerCannotOverSendValue")
                .given(
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD))
                .when(
                        cryptoCreate(overAmbitiousPayer).balance(payerBalance),
                        contractCall(PAY_RECEIVABLE_CONTRACT, DEPOSIT, BigInteger.valueOf(overdraftAmount))
                                .payingWith(overAmbitiousPayer)
                                .sending(overdraftAmount)
                                .hasPrecheck(INSUFFICIENT_PAYER_BALANCE),
                        usableTxnIdNamed(uncheckedCC).payerId(overAmbitiousPayer),
                        uncheckedSubmit(contractCall(
                                                PAY_RECEIVABLE_CONTRACT, DEPOSIT, BigInteger.valueOf(overdraftAmount))
                                        .txnId(uncheckedCC)
                                        .payingWith(overAmbitiousPayer)
                                        .sending(overdraftAmount))
                                .payingWith(GENESIS))
                .then(
                        sleepFor(1_000),
                        getReceipt(uncheckedCC)
                                .hasPriorityStatus(INSUFFICIENT_PAYER_BALANCE)
                                .logged());
    }

    private HapiSpec createTokenWithInvalidFeeCollector() {
        return propertyPreservingHapiSpec("createTokenWithInvalidFeeCollector")
                .preserving(CRYPTO_CREATE_WITH_ALIAS_ENABLED, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CRYPTO_CREATE_WITH_ALIAS_ENABLED, FALSE_VALUE),
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ECDSA_KEY),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER),
                        tokenCreate(EXISTING_TOKEN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_CREATE_CONTRACT,
                                        CREATE_TOKEN_WITH_ALL_CUSTOM_FEES_AVAILABLE,
                                        spec.registry()
                                                .getKey(ECDSA_KEY)
                                                .getECDSASecp256K1()
                                                .toByteArray(),
                                        HapiParserUtil.asHeadlongAddress(
                                                (byte[]) ArrayUtils.toPrimitive(Utils.asSolidityAddress(0, 0, 15252L))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(EXISTING_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        AUTO_RENEW_PERIOD)
                                .via(FIRST_CREATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .refusingEthConversion()
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged(),
                        getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
                        getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith()
                                        .status(INVALID_CUSTOM_FEE_COLLECTOR)
                                        .contractCallResult(ContractFnResultAsserts.resultWith()
                                                .error(INVALID_CUSTOM_FEE_COLLECTOR.name()))));
    }

    private HapiSpec createTokenWithInvalidFixedFeeWithERC721Denomination() {
        final String feeCollector = ACCOUNT_2;
        final String someARAccount = "someARAccount";
        return propertyPreservingHapiSpec("createTokenWithInvalidFixedFeeWithERC721Denomination")
                .preserving(CRYPTO_CREATE_WITH_ALIAS_ENABLED, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CRYPTO_CREATE_WITH_ALIAS_ENABLED, FALSE_VALUE),
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ECDSA_KEY),
                        cryptoCreate(feeCollector).keyShape(ED25519_ON).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(someARAccount).keyShape(ED25519_ON).balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER),
                        tokenCreate(EXISTING_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(ECDSA_KEY)
                                .initialSupply(0L))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_CREATE_CONTRACT,
                                        CREATE_TOKEN_WITH_ALL_CUSTOM_FEES_AVAILABLE,
                                        spec.registry()
                                                .getKey(ECDSA_KEY)
                                                .getECDSASecp256K1()
                                                .toByteArray(),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(feeCollector))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(EXISTING_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(someARAccount))),
                                        AUTO_RENEW_PERIOD)
                                .via(FIRST_CREATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .refusingEthConversion()
                                .alsoSigningWithFullPrefix(someARAccount, feeCollector)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged(),
                        getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
                        getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith()
                                        .status(CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON)
                                        .contractCallResult(ContractFnResultAsserts.resultWith()
                                                .error(CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON.name()))));
    }

    private HapiSpec createTokenWithInvalidRoyaltyFee() {
        final String feeCollector = ACCOUNT_2;
        AtomicReference<String> existingToken = new AtomicReference<>();
        final String treasuryAndFeeCollectorKey = "treasuryAndFeeCollectorKey";
        return propertyPreservingHapiSpec("createTokenWithInvalidRoyaltyFee")
                .preserving(CRYPTO_CREATE_WITH_ALIAS_ENABLED, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CRYPTO_CREATE_WITH_ALIAS_ENABLED, FALSE_VALUE),
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(CONTRACT_ADMIN_KEY),
                        newKeyNamed(treasuryAndFeeCollectorKey),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ECDSA_KEY),
                        cryptoCreate(feeCollector)
                                .key(treasuryAndFeeCollectorKey)
                                .balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER).adminKey(CONTRACT_ADMIN_KEY),
                        tokenCreate(EXISTING_TOKEN).exposingCreatedIdTo(existingToken::set))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_CREATE_CONTRACT,
                                        "createNonFungibleTokenWithInvalidRoyaltyFee",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(feeCollector))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(EXISTING_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        AUTO_RENEW_PERIOD,
                                        spec.registry()
                                                .getKey(ED25519KEY)
                                                .getEd25519()
                                                .toByteArray())
                                .via(FIRST_CREATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .signedBy(ECDSA_KEY, treasuryAndFeeCollectorKey)
                                .alsoSigningWithFullPrefix(ED25519KEY, treasuryAndFeeCollectorKey)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged(),
                        getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
                        getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith()
                                        .status(CUSTOM_FEE_MUST_BE_POSITIVE)
                                        .contractCallResult(ContractFnResultAsserts.resultWith()
                                                .error(CUSTOM_FEE_MUST_BE_POSITIVE.name()))));
    }

    private HapiSpec nonFungibleTokenCreateWithFeesHappyPath() {
        final var createTokenNum = new AtomicLong();
        final var feeCollector = ACCOUNT_2;
        final var treasuryAndFeeCollectorKey = "treasuryAndFeeCollectorKey";
        return propertyPreservingHapiSpec("nonFungibleTokenCreateWithFeesHappyPath")
                .preserving(CRYPTO_CREATE_WITH_ALIAS_ENABLED, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CRYPTO_CREATE_WITH_ALIAS_ENABLED, FALSE_VALUE),
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(treasuryAndFeeCollectorKey),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ECDSA_KEY),
                        cryptoCreate(feeCollector)
                                .key(treasuryAndFeeCollectorKey)
                                .balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER),
                        tokenCreate(EXISTING_TOKEN),
                        tokenAssociate(feeCollector, EXISTING_TOKEN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_CREATE_CONTRACT,
                                        "createNonFungibleTokenWithCustomFees",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(feeCollector))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(EXISTING_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        AUTO_RENEW_PERIOD,
                                        spec.registry()
                                                .getKey(ED25519KEY)
                                                .getEd25519()
                                                .toByteArray())
                                .via(FIRST_CREATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .signedBy(ECDSA_KEY, treasuryAndFeeCollectorKey)
                                .alsoSigningWithFullPrefix(ED25519KEY, treasuryAndFeeCollectorKey)
                                .exposingResultTo(result -> {
                                    log.info(EXPLICIT_CREATE_RESULT, result[0]);
                                    final var res = (Address) result[0];
                                    createTokenNum.set(res.value().longValueExact());
                                }),
                        newKeyNamed(TOKEN_CREATE_CONTRACT_AS_KEY).shape(CONTRACT.signedWith(TOKEN_CREATE_CONTRACT)))))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged(),
                        getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
                        getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                SUCCESS,
                                TransactionRecordAsserts.recordWith().status(SUCCESS)),
                        sourcing(() -> {
                            final var newToken = asTokenString(TokenID.newBuilder()
                                    .setTokenNum(createTokenNum.get())
                                    .build());
                            return getTokenInfo(newToken)
                                    .logged()
                                    .hasTokenType(NON_FUNGIBLE_UNIQUE)
                                    .hasSymbol(TOKEN_SYMBOL)
                                    .hasName(TOKEN_NAME)
                                    .hasDecimals(0)
                                    .hasTotalSupply(0)
                                    .hasEntityMemo(MEMO)
                                    .hasTreasury(feeCollector)
                                    .hasAutoRenewAccount(ACCOUNT)
                                    .hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
                                    .hasSupplyType(TokenSupplyType.FINITE)
                                    .hasMaxSupply(400)
                                    .searchKeysGlobally()
                                    .hasAdminKey(TOKEN_CREATE_CONTRACT_AS_KEY)
                                    .hasPauseStatus(TokenPauseStatus.PauseNotApplicable)
                                    .hasCustom(royaltyFeeWithFallbackInHbarsInSchedule(4, 5, 10, feeCollector))
                                    .hasCustom(royaltyFeeWithFallbackInTokenInSchedule(
                                            4, 5, 10, EXISTING_TOKEN, feeCollector))
                                    .hasCustom(royaltyFeeWithoutFallbackInSchedule(4, 5, feeCollector));
                        }));
    }

    private HapiSpec fungibleTokenCreateWithFeesHappyPath() {
        final var createdTokenNum = new AtomicLong();
        final var feeCollector = "feeCollector";
        final var arEd25519Key = "arEd25519Key";
        final var initialAutoRenewAccount = "initialAutoRenewAccount";
        return propertyPreservingHapiSpec("fungibleTokenCreateWithFeesHappyPath")
                .preserving(CRYPTO_CREATE_WITH_ALIAS_ENABLED, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CRYPTO_CREATE_WITH_ALIAS_ENABLED, FALSE_VALUE),
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        newKeyNamed(arEd25519Key).shape(ED25519),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        cryptoCreate(initialAutoRenewAccount).key(arEd25519Key),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ECDSA_KEY),
                        cryptoCreate(feeCollector).keyShape(ED25519_ON).balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER),
                        tokenCreate(EXISTING_TOKEN),
                        tokenAssociate(feeCollector, EXISTING_TOKEN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_CREATE_CONTRACT,
                                        CREATE_TOKEN_WITH_ALL_CUSTOM_FEES_AVAILABLE,
                                        spec.registry()
                                                .getKey(ECDSA_KEY)
                                                .getECDSASecp256K1()
                                                .toByteArray(),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(feeCollector))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(EXISTING_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(initialAutoRenewAccount))),
                                        AUTO_RENEW_PERIOD)
                                .via(FIRST_CREATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .refusingEthConversion()
                                .alsoSigningWithFullPrefix(arEd25519Key, feeCollector)
                                .exposingResultTo(result -> {
                                    log.info(EXPLICIT_CREATE_RESULT, result[0]);
                                    final var res = (Address) result[0];
                                    createdTokenNum.set(res.value().longValueExact());
                                }),
                        newKeyNamed(TOKEN_CREATE_CONTRACT_AS_KEY).shape(CONTRACT.signedWith(TOKEN_CREATE_CONTRACT)))))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged(),
                        getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
                        getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                SUCCESS,
                                TransactionRecordAsserts.recordWith().status(SUCCESS)),
                        sourcing(() -> {
                            final var newToken = asTokenString(TokenID.newBuilder()
                                    .setTokenNum(createdTokenNum.get())
                                    .build());
                            return getTokenInfo(newToken)
                                    .logged()
                                    .hasTokenType(FUNGIBLE_COMMON)
                                    .hasSymbol(TOKEN_SYMBOL)
                                    .hasName(TOKEN_NAME)
                                    .hasDecimals(8)
                                    .hasTotalSupply(200)
                                    .hasEntityMemo(MEMO)
                                    .hasTreasury(TOKEN_CREATE_CONTRACT)
                                    .hasAutoRenewAccount(initialAutoRenewAccount)
                                    .hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
                                    .hasSupplyType(TokenSupplyType.INFINITE)
                                    .searchKeysGlobally()
                                    .hasAdminKey(ECDSA_KEY)
                                    .hasPauseStatus(TokenPauseStatus.PauseNotApplicable)
                                    .hasCustom(fixedHtsFeeInSchedule(1, EXISTING_TOKEN, feeCollector))
                                    .hasCustom(fixedHbarFeeInSchedule(2, feeCollector))
                                    .hasCustom(fixedHtsFeeInSchedule(4, newToken, feeCollector))
                                    .hasCustom(
                                            fractionalFeeInSchedule(4, 5, 10, OptionalLong.of(30), true, feeCollector));
                        }));
    }

    private HapiSpec etx026AccountWithoutAliasCanMakeEthTxnsDueToAutomaticAliasCreation() {
        final String ACCOUNT = "account";
        return propertyPreservingHapiSpec("etx026AccountWithoutAliasCanMakeEthTxnsDueToAutomaticAliasCreation")
                .preserving(CRYPTO_CREATE_WITH_ALIAS_ENABLED, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CRYPTO_CREATE_WITH_ALIAS_ENABLED, FALSE_VALUE),
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(ACCOUNT).key(SECP_256K1_SOURCE_KEY).balance(ONE_HUNDRED_HBARS))
                .when(ethereumContractCreate(PAY_RECEIVABLE_CONTRACT)
                        .type(EthTxData.EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(ACCOUNT)
                        .maxGasAllowance(FIVE_HBARS)
                        .nonce(0)
                        .gasLimit(GAS_LIMIT)
                        .hasKnownStatus(INVALID_ACCOUNT_ID))
                .then(overriding(CRYPTO_CREATE_WITH_ALIAS_ENABLED, "true"));
    }

    @HapiTests
    private HapiSpec transferToCaller() {
        final var transferTxn = TRANSFER_TXN;
        final var sender = "sender";
        return defaultHapiSpec("transferToCaller")
                .given(
                        uploadInitCode(TRANSFERRING_CONTRACT),
                        contractCreate(TRANSFERRING_CONTRACT).balance(10_000L),
                        cryptoCreate(sender).balance(ONE_HUNDRED_HBARS),
                        getAccountInfo(sender).savingSnapshot(ACCOUNT_INFO).payingWith(GENESIS))
                .when(withOpContext((spec, log) -> {
                    var transferCall = contractCall(TRANSFERRING_CONTRACT, TRANSFER_TO_CALLER, BigInteger.valueOf(10))
                            .payingWith(sender)
                            .via(transferTxn)
                            .logged();

                    var saveTxnRecord = getTxnRecord(transferTxn)
                            .saveTxnRecordToRegistry("txn")
                            .payingWith(GENESIS);
                    var saveAccountInfoAfterCall = getAccountInfo(sender)
                            .savingSnapshot(ACCOUNT_INFO_AFTER_CALL)
                            .payingWith(GENESIS);
                    var saveContractInfo =
                            getContractInfo(TRANSFERRING_CONTRACT).saveToRegistry(CONTRACT_FROM);

                    allRunFor(spec, transferCall, saveTxnRecord, saveAccountInfoAfterCall, saveContractInfo);
                }))
                .then(
                        assertionsHold((spec, opLog) -> {
                            final var fee =
                                    spec.registry().getTransactionRecord("txn").getTransactionFee();
                            final var accountBalanceBeforeCall =
                                    spec.registry().getAccountInfo(ACCOUNT_INFO).getBalance();
                            final var accountBalanceAfterCall = spec.registry()
                                    .getAccountInfo(ACCOUNT_INFO_AFTER_CALL)
                                    .getBalance();
                            assertEquals(accountBalanceAfterCall, accountBalanceBeforeCall - fee + 10L);
                        }),
                        sourcing(() -> getContractInfo(TRANSFERRING_CONTRACT)
                                .has(contractWith().balance(10_000L - 10L))));
    }

    @HapiTests
    private HapiSpec maxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller() {
        return defaultHapiSpec("MaxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller")
                .given(
                        overriding(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT, "5"),
                        uploadInitCode(SIMPLE_UPDATE_CONTRACT))
                .when(
                        contractCreate(SIMPLE_UPDATE_CONTRACT).gas(300_000L),
                        contractCall(SIMPLE_UPDATE_CONTRACT, "set", BigInteger.valueOf(5), BigInteger.valueOf(42))
                                .gas(300_000L)
                                .via(CALL_TX))
                .then(
                        withOpContext((spec, ignore) -> {
                            final var subop01 = getTxnRecord(CALL_TX).saveTxnRecordToRegistry(CALL_TX_REC);
                            allRunFor(spec, subop01);

                            final var gasUsed = spec.registry()
                                    .getTransactionRecord(CALL_TX_REC)
                                    .getContractCallResult()
                                    .getGasUsed();
                            assertEquals(285000, gasUsed);
                        }),
                        resetToDefault(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT));
    }

    @SuppressWarnings("java:S5960")
    private HapiSpec contractCreationStoragePriceMatchesFinalExpiry() {
        final var toyMaker = "ToyMaker";
        final var createIndirectly = "CreateIndirectly";
        final var normalPayer = "normalPayer";
        final var longLivedPayer = "longLivedPayer";
        final var longLifetime = 100 * 7776000L;
        final AtomicLong normalPayerGasUsed = new AtomicLong();
        final AtomicLong longLivedPayerGasUsed = new AtomicLong();
        final AtomicReference<String> toyMakerMirror = new AtomicReference<>();

        return defaultHapiSpec("ContractCreationStoragePriceMatchesFinalExpiry")
                .given(
                        overriding(LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION, "" + longLifetime),
                        cryptoCreate(normalPayer),
                        cryptoCreate(longLivedPayer).autoRenewSecs(longLifetime),
                        uploadInitCode(toyMaker, createIndirectly),
                        contractCreate(toyMaker)
                                .exposingNumTo(num -> toyMakerMirror.set(asHexedSolidityAddress(0, 0, num))),
                        sourcing(() -> contractCreate(createIndirectly)
                                .autoRenewSecs(longLifetime)
                                .payingWith(GENESIS)))
                .when(
                        contractCall(toyMaker, "make")
                                .payingWith(normalPayer)
                                .exposingGasTo((status, gasUsed) -> normalPayerGasUsed.set(gasUsed)),
                        contractCall(toyMaker, "make")
                                .payingWith(longLivedPayer)
                                .exposingGasTo((status, gasUsed) -> longLivedPayerGasUsed.set(gasUsed)),
                        assertionsHold((spec, opLog) -> assertEquals(
                                normalPayerGasUsed.get(),
                                longLivedPayerGasUsed.get(),
                                "Payer expiry should not affect create storage" + " cost")),
                        // Verify that we are still charged a "typical" amount despite the payer and
                        // the original sender contract having extremely long expiry dates
                        sourcing(() -> contractCall(
                                        createIndirectly, "makeOpaquely", asHeadlongAddress(toyMakerMirror.get()))
                                .payingWith(longLivedPayer)))
                .then(overriding(LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION, DEFAULT_MAX_AUTO_RENEW_PERIOD));
    }

    @HapiTests
    private HapiSpec gasLimitOverMaxGasLimitFailsPrecheck() {
        return defaultHapiSpec("GasLimitOverMaxGasLimitFailsPrecheck")
                .given(
                        uploadInitCode(SIMPLE_UPDATE_CONTRACT),
                        contractCreate(SIMPLE_UPDATE_CONTRACT).gas(300_000L),
                        overriding(CONTRACTS_MAX_GAS_PER_SEC, "100"))
                .when()
                .then(
                        contractCall(SIMPLE_UPDATE_CONTRACT, "set", BigInteger.valueOf(5), BigInteger.valueOf(42))
                                .gas(21_000L)
                                .hasPrecheck(MAX_GAS_LIMIT_EXCEEDED),
                        resetToDefault(CONTRACTS_MAX_GAS_PER_SEC));
    }

    @HapiTests
    private HapiSpec createGasLimitOverMaxGasLimitFailsPrecheck() {
        return defaultHapiSpec("CreateGasLimitOverMaxGasLimitFailsPrecheck")
                .given(overriding("contracts.maxGasPerSec", "100"), uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .when()
                .then(
                        contractCreate(EMPTY_CONSTRUCTOR_CONTRACT).gas(101L).hasPrecheck(MAX_GAS_LIMIT_EXCEEDED),
                        UtilVerbs.resetToDefault("contracts.maxGasPerSec"));
    }

    @HapiTests
    private HapiSpec transferZeroHbarsToCaller() {
        final var transferTxn = TRANSFER_TXN;
        return defaultHapiSpec("transferZeroHbarsToCaller")
                .given(
                        uploadInitCode(TRANSFERRING_CONTRACT),
                        contractCreate(TRANSFERRING_CONTRACT).balance(10_000L),
                        getAccountInfo(DEFAULT_CONTRACT_SENDER)
                                .savingSnapshot(ACCOUNT_INFO)
                                .payingWith(GENESIS))
                .when(withOpContext((spec, log) -> {
                    var transferCall = contractCall(TRANSFERRING_CONTRACT, TRANSFER_TO_CALLER, BigInteger.ZERO)
                            .payingWith(DEFAULT_CONTRACT_SENDER)
                            .via(transferTxn)
                            .logged();

                    var saveTxnRecord = getTxnRecord(transferTxn)
                            .saveTxnRecordToRegistry("txn_registry")
                            .payingWith(GENESIS);
                    var saveAccountInfoAfterCall = getAccountInfo(DEFAULT_CONTRACT_SENDER)
                            .savingSnapshot(ACCOUNT_INFO_AFTER_CALL)
                            .payingWith(GENESIS);
                    var saveContractInfo =
                            getContractInfo(TRANSFERRING_CONTRACT).saveToRegistry(CONTRACT_FROM);

                    allRunFor(spec, transferCall, saveTxnRecord, saveAccountInfoAfterCall, saveContractInfo);
                }))
                .then(assertionsHold((spec, opLog) -> {
                    final var fee =
                            spec.registry().getTransactionRecord("txn_registry").getTransactionFee();
                    final var accountBalanceBeforeCall =
                            spec.registry().getAccountInfo(ACCOUNT_INFO).getBalance();
                    final var accountBalanceAfterCall = spec.registry()
                            .getAccountInfo(ACCOUNT_INFO_AFTER_CALL)
                            .getBalance();
                    final var contractBalanceAfterCall =
                            spec.registry().getContractInfo(CONTRACT_FROM).getBalance();

                    assertEquals(accountBalanceAfterCall, accountBalanceBeforeCall - fee);
                    assertEquals(contractBalanceAfterCall, 10_000L);
                }));
    }

    @HapiTests
    private HapiSpec resultSizeAffectsFees() {
        final var contract = "VerboseDeposit";
        final var TRANSFER_AMOUNT = 1_000L;
        BiConsumer<TransactionRecord, Logger> resultSizeFormatter = (rcd, txnLog) -> {
            final var result = rcd.getContractCallResult();
            txnLog.info(
                    "Contract call result FeeBuilder size = {}, fee = {}, result is"
                            + " [self-reported size = {}, '{}']",
                    () -> FeeBuilder.getContractFunctionSize(result),
                    rcd::getTransactionFee,
                    result.getContractCallResult()::size,
                    result::getContractCallResult);
            txnLog.info("  Literally :: {}", result);
        };

        return defaultHapiSpec("ResultSizeAffectsFees")
                .given(
                        overriding(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT, "100"),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when(
                        contractCall(contract, DEPOSIT, TRANSFER_AMOUNT, 0L, "So we out-danced thought...")
                                .via("noLogsCallTxn")
                                .sending(TRANSFER_AMOUNT),
                        contractCall(contract, DEPOSIT, TRANSFER_AMOUNT, 5L, "So we out-danced thought...")
                                .via("loggedCallTxn")
                                .sending(TRANSFER_AMOUNT))
                .then(
                        assertionsHold((spec, assertLog) -> {
                            HapiGetTxnRecord noLogsLookup =
                                    QueryVerbs.getTxnRecord("noLogsCallTxn").loggedWith(resultSizeFormatter);
                            HapiGetTxnRecord logsLookup =
                                    QueryVerbs.getTxnRecord("loggedCallTxn").loggedWith(resultSizeFormatter);
                            allRunFor(spec, noLogsLookup, logsLookup);
                            final var unloggedRecord = noLogsLookup
                                    .getResponse()
                                    .getTransactionGetRecord()
                                    .getTransactionRecord();
                            final var loggedRecord = logsLookup
                                    .getResponse()
                                    .getTransactionGetRecord()
                                    .getTransactionRecord();
                            assertLog.info("Fee for logged record   = {}", loggedRecord::getTransactionFee);
                            assertLog.info("Fee for unlogged record = {}", unloggedRecord::getTransactionFee);
                            Assertions.assertNotEquals(
                                    unloggedRecord.getTransactionFee(),
                                    loggedRecord.getTransactionFee(),
                                    "Result size should change the txn fee!");
                        }),
                        resetToDefault(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT));
    }

    private HapiSpec autoAssociationSlotsAppearsInInfo() {
        final int maxAutoAssociations = 100;
        final int ADVENTUROUS_NETWORK = 1_000;
        final String CONTRACT = "Multipurpose";
        final String associationsLimitProperty = "entities.limitTokenAssociations";
        final String defaultAssociationsLimit =
                HapiSpecSetup.getDefaultNodeProps().get(associationsLimitProperty);

        return defaultHapiSpec("autoAssociationSlotsAppearsInInfo")
                .given(overridingThree(
                        "entities.limitTokenAssociations",
                        "true",
                        "tokens.maxPerAccount",
                        "" + 1,
                        CONTRACT_ALLOW_ASSOCIATIONS_PROPERTY,
                        "true"))
                .when()
                .then(
                        newKeyNamed(ADMIN_KEY),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT)
                                .adminKey(ADMIN_KEY)
                                .maxAutomaticTokenAssociations(maxAutoAssociations)
                                .hasPrecheck(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT),

                        // Default is NOT to limit associations for entities
                        overriding(associationsLimitProperty, defaultAssociationsLimit),
                        contractCreate(CONTRACT).adminKey(ADMIN_KEY).maxAutomaticTokenAssociations(maxAutoAssociations),
                        getContractInfo(CONTRACT)
                                .has(ContractInfoAsserts.contractWith().maxAutoAssociations(maxAutoAssociations))
                                .logged(),
                        // Restore default
                        overriding("tokens.maxPerAccount", "" + ADVENTUROUS_NETWORK));
    }

    @HapiTests
    private HapiSpec createMaxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller() {
        return defaultHapiSpec("CreateMaxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller")
                .given(
                        overriding(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT1, "5"),
                        uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .when(contractCreate(EMPTY_CONSTRUCTOR_CONTRACT).gas(300_000L).via(CREATE_TX))
                .then(
                        withOpContext((spec, ignore) -> {
                            final var subop01 = getTxnRecord(CREATE_TX).saveTxnRecordToRegistry(CREATE_TX_REC);
                            allRunFor(spec, subop01);

                            final var gasUsed = spec.registry()
                                    .getTransactionRecord(CREATE_TX_REC)
                                    .getContractCreateResult()
                                    .getGasUsed();
                            assertEquals(285_000L, gasUsed);
                        }),
                        resetToDefault(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT1));
    }

    @HapiTests
    private HapiSpec createMinChargeIsTXGasUsedByContractCreate() {
        return defaultHapiSpec("CreateMinChargeIsTXGasUsedByContractCreate")
                .given(
                        overriding(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT1, "100"),
                        uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .when(contractCreate(EMPTY_CONSTRUCTOR_CONTRACT).gas(300_000L).via(CREATE_TX))
                .then(
                        withOpContext((spec, ignore) -> {
                            final var subop01 = getTxnRecord(CREATE_TX).saveTxnRecordToRegistry(CREATE_TX_REC);
                            allRunFor(spec, subop01);

                            final var gasUsed = spec.registry()
                                    .getTransactionRecord(CREATE_TX_REC)
                                    .getContractCreateResult()
                                    .getGasUsed();
                            assertTrue(gasUsed > 0L);
                        }),
                        resetToDefault(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT1));
    }

    HapiSpec propagatesNestedCreations() {
        final var call = "callTxn";
        final var creation = "createTxn";
        final var contract = "NestedCreations";

        final var adminKey = "adminKey";
        final var entityMemo = "JUST DO IT";
        final var customAutoRenew = 7776001L;
        final AtomicReference<String> childLiteralId = new AtomicReference<>();
        final AtomicReference<String> grandChildLiteralId = new AtomicReference<>();
        final AtomicReference<ByteString> expectedChildAddress = new AtomicReference<>();
        final AtomicReference<ByteString> expectedParentAddress = new AtomicReference<>();

        return defaultHapiSpec("PropagatesNestedCreations")
                .given(
                        newKeyNamed(adminKey),
                        uploadInitCode(contract),
                        contractCreate(contract)
                                .stakedNodeId(0)
                                .adminKey(adminKey)
                                .entityMemo(entityMemo)
                                .autoRenewSecs(customAutoRenew)
                                .via(creation))
                .when(contractCall(contract, "propagate").gas(4_000_000L).via(call))
                .then(
                        withOpContext((spec, opLog) -> {
                            final var parentNum = spec.registry().getContractId(contract);

                            final var expectedParentContractAddress = asHeadlongAddress(
                                            asEvmAddress(parentNum.getContractNum()))
                                    .toString()
                                    .toLowerCase()
                                    .substring(2);
                            expectedParentAddress.set(
                                    ByteString.copyFrom(CommonUtils.unhex(expectedParentContractAddress)));

                            final var expectedChildContractAddress =
                                    contractAddress(fromHexString(expectedParentContractAddress), 1L);
                            final var expectedGrandChildContractAddress =
                                    contractAddress(expectedChildContractAddress, 1L);

                            final var childId = ContractID.newBuilder()
                                    .setContractNum(parentNum.getContractNum() + 1L)
                                    .build();
                            childLiteralId.set(HapiPropertySource.asContractString(childId));
                            expectedChildAddress.set(ByteString.copyFrom(expectedChildContractAddress.toArray()));
                            final var grandChildId = ContractID.newBuilder()
                                    .setContractNum(parentNum.getContractNum() + 2L)
                                    .build();
                            grandChildLiteralId.set(HapiPropertySource.asContractString(grandChildId));

                            final var parentContractInfo = getContractInfo(contract)
                                    .has(contractWith().addressOrAlias(expectedParentContractAddress));
                            final var childContractInfo = getContractInfo(childLiteralId.get())
                                    .has(contractWith()
                                            .addressOrAlias(expectedChildContractAddress.toUnprefixedHexString()));
                            final var grandChildContractInfo = getContractInfo(grandChildLiteralId.get())
                                    .has(contractWith()
                                            .addressOrAlias(expectedGrandChildContractAddress.toUnprefixedHexString()))
                                    .logged();

                            allRunFor(spec, parentContractInfo, childContractInfo, grandChildContractInfo);
                        }),
                        sourcing(() -> childRecordsCheck(
                                call,
                                SUCCESS,
                                recordWith()
                                        .contractCreateResult(
                                                resultWith().create1EvmAddress(expectedParentAddress.get(), 1L))
                                        .status(SUCCESS),
                                recordWith()
                                        .contractCreateResult(
                                                resultWith().create1EvmAddress(expectedChildAddress.get(), 1L))
                                        .status(SUCCESS))),
                        sourcing(() -> getContractInfo(childLiteralId.get())
                                .has(contractWith().propertiesInheritedFrom(contract))));
    }

    @HapiTests
    HapiSpec temporarySStoreRefundTest() {
        final var contract = "TemporarySStoreRefund";
        return defaultHapiSpec("TemporarySStoreRefundTest")
                .given(
                        overriding(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT1, "100"),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when(
                        contractCall(contract, "holdTemporary", BigInteger.valueOf(10))
                                .via("tempHoldTx"),
                        contractCall(contract, "holdPermanently", BigInteger.valueOf(10))
                                .via("permHoldTx"))
                .then(
                        withOpContext((spec, opLog) -> {
                            final var subop01 = getTxnRecord("tempHoldTx")
                                    .saveTxnRecordToRegistry("tempHoldTxRec")
                                    .logged();
                            final var subop02 = getTxnRecord("permHoldTx")
                                    .saveTxnRecordToRegistry("permHoldTxRec")
                                    .logged();

                            CustomSpecAssert.allRunFor(spec, subop01, subop02);

                            final var gasUsedForTemporaryHoldTx = spec.registry()
                                    .getTransactionRecord("tempHoldTxRec")
                                    .getContractCallResult()
                                    .getGasUsed();
                            final var gasUsedForPermanentHoldTx = spec.registry()
                                    .getTransactionRecord("permHoldTxRec")
                                    .getContractCallResult()
                                    .getGasUsed();

                            Assertions.assertTrue(gasUsedForTemporaryHoldTx < 23535L);
                            Assertions.assertTrue(gasUsedForPermanentHoldTx > 20000L);
                        }),
                        UtilVerbs.resetToDefault(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT1));
    }

    @HapiTests
    private HapiSpec deletedContractsCannotBeUpdated() {
        final var contract = "SelfDestructCallable";
        final var beneficiary = "beneficiary";
        return defaultHapiSpec("DeletedContractsCannotBeUpdated")
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract).gas(300_000),
                        cryptoCreate(beneficiary).balance(ONE_HUNDRED_HBARS))
                .when(contractCall(contract, "destroy").deferStatusResolution().payingWith(beneficiary))
                .then(contractUpdate(contract).newMemo("Hi there!").hasKnownStatus(INVALID_CONTRACT_ID));
    }

    @HapiTests
    private HapiSpec canCallPendingContractSafely() {
        final var numSlots = 64L;
        final var createBurstSize = 500;
        final long[] targets = {19, 24};
        final AtomicLong createdFileNum = new AtomicLong();
        final var callTxn = "callTxn";
        final var contract = "FibonacciPlus";
        final var expiry = Instant.now().getEpochSecond() + 7776000;

        return defaultHapiSpec("CanCallPendingContractSafely")
                .given(
                        uploadSingleInitCode(contract, expiry, GENESIS, createdFileNum::set),
                        inParallel(IntStream.range(0, createBurstSize)
                                .mapToObj(i -> contractCustomCreate(contract, String.valueOf(i), numSlots)
                                        .fee(ONE_HUNDRED_HBARS)
                                        .gas(300_000L)
                                        .payingWith(GENESIS)
                                        .noLogging()
                                        .deferStatusResolution()
                                        .bytecode(contract)
                                        .adminKey(THRESHOLD))
                                .toArray(HapiSpecOperation[]::new)))
                .when()
                .then(sourcing(() -> contractCallWithFunctionAbi(
                                "0.0." + (createdFileNum.get() + createBurstSize),
                                getABIFor(FUNCTION, "addNthFib", contract),
                                targets,
                                12L)
                        .payingWith(GENESIS)
                        .gas(300_000L)
                        .via(callTxn)));
    }

    @HapiTests
    private HapiSpec lazyCreateThroughPrecompileNotSupportedWhenFlagDisabled() {
        final var CONTRACT = CRYPTO_TRANSFER;
        final var SENDER = "sender";
        final var FUNGIBLE_TOKEN = "fungibleToken";
        final var DELEGATE_KEY = "contractKey";
        final var NOT_SUPPORTED_TXN = "notSupportedTxn";
        final var TOTAL_SUPPLY = 1_000;
        final var ALLOW_AUTO_ASSOCIATIONS_PROPERTY = CONTRACT_ALLOW_ASSOCIATIONS_PROPERTY;

        return propertyPreservingHapiSpec("lazyCreateThroughPrecompileNotSupportedWhenFlagDisabled")
                .preserving(ALLOW_AUTO_ASSOCIATIONS_PROPERTY, LAZY_CREATION_ENABLED)
                .given(
                        overriding(ALLOW_AUTO_ASSOCIATIONS_PROPERTY, "true"),
                        UtilVerbs.overriding(LAZY_CREATION_ENABLED, FALSE),
                        cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN)),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(moving(200, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER)),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT).maxAutomaticTokenAssociations(1),
                        getContractInfo(CONTRACT)
                                .has(ContractInfoAsserts.contractWith().maxAutoAssociations(1))
                                .logged())
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    final var token = spec.registry().getTokenID(FUNGIBLE_TOKEN);
                    final var sender = spec.registry().getAccountID(SENDER);
                    final var amountToBeSent = 50L;

                    allRunFor(
                            spec,
                            newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, CONTRACT))),
                            cryptoUpdate(SENDER).key(DELEGATE_KEY),
                            contractCall(
                                            CONTRACT,
                                            "transferMultipleTokens",
                                            tokenTransferLists()
                                                    .withTokenTransferList(tokenTransferList()
                                                            .forToken(token)
                                                            .withAccountAmounts(
                                                                    accountAmount(sender, -amountToBeSent),
                                                                    accountAmountAlias(addressBytes, amountToBeSent))
                                                            .build())
                                                    .build())
                                    .payingWith(GENESIS)
                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                    .via(NOT_SUPPORTED_TXN)
                                    .gas(GAS_TO_OFFER),
                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY).hasCostAnswerPrecheck(INVALID_ACCOUNT_ID),
                            childRecordsCheck(
                                    NOT_SUPPORTED_TXN,
                                    CONTRACT_REVERT_EXECUTED,
                                    recordWith().status(NOT_SUPPORTED)));
                }))
                .then();
    }

    private HapiSpec evmLazyCreateViaSolidityCall() {
        final var LAZY_CREATE_CONTRACT = "NestedLazyCreateContract";
        final var ECDSA_KEY = "ECDSAKey";
        final var callLazyCreateFunction = "nestedLazyCreateThenSendMore";
        final var revertingCallLazyCreateFunction = "nestedLazyCreateThenRevert";
        final var lazyCreationProperty = "lazyCreation.enabled";
        final var contractsEvmVersionProperty = "contracts.evm.version";
        final var contractsEvmVersionDynamicProperty = "contracts.evm.version.dynamic";
        final var REVERTING_TXN = "revertingTxn";
        final var depositAmount = 1000;
        final var payTxn = "payTxn";

        return propertyPreservingHapiSpec("evmLazyCreateViaSolidityCall")
                .preserving(lazyCreationProperty, contractsEvmVersionProperty, contractsEvmVersionDynamicProperty)
                .given(
                        overridingThree(
                                lazyCreationProperty,
                                "true",
                                contractsEvmVersionProperty,
                                "v0.34",
                                contractsEvmVersionDynamicProperty,
                                "true"),
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        uploadInitCode(LAZY_CREATE_CONTRACT),
                        contractCreate(LAZY_CREATE_CONTRACT).via(CALL_TX_REC),
                        getTxnRecord(CALL_TX_REC).andAllChildRecords().logged())
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    final var mirrorTxn = "mirrorTxn";
                    allRunFor(
                            spec,
                            contractCall(LAZY_CREATE_CONTRACT, callLazyCreateFunction, mirrorAddrWith(1_234_567_890L))
                                    .sending(depositAmount)
                                    .via(mirrorTxn)
                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                    .gas(6_000_000),
                            emptyChildRecordsCheck(mirrorTxn, CONTRACT_REVERT_EXECUTED),
                            contractCall(
                                            LAZY_CREATE_CONTRACT,
                                            revertingCallLazyCreateFunction,
                                            asHeadlongAddress(addressBytes))
                                    .sending(depositAmount)
                                    .via(REVERTING_TXN)
                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                    .gas(6_000_000),
                            emptyChildRecordsCheck(REVERTING_TXN, CONTRACT_REVERT_EXECUTED),
                            contractCall(LAZY_CREATE_CONTRACT, callLazyCreateFunction, asHeadlongAddress(addressBytes))
                                    .via(payTxn)
                                    .sending(depositAmount)
                                    .gas(6_000_000));
                }))
                .then(withOpContext((spec, opLog) -> {
                    final var getTxnRecord =
                            getTxnRecord(payTxn).andAllChildRecords().logged();
                    allRunFor(spec, getTxnRecord);
                    final var lazyAccountId =
                            getTxnRecord.getChildRecord(0).getReceipt().getAccountID();
                    final var name = "lazy";
                    spec.registry().saveAccountId(name, lazyAccountId);
                    allRunFor(spec, getAccountBalance(name).hasTinyBars(depositAmount));
                }));
    }

    private HapiSpec requiresTopLevelSignatureOrApprovalDependingOnControllingProperty() {
        final var ignoredTopLevelSigTransfer = "ignoredTopLevelSigTransfer";
        final var ignoredApprovalTransfer = "ignoredApprovalTransfer";
        final var approvedTransfer = "approvedTransfer";
        final AtomicReference<AccountID> senderAddress = new AtomicReference<>();
        final AtomicReference<AccountID> receiverAddress = new AtomicReference<>();
        final AtomicReference<Address> tokenAddress = new AtomicReference<>();
        final var amountPerTransfer = 50L;
        return propertyPreservingHapiSpec("RequiresTopLevelSignatureOrApprovalDependingOnControllingProperty")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        cryptoCreate(SENDER)
                                .keyShape(SECP256K1_ON)
                                .exposingCreatedIdTo(senderAddress::set)
                                .maxAutomaticTokenAssociations(1),
                        cryptoCreate(RECEIVER)
                                .keyShape(SECP256K1_ON)
                                .exposingCreatedIdTo(receiverAddress::set)
                                .maxAutomaticTokenAssociations(1),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY)
                                .exposingAddressTo(tokenAddress::set),
                        cryptoTransfer(
                                moving(4 * amountPerTransfer, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER)),
                        uploadInitCode(TRANSFER_CONTRACT),
                        contractCreate(TRANSFER_CONTRACT),
                        // First revoke use of top-level signatures from all precompiles
                        overriding(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, ""),
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"))
                .when(
                        // Then, try to transfer tokens using a top-level signature
                        sourcing(() -> contractCall(TRANSFER_CONTRACT, TRANSFER_MULTIPLE_TOKENS, (Object) new Tuple[] {
                                    tokenTransferList()
                                            .forTokenAddress(tokenAddress.get())
                                            .withAccountAmounts(
                                                    accountAmount(senderAddress.get(), -amountPerTransfer),
                                                    accountAmount(receiverAddress.get(), +amountPerTransfer))
                                            .build()
                                })
                                .alsoSigningWithFullPrefix(SENDER)
                                .via(ignoredTopLevelSigTransfer)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        // Switch to allow use of top-level signatures from CryptoTransfer
                        overriding(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CRYPTO_TRANSFER),
                        // Validate now the top-level signature works
                        sourcing(() -> contractCall(TRANSFER_CONTRACT, TRANSFER_MULTIPLE_TOKENS, (Object) new Tuple[] {
                                    tokenTransferList()
                                            .forTokenAddress(tokenAddress.get())
                                            .withAccountAmounts(
                                                    accountAmount(senderAddress.get(), -amountPerTransfer),
                                                    accountAmount(receiverAddress.get(), +amountPerTransfer))
                                            .build()
                                })
                                .alsoSigningWithFullPrefix(SENDER)
                                .gas(GAS_TO_OFFER)),
                        // And validate that ONLY top-level signatures work here (i.e. approvals are
                        // not used
                        // automatically) by trying to transfer tokens using an approval without
                        // top-level signature
                        cryptoApproveAllowance()
                                .payingWith(SENDER)
                                .addTokenAllowance(SENDER, FUNGIBLE_TOKEN, TRANSFER_CONTRACT, 4 * amountPerTransfer),
                        sourcing(() -> contractCall(TRANSFER_CONTRACT, TRANSFER_MULTIPLE_TOKENS, (Object) new Tuple[] {
                                    tokenTransferList()
                                            .forTokenAddress(tokenAddress.get())
                                            .withAccountAmounts(
                                                    accountAmount(senderAddress.get(), -amountPerTransfer),
                                                    accountAmount(receiverAddress.get(), +amountPerTransfer))
                                            .build()
                                })
                                .gas(GAS_TO_OFFER)
                                .via(ignoredApprovalTransfer)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        // Then revoke use of top-level signatures once more, so the approval will
                        // be used automatically
                        overriding(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, ""))
                .then(
                        // Validate the approval is used automatically (although not specified in
                        // the contract)
                        sourcing(() -> contractCall(TRANSFER_CONTRACT, TRANSFER_MULTIPLE_TOKENS, (Object) new Tuple[] {
                                    tokenTransferList()
                                            .forTokenAddress(tokenAddress.get())
                                            .withAccountAmounts(
                                                    accountAmount(senderAddress.get(), -amountPerTransfer),
                                                    accountAmount(receiverAddress.get(), +amountPerTransfer))
                                            .build()
                                })
                                .via(approvedTransfer)
                                .gas(GAS_TO_OFFER)),
                        // Two successful transfers - one with a top-level signature, one with an
                        // approval
                        getAccountBalance(RECEIVER).hasTokenBalance(FUNGIBLE_TOKEN, 2 * amountPerTransfer),
                        getAccountBalance(SENDER).hasTokenBalance(FUNGIBLE_TOKEN, 2 * amountPerTransfer),
                        childRecordsCheck(
                                approvedTransfer,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS))
                                                .gasUsed(14085L))
                                        .tokenTransfers(SomeFungibleTransfers.changingFungibleBalances()
                                                .including(FUNGIBLE_TOKEN, SENDER, -amountPerTransfer)
                                                .including(FUNGIBLE_TOKEN, RECEIVER, amountPerTransfer))),
                        // Confirm the failure without access to top-level sigs was due to the
                        // contract not having an allowance
                        childRecordsCheck(
                                ignoredTopLevelSigTransfer,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(SPENDER_DOES_NOT_HAVE_ALLOWANCE)),
                        // Confirm the failure with access to top-level sigs was due to the missing
                        // top-level sig (not the lack of an allowance)
                        childRecordsCheck(
                                ignoredApprovalTransfer,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)));
    }

    private HapiSpec evmLazyCreateViaSolidityCallTooManyCreatesFails() {
        final var LAZY_CREATE_CONTRACT = "NestedLazyCreateContract";
        final var ECDSA_KEY = "ECDSAKey";
        final var ECDSA_KEY2 = "ECDSAKey2";
        final var createTooManyHollowAccounts = "createTooManyHollowAccounts";
        final var lazyCreationProperty = "lazyCreation.enabled";
        final var contractsEvmVersionProperty = "contracts.evm.version";
        final var contractsEvmVersionDynamicProperty = "contracts.evm.version.dynamic";
        final var maxPrecedingRecords = "consensus.handle.maxPrecedingRecords";
        final var depositAmount = 1000;
        return propertyPreservingHapiSpec("evmLazyCreateViaSolidityCallTooManyCreatesFails")
                .preserving(
                        lazyCreationProperty,
                        maxPrecedingRecords,
                        contractsEvmVersionDynamicProperty,
                        contractsEvmVersionDynamicProperty)
                .given(
                        overridingTwo(lazyCreationProperty, "true", maxPrecedingRecords, "1"),
                        overridingTwo(contractsEvmVersionProperty, "v0.34", contractsEvmVersionDynamicProperty, "true"),
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(ECDSA_KEY2).shape(SECP_256K1_SHAPE),
                        uploadInitCode(LAZY_CREATE_CONTRACT),
                        contractCreate(LAZY_CREATE_CONTRACT).via(CALL_TX_REC),
                        getTxnRecord(CALL_TX_REC).andAllChildRecords().logged())
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    final var ecdsaKey2 = spec.registry().getKey(ECDSA_KEY2);
                    final var tmp2 = ecdsaKey2.getECDSASecp256K1().toByteArray();
                    final var addressBytes2 = recoverAddressFromPubKey(tmp2);
                    allRunFor(
                            spec,
                            contractCall(LAZY_CREATE_CONTRACT, createTooManyHollowAccounts, (Object)
                                            asHeadlongAddressArray(addressBytes, addressBytes2))
                                    .sending(depositAmount)
                                    .via(TRANSFER_TXN)
                                    .gas(6_000_000)
                                    .hasKnownStatus(MAX_CHILD_RECORDS_EXCEEDED),
                            getAliasedAccountInfo(ecdsaKey.toByteString())
                                    .logged()
                                    .hasCostAnswerPrecheck(INVALID_ACCOUNT_ID),
                            getAliasedAccountInfo(ecdsaKey2.toByteString())
                                    .logged()
                                    .hasCostAnswerPrecheck(INVALID_ACCOUNT_ID));
                }))
                .then(
                        emptyChildRecordsCheck(TRANSFER_TXN, MAX_CHILD_RECORDS_EXCEEDED),
                        resetToDefault(lazyCreationProperty, contractsEvmVersionProperty, maxPrecedingRecords));
    }

    @HapiTests
    private HapiSpec rejectsCreationAndUpdateOfAssociationsWhenFlagDisabled() {
        return propertyPreservingHapiSpec("rejectsCreationAndUpdateOfAssociationsWhenFlagDisabled")
                .preserving(CONTRACT_ALLOW_ASSOCIATIONS_PROPERTY)
                .given(overriding(CONTRACT_ALLOW_ASSOCIATIONS_PROPERTY, FALSE))
                .when(uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .then(
                        contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
                                .maxAutomaticTokenAssociations(5)
                                .hasPrecheck(NOT_SUPPORTED),
                        contractCreate(EMPTY_CONSTRUCTOR_CONTRACT).maxAutomaticTokenAssociations(0),
                        contractUpdate(EMPTY_CONSTRUCTOR_CONTRACT)
                                .newMaxAutomaticAssociations(5)
                                .hasPrecheck(NOT_SUPPORTED),
                        contractUpdate(EMPTY_CONSTRUCTOR_CONTRACT).newMemo("Hola!"));
    }

    @HapiTests
    private HapiSpec erc20TransferFromDoesNotWorkIfFlagIsDisabled() {
        return defaultHapiSpec("erc20TransferFromDoesNotWorkIfFlagIsDisabled")
                .given(
                        overriding(HEDERA_ALLOWANCES_IS_ENABLED, FALSE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECIPIENT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(10L)
                                .maxSupply(1000L)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        tokenAssociate(RECIPIENT, FUNGIBLE_TOKEN),
                        tokenAssociate(ERC_20_CONTRACT, FUNGIBLE_TOKEN),
                        cryptoTransfer(moving(10, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_20_CONTRACT,
                                        TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(OWNER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECIPIENT))),
                                        BigInteger.TWO)
                                .gas(500_000L)
                                .via(TRANSFER_FROM_ACCOUNT_TXN)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(
                        getTxnRecord(TRANSFER_FROM_ACCOUNT_TXN)
                                .logged(), // has gasUsed little less than supplied 500K in
                        // contractCall result
                        overriding(HEDERA_ALLOWANCES_IS_ENABLED, "true"));
    }

    @HapiTests
    private HapiSpec whitelistPositiveCase() {
        final AtomicLong whitelistedCalleeMirrorNum = new AtomicLong();
        final AtomicReference<TokenID> tokenID = new AtomicReference<>();
        final AtomicReference<String> attackerMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> whitelistedCalleeMirrorAddr = new AtomicReference<>();

        return propertyPreservingHapiSpec("WhitelistPositiveCase")
                .preserving(CONTRACTS_PERMITTED_DELEGATE_CALLERS)
                .given(
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(PRETEND_ATTACKER)
                                .exposingCreatedIdTo(id -> attackerMirrorAddr.set(asHexedSolidityAddress(id))),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .initialSupply(Long.MAX_VALUE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .exposingCreatedIdTo(id -> tokenID.set(asToken(id))),
                        uploadInitCode(PRETEND_PAIR),
                        contractCreate(PRETEND_PAIR).adminKey(DEFAULT_PAYER),
                        uploadInitCode(DELEGATE_PRECOMPILE_CALLEE),
                        contractCreate(DELEGATE_PRECOMPILE_CALLEE)
                                .adminKey(DEFAULT_PAYER)
                                .exposingNumTo(num -> {
                                    whitelistedCalleeMirrorNum.set(num);
                                    whitelistedCalleeMirrorAddr.set(asHexedSolidityAddress(0, 0, num));
                                }),
                        tokenAssociate(PRETEND_PAIR, FUNGIBLE_TOKEN),
                        tokenAssociate(DELEGATE_PRECOMPILE_CALLEE, FUNGIBLE_TOKEN))
                .when(
                        sourcing(() -> overriding(
                                CONTRACTS_PERMITTED_DELEGATE_CALLERS,
                                String.valueOf(whitelistedCalleeMirrorNum.get()))),
                        sourcing(() -> contractCall(
                                        PRETEND_PAIR,
                                        CALL_TO,
                                        asHeadlongAddress(whitelistedCalleeMirrorAddr.get()),
                                        asHeadlongAddress(asSolidityAddress(tokenID.get())),
                                        asHeadlongAddress(attackerMirrorAddr.get()))
                                .via(ATTACK_CALL)
                                .gas(5_000_000L)
                                .hasKnownStatus(SUCCESS)))
                .then(
                        // Because this callee is on the whitelist, the pair WILL have an allowance
                        // here
                        getAccountDetails(PRETEND_PAIR).has(accountDetailsWith().tokenAllowancesCount(1)),
                        // Instead of the callee
                        getAccountDetails(DELEGATE_PRECOMPILE_CALLEE)
                                .has(accountDetailsWith().tokenAllowancesCount(0)));
    }

    @HapiTests
    private HapiSpec whitelistNegativeCases() {
        final AtomicLong unlistedCalleeMirrorNum = new AtomicLong();
        final AtomicLong whitelistedCalleeMirrorNum = new AtomicLong();
        final AtomicReference<TokenID> tokenID = new AtomicReference<>();
        final AtomicReference<String> attackerMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> unListedCalleeMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> whitelistedCalleeMirrorAddr = new AtomicReference<>();

        return propertyPreservingHapiSpec("WhitelistNegativeCases")
                .preserving(CONTRACTS_PERMITTED_DELEGATE_CALLERS)
                .given(
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(PRETEND_ATTACKER)
                                .exposingCreatedIdTo(id -> attackerMirrorAddr.set(asHexedSolidityAddress(id))),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .initialSupply(Long.MAX_VALUE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .exposingCreatedIdTo(id -> tokenID.set(asToken(id))),
                        uploadInitCode(PRETEND_PAIR),
                        contractCreate(PRETEND_PAIR).adminKey(DEFAULT_PAYER),
                        uploadInitCode(DELEGATE_ERC_CALLEE),
                        contractCreate(DELEGATE_ERC_CALLEE)
                                .adminKey(DEFAULT_PAYER)
                                .exposingNumTo(num -> {
                                    whitelistedCalleeMirrorNum.set(num);
                                    whitelistedCalleeMirrorAddr.set(asHexedSolidityAddress(0, 0, num));
                                }),
                        uploadInitCode(DELEGATE_PRECOMPILE_CALLEE),
                        contractCreate(DELEGATE_PRECOMPILE_CALLEE)
                                .adminKey(DEFAULT_PAYER)
                                .exposingNumTo(num -> {
                                    unlistedCalleeMirrorNum.set(num);
                                    unListedCalleeMirrorAddr.set(asHexedSolidityAddress(0, 0, num));
                                }),
                        tokenAssociate(PRETEND_PAIR, FUNGIBLE_TOKEN),
                        tokenAssociate(DELEGATE_ERC_CALLEE, FUNGIBLE_TOKEN),
                        tokenAssociate(DELEGATE_PRECOMPILE_CALLEE, FUNGIBLE_TOKEN))
                .when(
                        sourcing(() -> overriding(
                                CONTRACTS_PERMITTED_DELEGATE_CALLERS,
                                String.valueOf(whitelistedCalleeMirrorNum.get()))),
                        sourcing(() -> contractCall(
                                        PRETEND_PAIR,
                                        CALL_TO,
                                        asHeadlongAddress(unListedCalleeMirrorAddr.get()),
                                        asHeadlongAddress(asSolidityAddress(tokenID.get())),
                                        asHeadlongAddress(attackerMirrorAddr.get()))
                                .gas(5_000_000L)
                                .hasKnownStatus(SUCCESS)),
                        // Because this callee isn't on the whitelist, the pair won't have an
                        // allowance here
                        getAccountDetails(PRETEND_PAIR).has(accountDetailsWith().tokenAllowancesCount(0)),
                        // Instead nobody gets an allowance
                        getAccountDetails(DELEGATE_PRECOMPILE_CALLEE)
                                .has(accountDetailsWith().tokenAllowancesCount(0)),
                        sourcing(() -> contractCall(
                                        PRETEND_PAIR,
                                        CALL_TO,
                                        asHeadlongAddress(whitelistedCalleeMirrorAddr.get()),
                                        asHeadlongAddress(asSolidityAddress(tokenID.get())),
                                        asHeadlongAddress(attackerMirrorAddr.get()))
                                .gas(5_000_000L)
                                .hasKnownStatus(SUCCESS)))
                .then(
                        // Even though this is on the whitelist, b/c the whitelisted contract
                        // is going through a delegatecall "chain" via the ERC-20 call, the pair
                        // still won't have an allowance here
                        getAccountDetails(PRETEND_PAIR).has(accountDetailsWith().tokenAllowancesCount(0)),
                        // Instead of the callee
                        getAccountDetails(DELEGATE_ERC_CALLEE)
                                .has(accountDetailsWith().tokenAllowancesCount(0)));
    }

    private HapiSpec contractCreateNoncesExternalizationHappyPath() {
        final var contract = "NoncesExternalization";
        final var contractCreateTxn = "contractCreateTxn";

        return propertyPreservingHapiSpec("ContractCreateNoncesExternalizationHappyPath")
                .preserving(CONTRACTS_NONCES_EXTERNALIZATION_ENABLED)
                .given(
                        overriding(CONTRACTS_NONCES_EXTERNALIZATION_ENABLED, "true"),
                        cryptoCreate(PAYER).balance(10 * ONE_HUNDRED_HBARS),
                        uploadInitCode(contract),
                        contractCreate(contract).via(contractCreateTxn))
                .when()
                .then(withOpContext((spec, opLog) -> {
                    final var opContractTxnRecord = getTxnRecord(contractCreateTxn);

                    allRunFor(spec, opContractTxnRecord);

                    final var parentContractId = spec.registry().getContractId(contract);
                    final var childContracts = opContractTxnRecord
                            .getResponse()
                            .getTransactionGetRecord()
                            .getTransactionRecord()
                            .getContractCreateResult()
                            .getContractNoncesList()
                            .stream()
                            .filter(contractNonceInfo ->
                                    !contractNonceInfo.getContractId().equals(parentContractId))
                            .toList();

                    // Asserts nonce of parent contract
                    HapiGetTxnRecord opAssertParent = getTxnRecord(contractCreateTxn)
                            .hasPriority(recordWith()
                                    .contractCreateResult(resultWith().contractWithNonce(parentContractId, 4L)));
                    allRunFor(spec, opAssertParent);

                    // Asserts nonces of all newly deployed contracts through the constructor
                    for (final var contractNonceInfo : childContracts) {
                        HapiGetTxnRecord op = getTxnRecord(contractCreateTxn)
                                .hasPriority(recordWith()
                                        .contractCreateResult(
                                                resultWith().contractWithNonce(contractNonceInfo.getContractId(), 1L)));
                        allRunFor(spec, op);
                    }
                }));
    }

    @HapiTests
    private HapiSpec contractCreateFollowedByContractCallNoncesExternalization() {
        final var contract = "NoncesExternalization";
        final var payer = "payer";

        /* SMART CONTRACT FUNCTION NAMES */
        final var deployParentContractFn = "deployParentContract";
        final var deployChildFromParentContractFn = "deployChildFromParentContract";
        final var deployChildAndRevertFromParentContractFn = "deployChildAndRevertFromParentContract";

        /* VIA TRANSACTION NAMES */
        final var contractCreateTx = "contractCreateTx";
        final var deployContractTx = "deployContractTx";
        final var committedInnerCreationTx = "committedInnerCreationTx";
        final var revertedInnerCreationTx = "revertedInnerCreationTx";

        return propertyPreservingHapiSpec("contractCreateFollowedByContractCallNoncesExternalization")
                .preserving(CONTRACTS_NONCES_EXTERNALIZATION_ENABLED)
                .given(
                        overriding(CONTRACTS_NONCES_EXTERNALIZATION_ENABLED, "true"),
                        cryptoCreate(payer).balance(10 * ONE_HUNDRED_HBARS),
                        uploadInitCode(contract),
                        contractCreate(contract).via(contractCreateTx))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(contract, deployParentContractFn)
                                .payingWith(payer)
                                .via(deployContractTx)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS))))
                .then(withOpContext((spec, opLog) -> {
                    /** 1. Retrieves sorted list of all contracts deployed in the constructor (parent contracts) */
                    final var opCreateTxRecord = getTxnRecord(contractCreateTx);
                    allRunFor(spec, opCreateTxRecord);

                    final var parentContractsList = opCreateTxRecord
                            .getResponse()
                            .getTransactionGetRecord()
                            .getTransactionRecord()
                            .getContractCreateResult()
                            .getContractNoncesList()
                            .stream()
                            .filter(contractNonceInfo -> !contractNonceInfo
                                    .getContractId()
                                    .equals(spec.registry().getContractId(contract)))
                            .toList();

                    /** 2. Asserts main contract (NoncesExternalization) nonce is 5 */
                    final var opAssertMain = getTxnRecord(deployContractTx)
                            .logged()
                            .hasPriority(recordWith()
                                    .contractCallResult(resultWith()
                                            .contractWithNonce(spec.registry().getContractId(contract), 5L)));
                    allRunFor(spec, opAssertMain);

                    /**
                     * 3. Deploys child from the first parent contract deployed in the constructor (index 0).
                     * Asserts parent's nonce is 2.
                     */
                    final var deployChild = contractCall(contract, deployChildFromParentContractFn, BigInteger.ZERO)
                            .gas(GAS_TO_OFFER)
                            .via(committedInnerCreationTx);
                    HapiGetTxnRecord deployChildTxnRecord = getTxnRecord(committedInnerCreationTx);
                    allRunFor(spec, deployChild, deployChildTxnRecord);

                    /* Retrieves contractId of the first deployed contract in the constructor - index 0 */
                    final var firstParentContractId = parentContractsList.get(0).getContractId();
                    spec.registry().saveContractId("firstParentContractId", firstParentContractId);

                    HapiGetTxnRecord opFirstParentNonce = getTxnRecord(committedInnerCreationTx)
                            .andAllChildRecords()
                            .logged()
                            .hasPriority(recordWith()
                                    .contractCallResult(resultWith()
                                            .contractWithNonce(
                                                    spec.registry().getContractId("firstParentContractId"), 2L)));
                    allRunFor(spec, opFirstParentNonce);

                    /** 4. Tries to deploy child from parent and reverts. Asserts contract_nonces entries are null. */
                    final var deployChildAndRevert = contractCall(
                                    contract, deployChildAndRevertFromParentContractFn, BigInteger.ONE)
                            .gas(GAS_TO_OFFER)
                            .via(revertedInnerCreationTx);
                    final var deployChildAndRevertTxnRecord = getTxnRecord(revertedInnerCreationTx);
                    allRunFor(spec, deployChildAndRevert, deployChildAndRevertTxnRecord);

                    /* Retrieves contractId of the second deployed contract in the constructor - index 1 */
                    final var secondParentContractId =
                            parentContractsList.get(1).getContractId();
                    spec.registry().saveContractId("secondParentContractId", secondParentContractId);

                    HapiGetTxnRecord opSecondParentNonce = getTxnRecord(revertedInnerCreationTx)
                            .andAllChildRecords()
                            .logged()
                            .hasPriority(recordWith()
                                    .contractCallResult(resultWith()
                                            .contractWithNonce(
                                                    spec.registry().getContractId("secondParentContractId"), null)));
                    allRunFor(spec, opSecondParentNonce);
                }));
    }

    private HapiSpec shouldReturnNullWhenContractsNoncesExternalizationFlagIsDisabled() {
        final var contract = "NoncesExternalization";
        final var payer = "payer";

        return propertyPreservingHapiSpec("shouldReturnNullWhenContractsNoncesExternalizationFlagIsDisabled")
                .preserving(CONTRACTS_NONCES_EXTERNALIZATION_ENABLED)
                .given(
                        overriding(CONTRACTS_NONCES_EXTERNALIZATION_ENABLED, "false"),
                        cryptoCreate(payer).balance(10 * ONE_HUNDRED_HBARS),
                        uploadInitCode(contract),
                        contractCreate(contract).logged().via("txn"),
                        withOpContext((spec, opLog) -> {
                            HapiGetTxnRecord op = getTxnRecord("txn")
                                    .logged()
                                    .hasPriority(recordWith()
                                            .contractCreateResult(resultWith()
                                                    .contractWithNonce(
                                                            spec.registry().getContractId(contract), null)));
                            allRunFor(spec, op);
                        }))
                .when()
                .then();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
