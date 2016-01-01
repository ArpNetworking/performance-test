/**
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
import com.carrotsearch.junitbenchmarks.AutocloseConsumer;
import com.carrotsearch.junitbenchmarks.GCSnapshot;
import com.carrotsearch.junitbenchmarks.Result;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.io.IOUtils;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
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

    /**
     * Public constructor.
     *
     * @param path Path of the file to write.
     */
    public JsonBenchmarkConsumer(final Path path) {
        this(path, false);
    }

    /**
     * Public constructor.
     *
     * @param path Path of the file to write.
     * @param append Whether to append to the file or overwrite.
     */
    public JsonBenchmarkConsumer(final Path path, final boolean append) {
        _path = path;
        _append = append;
        addAutoclose(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void accept(final Result result) throws IOException {
        if (_closed) {
            throw new IllegalStateException("Consumer is already closed");
        }
        final Optional<Path> profileFile = getProfileFile();
        if (profileFile.isPresent()) {
            // Dump the profile data for the test and store the result
            _resultsWithProfileData.put(dumpProfileData(), result);
        } else {
            // Augment results without profile file
            _resultsWithoutProfileData.add(result);
        }
    }

    /**
     * {@inheritDoc}
     */
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
                    if (profileDataFile.isPresent()) {
                        LOGGER.info(String.format(
                                "Filtering profile for %s.%s in %s at %d",
                                result.getTestClassName(),
                                result.getTestMethodName(),
                                profileDataFile.get(),
                                index));

                        final Path extractedProfileDataFile = outputDirectory.resolve(
                                nameWithoutExtension + "." + result.getTestMethodName() + ".hprof");
                        new HProfFilter(profileDataFile.get(), Optional.of(extractedProfileDataFile), Optional.of(index)).run();

                        augmentedResults.add(new AugmentedResult(result, extractedProfileDataFile));
                    } else {
                        LOGGER.warn("Profile data file lost between accept and close");
                        augmentedResults.add(new AugmentedResult(result));
                    }
                }

                // Output the test performance results
                LOGGER.info(String.format("Closing; file=%s", _path));
                final FileOutputStream outputStream = new FileOutputStream(_path.toString(), _append);
                try {
                    OBJECT_MAPPER.writeValue(outputStream, augmentedResults);
                    outputStream.write("\n".getBytes(StandardCharsets.UTF_8));
                } finally {
                    IOUtils.closeQuietly(outputStream);
                }
            } catch (final IOException e) {
                LOGGER.error("Could not write json performance file", e);
            }
            _closed = true;
        }
    }

    /**
     * Invoke this method in each test suite's (e.g. class) @BeforeClass method to dump the test preparation cost
     * from the profiling. The method has no effect if profiling is not enabled.
     */
    public void prepareClass() {
        final Optional<Path> profileDataFile = getProfileFile();
        if (profileDataFile.isPresent()) {
            LOGGER.info(String.format("Resetting profile data; file=%s", profileDataFile.get()));
            dumpProfileData();
        }
    }

    /**
     * Retrieve the profile data file if set.
     *
     * @return Optional profile data file.
     */
    protected Optional<Path> getProfileFile() {
        for (final String argument : getJvmArguments()) {
            final Matcher matcher = HPROF_FILE_PATTERN.matcher(argument);
            if (matcher.matches()) {
                return Optional.of(Paths.get(matcher.group(2)));
            }
        }
        return Optional.empty();
    }

    /**
     * Retrieve the argument list used to launch this JVM.
     *
     * @return The argument list used to launch this JVM.
     */
    protected List<String> getJvmArguments() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments();
    }

    /**
     * Dump the accumulated profile data and return the index of the profile.
     *
     * @return The index of the profile just dumped.
     */
    protected int dumpProfileData() {
        // *IMPORTANT* This will only work on *nix based systems. Other alternatives
        // such as Sun's private Signal API did not work.
        try {
            Runtime.getRuntime().exec("kill -SIGQUIT " + getProcessId());
        } catch (final IOException e) {
            LOGGER.error("Unable to dump profile data", e);
        }
        return NEXT_PROFILE_INDEX.getAndIncrement();
    }

    /**
     * Retrieve the process id.
     *
     * @return The process id.
     */
    protected long getProcessId() {
        final String processName = ManagementFactory.getRuntimeMXBean().getName();
        return Long.parseLong(processName.split("@")[0]);
    }

    private void ensurePathExists() throws IOException {
        final Path parent = _path.toAbsolutePath().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    private boolean _closed = false;

    private final List<Result> _resultsWithoutProfileData = Lists.newArrayList();
    private final Map<Integer, Result> _resultsWithProfileData = Maps.newHashMap();
    private final Path _path;
    private final boolean _append;

    private static final AtomicInteger NEXT_PROFILE_INDEX = new AtomicInteger(0);
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createInstance();
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonBenchmarkConsumer.class);
    private static final Pattern HPROF_FILE_PATTERN = Pattern.compile("^-agentlib:hprof=(.*,)?file=([^,]*)(,.*)?$");

    static {
        final SimpleModule simpleModule = new SimpleModule();
        simpleModule.addSerializer(GCSnapshot.class, new GCSnapshotSerializer());
        OBJECT_MAPPER.registerModule(simpleModule);
        OBJECT_MAPPER.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
    }

    /**
     * Augmented <code>Result</code> wrapper.
     */
    public static class AugmentedResult {

        /**
         * Constructor for creating an augmented result.
         *
         * @param result The <code>Result</code> to extend.
         */
        public AugmentedResult(final Result result) {
            _result = result;
            _profileFile = Optional.empty();
        }

        /**
         * Constructor for creating an augmented result.
         *
         * @param result The <code>Result</code> to extend.
         * @param profileFile The profile data file.
         */
        public AugmentedResult(final Result result, final Path profileFile) {
            _result = result;
            _profileFile = Optional.of(profileFile);
        }

        public Result getResult() {
            return _result;
        }

        public Optional<Path> getProfileFile() {
            return _profileFile;
        }

        private final Result _result;
        private final Optional<Path> _profileFile;
    }
}
