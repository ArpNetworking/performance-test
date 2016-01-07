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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Sample parameterized performance test. This test "compares" the performance of reflective instantiation with
 * <code>newInstance</code> versus direct construction invocation.  This produces an independent
 * row of performance data in the output file for each parameterization of each test. Each row is an independent
 * performance series.
 *
 * If you enable the profiler it will generate a single profile for all permutations of this test. If the permutations
 * represent different use cases of the same code this may be acceptable. However, if the permutations are for
 * comparative purposes it is strongly recommended that different test classes be used.
 *
 * @author Ville Koskela (vkoskela at groupon dot com)
 */
@RunWith(Parameterized.class)
@BenchmarkOptions(callgc = true, benchmarkRounds = 10, warmupRounds = 5)
public class SampleParameterizedPerformanceTest {

    public SampleParameterizedPerformanceTest(final String name, final Function<Void, Void> method, final Long repetitions) {
        _method = method;
        _repetitions = repetitions;
    }

    @BeforeClass
    public static void setUp() {
        JSON_BENCHMARK_CONSUMER.prepareClass();
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> createParameters() {
        final List<Object> methodA = Arrays.<Object>asList(
                "constructor",
                (Function<Void, Void>) input -> {
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
        final List<Object> methodB = Arrays.<Object>asList(
                "new_instance",
                (Function<Void, Void>) input -> {
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

        return Arrays.asList(
                methodA.toArray(),
                methodB.toArray());
    }

    @Test
    public void test() {
        for (long i = 0; i < _repetitions; ++i) {
            _method.apply(null);
        }
    }

    private final Function<Void, Void> _method;
    private final long _repetitions;

    @Rule
    public final TestRule _benchMarkRule = new BenchmarkRule(JSON_BENCHMARK_CONSUMER);

    private static final JsonBenchmarkConsumer JSON_BENCHMARK_CONSUMER = new JsonBenchmarkConsumer(
            Paths.get("target/site/perf/sample-parameterized-performance-test.json"));

    private static final long ITERATIONS = 10000L;

    private static final class TestClass {

        TestClass() {}
    }
}
