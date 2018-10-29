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

import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;

/**
 * Tests the {@link Trace} class.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public final class TraceTest {

    @Test
    public void testShouldFilter() {
        // Filter empty traces
        final Trace emptyTrace = new Trace(123);
        Assert.assertTrue(emptyTrace.shouldFilter());

        // Filter traces starting with: sun.nio
        final Trace sunNioTrace = new Trace(123);
        sunNioTrace.addStackLine("sun.nio.foo.bar");
        Assert.assertTrue(sunNioTrace.shouldFilter());

        // Filter traces starting with: sun.misc.Unsafe
        final Trace sunMiscTrace = new Trace(123);
        sunMiscTrace.addStackLine("sun.misc.Unsafe.foo.bar");
        Assert.assertTrue(sunMiscTrace.shouldFilter());

        // Don't filter any other traces
        Assert.assertFalse(new Trace(123).addStackLine("com.java").shouldFilter());
        Assert.assertFalse(new Trace(123).addStackLine("sun.foo").shouldFilter());
    }

    @Test
    public void testEmitEmpty() throws IOException {
        final Trace trace = new Trace(123);
        final StringWriter stringWriter = new StringWriter();
        final BufferedWriter bufferedWriter = new BufferedWriter(stringWriter);

        trace.emit(bufferedWriter);
        bufferedWriter.close();

        Assert.assertEquals("TRACE 123:\n", stringWriter.toString());
    }

    @Test
    public void testEmitFrames() throws IOException {
        final Trace trace = new Trace(123).addStackLine("foo").addStackLine("bar");
        final StringWriter stringWriter = new StringWriter();
        final BufferedWriter bufferedWriter = new BufferedWriter(stringWriter);

        trace.emit(bufferedWriter);
        bufferedWriter.close();

        Assert.assertEquals("TRACE 123:\nfoo\nbar\n", stringWriter.toString());
    }

    @Test
    public void testGetId() {
        final Trace t = new Trace(123);
        Assert.assertEquals(123, t.getId());
    }

    @Test
    public void testHash() {
        Assert.assertEquals(new Trace(1).hashCode(), new Trace(1).hashCode());
        Assert.assertEquals(
                new Trace(1).addStackLine("foo").hashCode(),
                new Trace(1).addStackLine("foo").hashCode());
    }

    @Test
    public void testEquality() {
        final Trace t = new Trace(1);
        Assert.assertTrue(t.equals(t));

        Assert.assertFalse(new Trace(1).equals(null));
        Assert.assertFalse(new Trace(1).equals("foo"));

        Assert.assertEquals(new Trace(1), new Trace(1));
        Assert.assertEquals(
                new Trace(1)
                        .addStackLine("foo"),
                new Trace(1)
                        .addStackLine("foo"));

        Assert.assertNotEquals(new Trace(1), new Trace(2));
        Assert.assertNotEquals(
                new Trace(1)
                        .addStackLine("foo"),
                new Trace(2)
                        .addStackLine("foo"));
        Assert.assertNotEquals(
                new Trace(1)
                        .addStackLine("foo"),
                new Trace(1)
                        .addStackLine("bar"));
        Assert.assertNotEquals(
                new Trace(1)
                        .addStackLine("foo")
                        .addStackLine("bar"),
                new Trace(1)
                        .addStackLine("foo"));
    }

    @Test
    public void testToString() {
        final String traceWithoutStackLinesAsString = new Trace(1).toString();
        Assert.assertNotNull(traceWithoutStackLinesAsString);
        Assert.assertFalse(traceWithoutStackLinesAsString.isEmpty());

        final String traceWithStackLinesAsString = new Trace(1).addStackLine("foo").toString();
        Assert.assertNotNull(traceWithStackLinesAsString);
        Assert.assertFalse(traceWithStackLinesAsString.isEmpty());
    }
}
