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

package com.hedera.node.app.service.mono.state.codec;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import java.nio.ByteBuffer;
import java.util.SplittableRandom;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class VirtualValueSerdesAdapterTests extends AbstractVirtualCodecTests<VirtualBlobValue> {
    private static final int NUM_INSTANCES = 42;
    private static final SplittableRandom RANDOM = new SplittableRandom();

    public VirtualValueSerdesAdapterTests() {
        super(MonoMapCodecAdapter.codecForVirtualValue(VirtualBlobValue.CURRENT_VERSION, VirtualBlobValue::new));
    }

    @Test
    void doesNotSupportMeasuring() {
        final var bytes = randomBytes();
        final var value = new VirtualBlobValue(bytes);
        final var bb = BufferedData.wrap(ByteBuffer.wrap(writeUsingBuffer(value)));
        assertThrows(UnsupportedOperationException.class, () -> subject.measure(bb));
    }

    @Test
    void doesNotSupportFastEquals() {
        final var value = new VirtualBlobValue(randomBytes());
        final var bb = BufferedData.wrap(ByteBuffer.wrap(writeUsingBuffer(value)));
        assertThrows(UnsupportedOperationException.class, () -> subject.fastEquals(value, bb));
    }

    /**
     * Used by the base {@link AbstractVirtualCodecTest} to generate random instances of
     * the {@link VirtualBlobValue} type.
     *
     * @return a stream of random instances of the {@link VirtualBlobValue} type
     */
    public static Stream<VirtualBlobValue> randomInstances() {
        return Stream.generate(() -> new VirtualBlobValue(randomBytes())).limit(NUM_INSTANCES);
    }

    private static byte[] randomBytes() {
        final var bytes = new byte[RANDOM.nextInt(1, MAX_SUPPORTED_SERIALIZED_SIZE / 32)];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
