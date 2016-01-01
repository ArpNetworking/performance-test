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

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A class to filter hprof results to be somewhat useful.
 *
 * @author Brandon Arp (barp at groupon dot com)
 */
public final class HProfFilter {
    /**
     * Entry point.
     *
     * @param args Command line args.
     */
    public static void main(final String[] args) {
        if (args.length == 0 || args.length > 3) {
            System.out.println("Invalid program arguments");
            System.out.println("- First argument must be the report path");
            System.out.println("- Second argument is optional filtered path");
            System.out.println("- Third argument is optional index");
        }
        final Path report = Paths.get(args[0]);
        final Optional<Path> result = Optional.ofNullable(args.length > 1 ? Paths.get(args[1]) : null);
        final Optional<Integer> index = Optional.ofNullable(args.length > 2 ? Integer.parseInt(args[2]) : null);
        final HProfFilter filter = new HProfFilter(report, result, index);
        try {
            filter.run();
        } catch (final IOException e) {
            System.err.println("IO Exception: " + e);
        }
    }

    /* package private for testing */ HProfFilter(final Path report, final Optional<Path> result, final Optional<Integer> index) {
        _report = report;
        if (result.isPresent()) {
            _result = result.get();
        } else {
            final String reportNoExt = com.google.common.io.Files.getNameWithoutExtension(_report.toString());
            final String reportExt = com.google.common.io.Files.getFileExtension(_report.toString());
            final String resultName = reportNoExt + ".filtered." + reportExt;
            _result = _report.toAbsolutePath().normalize().resolveSibling(resultName);
        }
        _index = index.orElse(0);
    }

    /* package private for testing */ void run() throws IOException {
        LOGGER.info(String.format("Report file %s", _report));
        LOGGER.info(String.format("Result file is %s", _result));

        try (
            final BufferedReader reader = Files.newBufferedReader(_report, Charsets.UTF_8);
            final BufferedWriter writer = Files.newBufferedWriter(_result, Charsets.UTF_8)) {
            readHeader(reader, writer);

            // Traces are reused; collect them all
            final Map<Integer, Trace> traces = Maps.newLinkedHashMap();

            // Skip to the desired index
            for (int i = 0; i < _index; ++i) {
                LOGGER.info(String.format("Skipping section %d", i));
                readAndDiscardBlock(reader, traces);
            }

            // The next thing in the file is the trace definitions
            readTraces(reader, writer, traces);

            // The next thing in the file is the samples
            final Samples samples = new Samples();
            final String date = readSamples(reader, writer, samples);

            // Now filter and output the traces and samples
            samples.emit(writer, traces, date);
        }
    }

