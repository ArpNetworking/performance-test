/*
 * Copyright 2014 Groupon.com
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

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.carrotsearch.junitbenchmarks.DataCreator;
import com.carrotsearch.junitbenchmarks.Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests a {@link JsonBenchmarkConsumer}.
 *
 * @author Brandon Arp (barp at groupon dot com)
 */
public final class JsonBenchmarkConsumerTest {

    @Test
    public void testNormalBenchmarkCase() throws IOException {
        final Path path = Paths.get("target/tmp/test/testConsumer.json");
        path.toFile().deleteOnExit();
        Files.deleteIfExists(path);
        final JsonBenchmarkConsumer consumer = new JsonBenchmarkConsumer(path);

        final Result result = DataCreator.createResult();
        consumer.accept(result);
        consumer.close();

        // Read the file back in as json
        final ObjectMapper mapper = ObjectMapperFactory.getInstance();
        final JsonNode resultsArray = mapper.readTree(path.toFile());
        Assert.assertEquals(1, resultsArray.size());
        final JsonNode augmentedResultNode = resultsArray.get(0);
        Assert.assertTrue(augmentedResultNode.isObject());
        final JsonNode resultNode = augmentedResultNode.get("result");
        Assert.assertTrue(resultNode.isObject());

        Assert.assertEquals("com.arpnetworking.test.junitbenchmarks.JsonBenchmarkConsumerTest", resultNode.get("testClassName").asText());
        Assert.assertEquals("testNormalBenchmarkCase", resultNode.get("testMethodName").asText());

        Assert.assertEquals(result.benchmarkRounds, resultNode.get("benchmarkRounds").asInt());
        Assert.assertEquals(result.warmupRounds, resultNode.get("warmupRounds").asInt());
        Assert.assertEquals(result.warmupTime, resultNode.get("warmupTime").asInt());
        Assert.assertEquals(result.benchmarkTime, resultNode.get("benchmarkTime").asInt());

        Assert.assertTrue(resultNode.get("roundAverage").isObject());
        final ObjectNode roundAverageNode = (ObjectNode) resultNode.get("roundAverage");
        Assert.assertEquals(result.roundAverage.avg, roundAverageNode.get("avg").asDouble(), 0.0001d);
        Assert.assertEquals(result.roundAverage.stddev, roundAverageNode.get("stddev").asDouble(), 0.0001d);

        Assert.assertTrue(resultNode.get("blockedAverage").isObject());
        final ObjectNode blockedAverageNode = (ObjectNode) resultNode.get("blockedAverage");
        Assert.assertEquals(result.blockedAverage.avg, blockedAverageNode.get("avg").asDouble(), 0.0001d);
        Assert.assertEquals(result.blockedAverage.stddev, blockedAverageNode.get("stddev").asDouble(), 0.0001d);

        Assert.assertTrue(resultNode.get("gcAverage").isObject());
        final ObjectNode gcAverageNode = (ObjectNode) resultNode.get("gcAverage");
        Assert.assertEquals(result.gcAverage.avg, gcAverageNode.get("avg").asDouble(), 0.0001d);
        Assert.assertEquals(result.gcAverage.stddev, gcAverageNode.get("stddev").asDouble(), 0.0001d);

        Assert.assertTrue(resultNode.get("gcInfo").isObject());
        final ObjectNode gcInfoNode = (ObjectNode) resultNode.get("gcInfo");
        Assert.assertEquals(result.gcInfo.accumulatedInvocations(), gcInfoNode.get("accumulatedInvocations").asInt());
        Assert.assertEquals(result.gcInfo.accumulatedTime(), gcInfoNode.get("accumulatedTime").asInt());

        Assert.assertEquals(result.getThreadCount(), resultNode.get("threadCount").asInt());
    }

    @Test
    public void testMultipleClose() throws IOException {
        final Path path = Paths.get("target/tmp/test/testConsumerMultiClose.json");
        path.toFile().deleteOnExit();
        Files.deleteIfExists(path);
        final JsonBenchmarkConsumer consumer = new JsonBenchmarkConsumer(path);

        final Result result = DataCreator.createResult();
        consumer.accept(result);
        consumer.close();
        // Should not throw an exception
        consumer.close();
    }

    @Test
    public void testCreatesParentDirs() throws IOException {
        final Path root = Paths.get("target/tmp/testCreatesParentDirs");
        root.toFile().mkdirs();

        final Path path = root.resolve("another/directory/testConsumerMultiClose.json");
        path.toFile().deleteOnExit();
        Files.walk(root)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);

        final JsonBenchmarkConsumer consumer = new JsonBenchmarkConsumer(path);
        final Result result = DataCreator.createResult();
        consumer.accept(result);
        consumer.close();

