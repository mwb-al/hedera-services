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

package com.swirlds.common.metrics;

import static com.swirlds.common.metrics.Metric.ValueType.MAX;
import static com.swirlds.common.metrics.Metric.ValueType.MIN;
import static com.swirlds.common.metrics.Metric.ValueType.STD_DEV;
import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static com.swirlds.common.metrics.MetricType.ACCUMULATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Testing IntegerPairAccumulator")
class IntegerPairAccumulatorTests {

    private final IntegerPairAccumulator<Double> sut = new IntegerPairAccumulator<>() {
        @Override
        public Double get() {
            return null;
        }

        @Override
        public int getLeft() {
            return 0;
        }

        @Override
        public int getRight() {
            return 0;
        }

        @Override
        public void update(int leftValue, int rightValue) {}

        @Override
        public String getCategory() {
            return null;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public MetricType getMetricType() {
            return ACCUMULATOR;
        }

        @Override
        public DataType getDataType() {
            return null;
        }

        @Override
        public String getUnit() {
            return null;
        }

        @Override
        public String getFormat() {
            return null;
        }

        @Override
        public void reset() {}
    };

    @Test
    void getMetricType() {
        assertThat(sut.getMetricType()).isEqualTo(ACCUMULATOR);
    }

    @Test
    void getValueTypes() {
        assertThat(sut.getValueTypes()).containsExactly(VALUE);
    }

    @Test
    void get_ShouldReturnValueByValueType() {
        final IntegerPairAccumulator<Double> accumulator = spy(sut);

        final Double value = accumulator.get(VALUE);

        assertThat(value).isEqualTo(sut.get());
        verify(accumulator, times(1)).get();
    }

    @Test
    void get_ShouldThrowExceptionIfValueTypeNotSupported() {
        assertThatThrownBy(() -> sut.get(MAX)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sut.get(MIN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sut.get(STD_DEV)).isInstanceOf(IllegalArgumentException.class);
    }
}
