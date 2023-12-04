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

package com.hedera.services.bdd.junit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.platform.commons.annotation.Testable;

/**
 * {@link HapiTestSuite} is used to mark a file as containing a suite of HAPI tests. Each individual test method is
 * annotated with {@link HapiTests}. Classes with this annotation must extend from HapiSuite.
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Testable
public @interface HapiTestSuite {
    /** If true, then a new cluster is created for this test suite */
    boolean isolated() default false;

    /**
     * If true, we will set recordStream.autoSnapshotManagement property to true and enable fuzzy
     * matching or every spec in the suite
     * @return true if we want to enable fuzzy matching for every spec in the suite
     */
    boolean fuzzyMatch() default false;
}