        Assert.assertTrue(Files.exists(Paths.get("target/tmp/testCreatesParentDirs/another/directory")));
    }

    @Test(expected = IllegalStateException.class)
    public void testWriteAfterClose() throws IOException {
        final Path path = Paths.get("target/tmp/test/testConsumerMultiClose.json");
        path.toFile().deleteOnExit();
        Files.deleteIfExists(path);
        final JsonBenchmarkConsumer consumer = new JsonBenchmarkConsumer(path);

        final Result result = DataCreator.createResult();
        consumer.accept(result);
        consumer.close();
        // Should throw
        consumer.accept(result);
    }

    @Test
    public void testWriteInvalidFile() throws IOException {
        final Path path = Paths.get("target/tmp/test/does_not_exist");
        final JsonBenchmarkConsumer consumer = new JsonBenchmarkConsumer(path);

        final Result result = DataCreator.createResult();
        consumer.accept(result);
        consumer.close();
    }

    @Test
    public void testGetProfileFileFirst() {
        final Optional<Path> profileFile = new CustomBenchmarkConsumer(
                "-agentlib:hprof=file=target/perf.profile.hprof.txt,cpu=samples,depth=15,interval=5,force=n").getProfileFile();
        Assert.assertTrue(profileFile.isPresent());
        Assert.assertEquals("target/perf.profile.hprof.txt", profileFile.get().toString());
    }

    @Test
    public void testGetProfileFileLast() {
        final Optional<Path> profileFile = new CustomBenchmarkConsumer(
                "-agentlib:hprof=cpu=samples,depth=15,interval=5,force=n,file=target/perf.profile.hprof.txt").getProfileFile();
        Assert.assertTrue(profileFile.isPresent());
        Assert.assertEquals("target/perf.profile.hprof.txt", profileFile.get().toString());
    }

    @Test
    public void testGetProfileFileMiddle() {
        final Optional<Path> profileFile = new CustomBenchmarkConsumer(
                "-agentlib:hprof=cpu=samples,depth=15,interval=5,file=target/perf.profile.hprof.txt,force=n").getProfileFile();
        Assert.assertTrue(profileFile.isPresent());
        Assert.assertEquals("target/perf.profile.hprof.txt", profileFile.get().toString());
    }

    @Test
    public void testGetProfileNoProfileFile() {
        final Optional<Path> profileFile = new CustomBenchmarkConsumer(
                "-agentlib:hprof=cpu=samples,depth=15,interval=5,force=n").getProfileFile();
        Assert.assertFalse(profileFile.isPresent());
    }

    @Test
    public void testGetProcessId() throws IOException {
        final JsonBenchmarkConsumer consumer = new JsonBenchmarkConsumer(
                Files.createTempFile("testGetProcessId", ".hprof"));

        Assert.assertEquals(consumer.getProcessId(), consumer.getProcessId());
    }

    @Test
    public void testGetFileSize() throws IOException {
        final Path tmpFile = Files.createTempFile("testGetFileSize", ".tmp");
        final String content = "This is the file content";
        Files.write(tmpFile, content.getBytes(StandardCharsets.UTF_8));

        final JsonBenchmarkConsumer consumer = new JsonBenchmarkConsumer(
                Files.createTempFile("testGetFileSize", ".hprof"));

        final long fileSize = consumer.getFileSize(tmpFile);
        Assert.assertEquals(content.getBytes(StandardCharsets.UTF_8).length, fileSize);
    }

    @Test
    public void testWaitForOutputWriteTimeout() throws IOException {
        final Path tmpFile = Files.createTempFile("testWaitForOutputWriteTimeout", ".tmp");
        Files.write(tmpFile, new byte[0]);

        final JsonBenchmarkConsumer consumer = new JsonBenchmarkConsumer(
                Files.createTempFile("testWaitForOutputTimeout", ".hprof"));

        final long timeBefore = System.nanoTime();
        consumer.waitForOutput(tmpFile, Duration.ofMillis(1100), Duration.ofMillis(2000), Duration.ofMillis(100));
        final long timeAfter = System.nanoTime();

        // The write timeout should have been exhausted
        Assert.assertTrue(timeAfter - timeBefore > 1000);
    }

    @Test
    public void testWaitForOutputTotalTimeout() throws IOException {
        final Path tmpFile = Files.createTempFile("testWaitForOutputTotalTimeout", ".tmp");
        Files.write(tmpFile, new byte[0]);

        final JsonBenchmarkConsumer consumer = new JsonBenchmarkConsumer(
                Files.createTempFile("testWaitForOutputTotalTimeout", ".hprof"));

        final long timeBefore = System.nanoTime();
        consumer.waitForOutput(tmpFile, Duration.ofMillis(2000), Duration.ofMillis(1100), Duration.ofMillis(100));
        final long timeAfter = System.nanoTime();

        // The write timeout should have been exhausted
        Assert.assertTrue(timeAfter - timeBefore > 1000);
    }

    @Test
    public void testWaitForOutputTotalTimeoutWhileWriting() throws IOException, InterruptedException {
        final Path tmpFile = Files.createTempFile("testWaitForOutputTotalTimeoutWhileWriting", ".tmp");
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        Files.write(tmpFile, new byte[0]);

        executorService.submit(() -> {
            final StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0; i < 6; ++i) {

                try {
                    Thread.sleep(200);
                    stringBuilder.append("x");
                    Files.write(
                            tmpFile,
                            stringBuilder.toString().getBytes(Charset.defaultCharset()),
                            StandardOpenOption.APPEND);
                } catch (final IOException | InterruptedException e) {
                    return;
                }
            }
        });

        final JsonBenchmarkConsumer consumer = new JsonBenchmarkConsumer(
                Files.createTempFile("testWaitForOutputTotalTimeoutWhileWriting", ".hprof"));

        final long timeBefore = System.nanoTime();
        consumer.waitForOutput(tmpFile, Duration.ofMillis(400), Duration.ofMillis(1100), Duration.ofMillis(100));
        final long timeAfter = System.nanoTime();

        // The total timeout should have been exhausted
        Assert.assertTrue(timeAfter - timeBefore > 1000);

        executorService.shutdown();
        executorService.awaitTermination(500, TimeUnit.MILLISECONDS);
    }

    private static class CustomBenchmarkConsumer extends JsonBenchmarkConsumer {
        CustomBenchmarkConsumer(final String argument) {
            super(Paths.get("./target"));
            _argument = argument;
        }

        protected List<String> getJvmArguments() {
            return Arrays.asList(_argument);
        }

        private final String _argument;
    }
}
