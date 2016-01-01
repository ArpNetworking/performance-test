/**
 * Copyright 2015 Groupon.com
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

import com.carrotsearch.junitbenchmarks.BenchmarkOptions;
import com.carrotsearch.junitbenchmarks.BenchmarkRule;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.nio.file.Paths;
import java.util.function.Function;

/**
 * Sample performance test. This test "compares" the performance of reflective instantiation with
 * <code>newInstance</code> versus direct construction invocation. This produces an independent
 * row of performance data in the output file for each test. Each row is an independent performance
 * series.
 *
 * If you enable the profiler it will generate a single profile for all tests in this class. If the tests
 * represent different use cases of the same code this may be acceptable. However, if the tests are for
 * comparative purposes (as they are here) it is strongly recommended that different test classes be used.
 *
 * @author Ville Koskela (vkoskela at groupon dot com)
 */
@BenchmarkOptions(callgc = true, benchmarkRounds = 10, warmupRounds = 5)
public class SamplePerformanceTest {

    @BeforeClass
    public static void setUp() {
        JSON_BENCHMARK_CONSUMER.prepareClass();
    }

    @Test
    public void testConstructor() {
        test(
                "constructor",
                aVoid -> {
                    try {
                        new TestClass();
                        // CHECKSTYLE.OFF: IllegalCatch - Catch any exception
                    } catch (final Exception e) {
                        // CHECKSTYLE.ON: IllegalCatch
                        Assert.fail("Reflective construction failed");
                    }
                    return null;
                },
                ITERATIONS);
    }

    @Test
    public void testNewInstance() {
        test(
                "new_instance",
                aVoid -> {
                    try {
                        TestClass.class.newInstance();
                        // CHECKSTYLE.OFF: IllegalCatch - Catch any exception
                    } catch (final Exception e) {
                        // CHECKSTYLE.ON: IllegalCatch
                        Assert.fail("Reflective construction failed");
                    }
                    return null;
                },
                ITERATIONS);
    }

    public void test(final String name, final Function<Void, Void> method, final Long repetitions) {
        for (long i = 0; i < repetitions; ++i) {
            method.apply(null);
        }
    }

    @Rule
    public final TestRule _benchMarkRule = new BenchmarkRule(JSON_BENCHMARK_CONSUMER);

    private static final JsonBenchmarkConsumer JSON_BENCHMARK_CONSUMER = new JsonBenchmarkConsumer(
            Paths.get("target/site/perf/sample-performance-test.json"));

    private static final long ITERATIONS = 100000000L;

    private static final class TestClass {

        TestClass() {}
    }
}
