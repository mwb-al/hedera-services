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

package com.hedera.node.app.service.networkadmin.impl.test.serdes;

import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.submerkle.ExchangeRates;
import com.hedera.node.app.service.mono.state.submerkle.SequenceNumber;
import com.hedera.node.app.service.networkadmin.impl.serdes.MonoContextAdapterCodec;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class MonoContextAdapterSerdesTests {
    private static final MerkleNetworkContext SOME_CONTEXT = new MerkleNetworkContext(
            Instant.ofEpochSecond(1_234_567L, 890),
            new SequenceNumber(666L),
            777L,
            new ExchangeRates(1, 2, 3L, 4, 5, 6L));

    @Mock
    private ReadableSequentialData input;

    final MonoContextAdapterCodec subject = new MonoContextAdapterCodec();

    @Test
    void doesntSupportUnnecessary() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> subject.measureRecord(SOME_CONTEXT));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> subject.measure(input));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> subject.fastEquals(SOME_CONTEXT, input));
    }

    @Test
    void canSerializeAndDeserializeFromAppropriateStream() throws IOException {
        final var baos = new ByteArrayOutputStream();
        final var actualOut = new SerializableDataOutputStream(baos);
        subject.write(SOME_CONTEXT, new WritableStreamingData(actualOut));
        actualOut.flush();

        final var actualIn = new SerializableDataInputStream(new ByteArrayInputStream(baos.toByteArray()));
        final var parsed = subject.parse(new ReadableStreamingData(actualIn));
        assertSomeFields(SOME_CONTEXT, parsed);
    }

    private void assertSomeFields(final MerkleNetworkContext a, final MerkleNetworkContext b) {
        Assertions.assertEquals(a.seqNo().current(), b.seqNo().current());
        Assertions.assertEquals(a.lastScannedEntity(), b.lastScannedEntity());
        Assertions.assertEquals(a.midnightRates(), b.getMidnightRates());
        Assertions.assertEquals(a.getStateVersion(), b.getStateVersion());
        Assertions.assertEquals(a.getPreExistingEntityScanStatus(), b.getPreExistingEntityScanStatus());
    }
}
