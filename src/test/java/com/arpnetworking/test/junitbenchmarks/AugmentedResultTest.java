/*
 * Copyright 2018 Inscope Metrics
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arpnetworking.test.junitbenchmarks;

import com.carrotsearch.junitbenchmarks.DataCreator;
import com.carrotsearch.junitbenchmarks.Result;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Tests the {@link AugmentedResult} class.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public final class AugmentedResultTest {

    @Test
    public void testWithPath() {
        final Result result = DataCreator.createResult();
        final Path path = Paths.get("./target");
        final AugmentedResult augmentedResult = new AugmentedResult(result, path);

        Assert.assertTrue(augmentedResult.getProfileFile().isPresent());
        Assert.assertEquals(path, augmentedResult.getProfileFile().get());
        Assert.assertEquals(result, augmentedResult.getResult());
    }

    @Test
    public void testWithoutPath() {
        final Result result = DataCreator.createResult();
        final AugmentedResult augmentedResult = new AugmentedResult(result);

        Assert.assertFalse(augmentedResult.getProfileFile().isPresent());
        Assert.assertEquals(result, augmentedResult.getResult());
    }
}