    private String readSamples(
            final BufferedReader reader,
            final BufferedWriter writer,
            final Samples samples)
            throws IOException {
        String line;
        int totalSamples = 0;
        String date = "";
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("THREAD")) {
                // Discard
                continue;
            } else if (line.startsWith("CPU SAMPLES BEGIN")) {
                // Trim the "CPU SAMPLES BEGIN (total = " off the front
                String totals = line.substring(line.indexOf('(') + 9);
                totals = totals.substring(0, totals.indexOf(')'));
                totalSamples = Integer.parseInt(totals);
                final int dateStartIndex = line.indexOf(')') + 2;
                date = line.substring(dateStartIndex);
            } else if (line.trim().startsWith("rank")) {
                // Do nothing
                continue;
            } else if (line.startsWith("CPU SAMPLES END")) {
                // Done reading samples
                break;
            } else {
                samples.addLine(line);
            }
        }

        return date;
    }

    private void readTraces(
            final BufferedReader reader,
            final BufferedWriter writer,
            final Map<Integer, Trace> traces) throws IOException {

        String line;
        Trace trace = null;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("THREAD")) {
                // Discard
                continue;
            } else if (line.startsWith("TRACE")) {
                trace = createTrace(line);
                traces.put(trace.getId(), trace);
                reader.mark(READ_AHEAD_LIMIT);
            } else if (line.startsWith("CPU SAMPLES BEGIN")) {
                reader.reset();
                break;
            } else {
                trace.addStackLine(line);
                reader.mark(READ_AHEAD_LIMIT);
            }
        }
    }

    private void readHeader(final BufferedReader reader, final BufferedWriter writer) throws IOException {
        String line;
        boolean passedHeader = false;
        // Skip until we see a ------ line
        while ((line = reader.readLine()) != null) {
            writer.write(line);
            writer.newLine();
            if (line.startsWith("-----")) {
                passedHeader = true;
            } else if (passedHeader) {
                break;
            }
        }
    }

    private void readAndDiscardBlock(final BufferedReader reader, final Map<Integer, Trace> traces) throws IOException {
        // Consume everything up to and including the next end of samples
        String line;
        Trace trace = null;
        do {
            line = reader.readLine();
            if (line == null) {
                break;
            } else if (line.startsWith("TRACE")) {
                trace = createTrace(line);
                traces.put(trace.getId(), trace);
            } else if (line.startsWith("CPU SAMPLES BEGIN") || line.startsWith("THREAD")) {
                trace = null;
            } else if (trace != null) {
                trace.addStackLine(line);
            }
        } while (!line.startsWith("CPU SAMPLES END"));
    }

    private static Trace createTrace(final String traceLine) {
        final String[] split = traceLine.split(" ");
        if (split.length == 1) {
            throw new IllegalArgumentException(String.format("Trace line does not appear to be valid: %s", traceLine));
        }

        final int traceNumber = Integer.parseInt(split[1].replace(":", ""));
        return new Trace(traceNumber);
    }

    private final Path _report;
    private final Path _result;
    private final int _index;

    private static final int READ_AHEAD_LIMIT = 256 * 1024;
    private static final Logger LOGGER = LoggerFactory.getLogger(HProfFilter.class);

    private static final class Trace {
        private Trace(final int id) {
            _id = id;
        }

        public void addStackLine(final String line) {
            _stackLines.add(line);
        }

        public int getId() {
            return _id;
        }

        public boolean shouldFilter() {
            final String topLine = _stackLines.get(0).trim();
            if (topLine.startsWith("sun.nio")) {
                return true;
            } else if (topLine.startsWith("sun.misc.Unsafe")) {
                return true;
            }
            return false;
        }

        public void emit(final BufferedWriter writer) throws IOException {
            if (!shouldFilter()) {
                writer.write(String.format("TRACE %d:", _id));
                writer.newLine();
                for (final String stackLine : _stackLines) {
                    writer.write(stackLine);
                    writer.newLine();
                }
            }
        }

        private final int _id;
        private final List<String> _stackLines = Lists.newArrayList();
    }

    private static class Samples {

        public void addLine(final String line) {
            final List<String> strings = _splitter.splitToList(line);
            if (strings.size() != 6) {
                throw new IllegalArgumentException(String.format("Samples entry does not appear to be valid: %s", line));
            }
            final int count = Integer.parseInt(strings.get(3));
            final int trace = Integer.parseInt(strings.get(4));
            final String method = strings.get(5);
            _samples.add(new Sample(count, trace, method));
        }

        public void emit(
                final BufferedWriter writer,
                final Map<Integer, Trace> traces,
                final String date) throws IOException {

            if (_samples.isEmpty()) {
                return;
            }

            final Set<Integer> relevantTraces = Sets.newHashSet();
            final List<Sample> filteredSamples = FluentIterable.from(_samples)
                    .filter(
                            input -> {
                                if (input == null) {
                                    return false;
                                }
                                final Trace trace = traces.get(input.getTrace());
                                if (trace == null || trace.shouldFilter()) {
                                    return false;
                                }
                                relevantTraces.add(trace.getId());
                                return true;
                            })
                    .toSortedList((s1, s2) -> Integer.compare(s2.getCount(), s1.getCount()));

            LOGGER.info(String.format("Emitting %d relevant traces", relevantTraces.size()));
            for (final Map.Entry<Integer, Trace> entry : traces.entrySet()) {
                if (relevantTraces.contains(entry.getKey())) {
                    entry.getValue().emit(writer);
                }
            }

            final long filteredSamplesCount = filteredSamples.stream().collect(Collectors.summingLong(s -> s._count));
            LOGGER.info(String.format("Emitting %d filtered samples", filteredSamples.size()));

            writer.write(String.format("CPU SAMPLES BEGIN (total = %d) %s", filteredSamplesCount, date));
            writer.newLine();

            writer.write("rank   self  accum   count trace method");
            writer.newLine();

            final String sampleFormat = "%4d %5.2f%% %5.2f%% %7d %5d %s";

            int rank = 1;
            double accum = 0;
            for (final Sample sample : filteredSamples) {
                final double perc = (double) sample.getCount() / filteredSamplesCount * 100;
                accum += perc;
                writer.write(String.format(sampleFormat, rank, perc, accum, sample.getCount(), sample.getTrace(), sample.getMethod()));
                writer.newLine();
                ++rank;
            }

            writer.write("CPU SAMPLES END");
            writer.newLine();
        }

        private final Splitter _splitter = Splitter.on(" ").omitEmptyStrings().trimResults().limit(6);
        private final List<Sample> _samples = Lists.newArrayList();

        private static final class Sample {
            private Sample(final int count, final int trace, final String method) {
                _count = count;
                _trace = trace;
                _method = method;
            }

            public int getCount() {
                return _count;
            }

            public int getTrace() {
                return _trace;
            }

            public String getMethod() {
                return _method;
            }

            private final int _count;
            private final int _trace;
            private final String _method;
        }
    }
}
