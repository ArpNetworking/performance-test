/*
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

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;
import java.util.function.Function;

/**
 * Sample performance test. This test "compares" the performance of reflective
 * instantiation with {@code newInstance} versus direct construction
 * invocation. This produces an independent row of performance data in the
 * output file for each test. Each row is an independent performance series.
 *
 * If you enable the profiler it will generate a profile for each test method
 * in this class. The {@code prepareClass} call in {@code @BeforeClass} ensures
 * that test framework overhead does not skew the profile results. However,
 * be aware that test fixture setup time will not be excluded (e.g. constructor
 * execution and {@code @Before} execution). Therefore, you should initialize
 * any test fixtures prior to {@code prepareClass}; however, that call must be
 * in {@code @BeforeClass} due to design constraints of junitbenchmarks.
 *
 * @author Ville Koskela (vkoskela at groupon dot com)
 */
@BenchmarkOptions(callgc = true, benchmarkRounds = 500, warmupRounds = 50)
public final class SampleTestPerf {

    @Rule
    public final TestRule _benchMarkRule = new BenchmarkRule(JSON_BENCHMARK_CONSUMER);

    private static final JsonBenchmarkConsumer JSON_BENCHMARK_CONSUMER = new JsonBenchmarkConsumer(
            Paths.get("target/perf/sample-performance-test.json"));

    private static final long ITERATIONS = 10000L;

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
                        // CHECKSTYLE.OFF: IllegalCatch - We don't want to leak these.
                    } catch (final RuntimeException e) {
                        // CHECKSTYLE.ON: IllegalCatch
                        Assert.fail("Constructor construction failed");
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
                        TestClass.class.getDeclaredConstructor().newInstance();
                        // CHECKSTYLE.OFF: IllegalCatch - We don't want to leak these.
                    } catch (final IllegalAccessException
                                   | InstantiationException
                                   | RuntimeException
                                   | NoSuchMethodException
                                   | InvocationTargetException e) {
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

    private static final class TestClass {

        TestClass() {}
    }
}
