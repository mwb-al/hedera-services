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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.TokenUnpauseTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.ReadableTokenStoreImpl;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.TokenUnpauseHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.TokenHandlerTestBase;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenUnpauseHandlerTests extends TokenHandlerTestBase {
    private TokenUnpauseHandler subject;
    private TransactionBody tokenUnpauseTxn;
    private FakePreHandleContext preHandleContext;

    @Mock
    private ReadableAccountStore accountStore;

    @Mock
    private Account account;

    @Mock(strictness = LENIENT)
    private HandleContext handleContext;

    @BeforeEach
    void setUp() throws PreCheckException {
        given(accountStore.getAccountById(payerId)).willReturn(account);
        given(account.key()).willReturn(payerKey);
        subject = new TokenUnpauseHandler();
        givenValidTxn();
        refreshStoresWithCurrentTokenInWritable();
        preHandleContext = new FakePreHandleContext(accountStore, tokenUnpauseTxn);
        given(handleContext.writableStore(WritableTokenStore.class)).willReturn(writableTokenStore);
    }

    @Test
    void unPausesToken() {
        pauseKnownToken();
        assertTrue(writableTokenStore.get(tokenId).paused());

        subject.handle(handleContext);

        final var unpausedToken = writableTokenStore.get(tokenId);
        assertFalse(unpausedToken.paused());
    }

    @Test
    void unPausesTokenFailsIfInvalidToken() {
        pauseKnownToken();
        assertTrue(writableTokenStore.get(tokenId).paused());
        givenInvalidTokenInTxn();

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(INVALID_TOKEN_ID, msg.getStatus());
    }

    @Test
    void failsForNullArguments() {
        assertThrows(NullPointerException.class, () -> subject.handle(null));
    }

    @Test
    void validatesTokenExistsInPreHandle() throws PreCheckException {
        givenInvalidTokenInTxn();
        preHandleContext = new FakePreHandleContext(accountStore, tokenUnpauseTxn);
        preHandleContext.registerStore(ReadableTokenStore.class, readableTokenStore);

        assertThrowsPreCheck(() -> subject.preHandle(preHandleContext), INVALID_TOKEN_ID);
    }

    @Test
    void failsInPreCheckIfTxnBodyHasNoToken() throws PreCheckException {
        final var txn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payerId).build())
                .tokenUnpause(TokenUnpauseTransactionBody.newBuilder())
                .build();
        preHandleContext = new FakePreHandleContext(accountStore, txn);
        preHandleContext.registerStore(ReadableTokenStore.class, readableTokenStore);
        assertThrowsPreCheck(() -> subject.preHandle(preHandleContext), INVALID_TOKEN_ID);
    }

    @Test
    void preHandleAddsPauseKeyToContext() throws PreCheckException {
        preHandleContext.registerStore(ReadableTokenStore.class, readableTokenStore);
        subject.preHandle(preHandleContext);

        assertEquals(1, preHandleContext.requiredNonPayerKeys().size());
    }

    @Test
    void preHandleSetsStatusWhenTokenMissing() throws PreCheckException {
        givenInvalidTokenInTxn();
        preHandleContext = new FakePreHandleContext(accountStore, tokenUnpauseTxn);
        preHandleContext.registerStore(ReadableTokenStore.class, readableTokenStore);
        assertThrowsPreCheck(() -> subject.preHandle(preHandleContext), INVALID_TOKEN_ID);
    }

    @Test
    void doesntAddAnyKeyIfPauseKeyMissing() throws PreCheckException {
        final var copy = token.copyBuilder().pauseKey(Key.DEFAULT).build();
        readableTokenState = MapReadableKVState.<TokenID, Token>builder(TOKENS)
                .value(tokenId, copy)
                .build();
        given(readableStates.<TokenID, Token>get(TOKENS)).willReturn(readableTokenState);
        readableTokenStore = new ReadableTokenStoreImpl(readableStates);
        preHandleContext.registerStore(ReadableTokenStore.class, readableTokenStore);

        subject.preHandle(preHandleContext);
        assertEquals(0, preHandleContext.requiredNonPayerKeys().size());
    }

    private void givenValidTxn() {
        tokenUnpauseTxn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payerId).build())
                .tokenUnpause(TokenUnpauseTransactionBody.newBuilder().token(tokenId))
                .build();
        given(handleContext.body()).willReturn(tokenUnpauseTxn);
    }

    private void givenInvalidTokenInTxn() {
        tokenUnpauseTxn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payerId).build())
                .tokenUnpause(TokenUnpauseTransactionBody.newBuilder()
                        .token(TokenID.newBuilder().tokenNum(2).build()))
                .build();
        given(handleContext.body()).willReturn(tokenUnpauseTxn);
    }

    private void pauseKnownToken() {
        final var token =
                writableTokenStore.get(tokenId).copyBuilder().paused(true).build();
        writableTokenStore.put(token);
    }
}
