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
import com.arpnetworking.commons.java.util.function.SingletonSupplier;
import com.carrotsearch.junitbenchmarks.AutocloseConsumer;
import com.carrotsearch.junitbenchmarks.GCSnapshot;
import com.carrotsearch.junitbenchmarks.Result;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Writes a JSON file with benchmarking results.
 *
 * @author Brandon Arp (barp at groupon dot com)
 * @author Ville Koskela (vkoskela at groupon dot com)
 */
public class JsonBenchmarkConsumer extends AutocloseConsumer implements Closeable {

    private static final AtomicInteger NEXT_PROFILE_INDEX = new AtomicInteger(0);
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createInstance();
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonBenchmarkConsumer.class);
    private static final Pattern HPROF_FILE_PATTERN = Pattern.compile(".*-agentlib:hprof=(.*,)?file=([^,]*).*");

    static {
        final SimpleModule simpleModule = new SimpleModule();
        simpleModule.addSerializer(GCSnapshot.class, new GCSnapshotSerializer());
        OBJECT_MAPPER.registerModule(simpleModule);
        OBJECT_MAPPER.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
    }

    private final Supplier<Optional<Path>> _profileFileSupplier =
            new SingletonSupplier<>(() -> extractProfileFile(getJvmArguments()));

    private final Supplier<Long> _processIdProvier = new SingletonSupplier<>(() -> {
        final String processName = ManagementFactory.getRuntimeMXBean().getName();
        return Long.valueOf(processName.split("@")[0]);
    });

    private final List<Result> _resultsWithoutProfileData = Lists.newArrayList();
    private final Map<Integer, Result> _resultsWithProfileData = Maps.newHashMap();
    private final Path _path;
    private final boolean _append;

    private boolean _closed = false;

    /**
     * Public constructor. Overwrites the file by default.
     *
     * @param path {@code Path} of the file to write
     */
    public JsonBenchmarkConsumer(final Path path) {
        this(path, false);
    }

    /**
     * Public constructor.
     *
     * @param path {@code Path} of the file to write
     * @param append whether to append to the file or overwrite
     */
    public JsonBenchmarkConsumer(final Path path, final boolean append) {
        _path = path;
        _append = append;
        addAutoclose(this);
    }

    @Override
    public void accept(final Result result) {
        if (_closed) {
            throw new IllegalStateException("Consumer is already closed");
        }
        final Optional<Path> profileDataFile = getProfileFile();
        if (profileDataFile.isPresent()) {
            // Dump the profile data for the test and store the result
            final int index = dumpProfileData(profileDataFile.get());
            LOGGER.info(String.format(
                    "Accepted profiled results for %s.%s in %s at %d",
                    result.getTestClassName(),
                    result.getTestMethodName(),
                    profileDataFile.get(),
                    index));
            _resultsWithProfileData.put(index, result);

        } else {
            // Augment results without profile file
            LOGGER.info(String.format(
                    "Accepted non-profiled results for %s.%s",
                    result.getTestClassName(),
                    result.getTestMethodName()));
            _resultsWithoutProfileData.add(result);
        }
    }

    @Override
    public void close() {
        if (!_closed) {
            try {
                // Create the output path
                final Path outputDirectory = _path.getParent();
                final String nameWithoutExtension = com.google.common.io.Files.getNameWithoutExtension(_path.toString());
                ensurePathExists();

                // Merge the results
                final List<AugmentedResult> augmentedResults = Lists.newArrayListWithExpectedSize(
                        _resultsWithoutProfileData.size() + _resultsWithProfileData.size());

                // Simply wrap the results without profile data
                augmentedResults.addAll(_resultsWithoutProfileData.stream().map(AugmentedResult::new).collect(Collectors.toList()));

                // For results with profile data extract the data and pair it with the result
                for (final Map.Entry<Integer, Result> entry : _resultsWithProfileData.entrySet()) {
                    final int index = entry.getKey();
                    final Result result = entry.getValue();

                    final Optional<Path> profileDataFile = getProfileFile();
                    if (profileDataFile.isPresent() && index >= 0) {
                        LOGGER.info(String.format(
                                "Filtering profile for %s.%s in %s at %d",
                                result.getTestClassName(),
                                result.getTestMethodName(),
                                profileDataFile.get(),
                                index));

                        final Path extractedProfileDataFile = outputDirectory.resolve(
                                nameWithoutExtension + "." + result.getTestMethodName() + ".hprof");

                        filterProfileData(profileDataFile.get(), extractedProfileDataFile, index);

                        augmentedResults.add(new AugmentedResult(result, extractedProfileDataFile));
                    } else {
                        LOGGER.warn("Profile data file lost between accept and close");
                        augmentedResults.add(new AugmentedResult(result));
                    }
                }

                // Output the test performance results
                LOGGER.info(String.format("Closing; file=%s", _path));
                final ObjectWriter objectWriter = OBJECT_MAPPER.writerWithDefaultPrettyPrinter();
                try (FileOutputStream outputStream = new FileOutputStream(_path.toString(), _append)) {
                    objectWriter.writeValue(outputStream, augmentedResults);
                    outputStream.write("\n".getBytes(StandardCharsets.UTF_8));
                }
            } catch (final IOException e) {
                LOGGER.error("Could not write json performance file", e);
            }
            _closed = true;
        } else {
            LOGGER.error("JsonBenchmarkConsumer closed multiple times");
        }
    }

    /**
     * Invoke this method before each test or test suite (e.g. {@code @Before}
     * or {@code @BeforeClass}) to dump the test preparation cost from the
     * profiling. The method has no effect if profiling is not enabled.
     * <p>
     * Whether your test should use {@code @Before} or {@code @BeforeClass}
     * will depend on the nature of your test. Generally, if you have a
     * constructor you should use {@code @Before} and if you do not you can
     * use {@code @BeforeClass}.
     */
    public void prepareClass() {
        final Optional<Path> profileDataFile = getProfileFile();
        if (profileDataFile.isPresent()) {
            LOGGER.info(String.format("Resetting profile data; file=%s", profileDataFile.get()));
            dumpProfileData(profileDataFile.get());
        }
    }

    /**
     * Extract a specified index from the profile data filter it and write it
     * to the target path.
     *
     * @param pathIn the path to the input profile data
     * @param pathOut the path to the output filtered profile data
     * @param index the index of the data set to extract and filter
     * @throws IOException if the filtering operation fails
     */
    protected void filterProfileData(final Path pathIn, final Path pathOut, final int index) throws IOException {
        new HProfFilter().run(pathIn, Optional.of(pathOut), Optional.of(index));
    }

    /**
     * Retrieve the profile data file if set.
     *
     * @return {@code Optional} profile data file
     */
    protected Optional<Path> getProfileFile() {
        return _profileFileSupplier.get();
    }

    /**
     * Dump the accumulated profile data and return the index of the profile.
     *
     * @param profileFile the target file to dump profile data to
     * @return the index of the profile just dumped
     */
    protected synchronized int dumpProfileData(final Path profileFile) {
        // *IMPORTANT* This will only work on *nix based systems. Other alternatives
        // such as Sun's private Signal API did not work.
        final int nextIndex = NEXT_PROFILE_INDEX.getAndIncrement();
        try {
            Runtime.getRuntime().exec("kill -SIGQUIT " + getProcessId());
        } catch (final IOException e) {
            LOGGER.error("Unable to dump profile data", e);
        }

        waitForOutput(
                profileFile,
                Duration.ofSeconds(3),
                Duration.ofSeconds(10),
                Duration.ofMillis(500));

        LOGGER.info(String.format(
                "Dumped profile data %d",
                nextIndex));
        return nextIndex;
    }

    /**
     * Wait for the profile data to be written to disk.
     *
     * @param file the profile file
     * @param writeTimeout the time to wait for incremental data write
     * @param totalTimeout the time to wait for complete data write
     * @param interval the time to wait between checks of the file size
     */
    protected void waitForOutput(
            final Path file,
            final Duration writeTimeout,
            final Duration totalTimeout,
            final Duration interval) {
        final ZonedDateTime start = ZonedDateTime.now();
        ZonedDateTime previousSizeTimestamp = start;
        long previousSize = -1;
        ZonedDateTime now;
        do {
            // Wait to check for changes
            try {
                Thread.sleep(interval.toMillis());
            } catch (final InterruptedException e) {
                throw new RuntimeException("Interrupted waiting for output", e);
            }

            // Check if the profile file has grown
            long currentSize;
            try {
                currentSize = getFileSize(file);
            } catch (final IOException e) {
                currentSize = -1;
            }
            now = ZonedDateTime.now();
            if (currentSize > previousSize) {
                previousSize = currentSize;
                previousSizeTimestamp = now;
            }
        } while (Duration.between(previousSizeTimestamp, now).compareTo(writeTimeout) < 0
                && Duration.between(start, now).compareTo(totalTimeout) < 0);
    }

    /**
     * Determine the size of a file.
     *
     * @param file the file to determine size of
     * @return the size of the file in bytes
     * @throws IOException if the file size cannot be determined
     */
    protected long getFileSize(final Path file) throws IOException {
        return Files.size(file);
    }

    /**
     * Extract the profile file {@code Path} from a {@code List} of arguments.
     *
     * @param arguments the arguments to search for the profile file
     * @return an {@code Optional} {@code Path} to the profile file
     */
    protected Optional<Path> extractProfileFile(final List<String> arguments) {
        for (final String argument : arguments) {
            final Matcher matcher = HPROF_FILE_PATTERN.matcher(argument);
            if (matcher.matches()) {
                final Path path = Paths.get(matcher.group(2));
                LOGGER.info(String.format("Found profile file path: %s", path));
                return Optional.of(path);
            }
        }
        LOGGER.warn("No matching profile path found!");
        return Optional.empty();
    }

    /**
     * Return the arguments to the target JVM instance.
     *
     * @return the arguments to the target JVM instance
     */
    protected List<String> getJvmArguments() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments();
    }

    protected long getProcessId() {
        return _processIdProvier.get();
    }

    private void ensurePathExists() throws IOException {
        final Path parent = _path.toAbsolutePath().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

}
