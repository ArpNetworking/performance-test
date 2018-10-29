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

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Representation for a hprof trace line.
 *
 * @author Brandon Arp (barp at groupon dot com)
 */
final class Trace {

    /**
     * Add a stack line to this trace.
     *
     * @param line the stack line
     * @return this {@link Trace} instance
     */
    public Trace addStackLine(final String line) {
        _stackLines.add(line);
        return this;
    }

    /**
     * Whether the trace should be filtered out.
     *
     * @return {@code true} if and only if this trace should be filtered out
     */
    public boolean shouldFilter() {
        if (_stackLines.isEmpty()) {
            return true;
        }
        final String topLine = _stackLines.get(0).trim();
        if (topLine.startsWith("sun.nio")) {
            return true;
        } else if (topLine.startsWith("sun.misc.Unsafe")) {
            return true;
        }
        return false;
    }

    /**
     * Emit the trace to a {@code BufferedWriter}. Caller likely should check
     * whether the trace should be filtered before emitting it.
     *
     * @param writer the {@code BufferedWriter} to emit this {@link Trace} to
     * @throws IOException if an error occurs writing this {@link Trace}
     */
    public void emit(final BufferedWriter writer) throws IOException {
        writer.write(String.format("TRACE %d:", _id));
        writer.newLine();
        for (final String stackLine : _stackLines) {
            writer.write(stackLine);
            writer.newLine();
        }
    }

    public int getId() {
        return _id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_id, _stackLines);
    }

    @Override
    public boolean equals(final Object other) {
        if (other == this) {
            return true;
        }

        if (!(other instanceof Trace)) {
            return false;
        }

        final Trace otherTrace = (Trace) other;
        return Objects.equals(_id, otherTrace._id)
                && Objects.equals(_stackLines, otherTrace._stackLines);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", _id)
                .add("stackLines", _stackLines)
                .toString();
    }

    Trace(final int id) {
        _id = id;
    }

    private final int _id;
    private final List<String> _stackLines = Lists.newArrayList();
}
