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

import com.google.common.base.Charsets;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarOutputStream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Tests the {@link ContainerFileReader} class.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
@SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
public final class ContainerFileReaderTest {

    @Mock
    private DockerClient _dockerClient;
    @Mock
    private Container _container;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGetContainerFileReader() throws DockerException, InterruptedException, IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final TarOutputStream outputStream = new TarOutputStream(byteArrayOutputStream);
        final TarEntry tarEntry = new TarEntry("/var/tmp/foo");
        tarEntry.setSize("file contents".getBytes(Charsets.UTF_8).length);
        outputStream.putNextEntry(tarEntry);
        outputStream.write("file contents".getBytes(Charsets.UTF_8));
        outputStream.closeEntry();
        outputStream.close();

        final InputStream inputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
        final Path file = Paths.get("/var/tmp/foo");
        Mockito.doReturn("123").when(_container).id();
        Mockito.doReturn(inputStream).when(_dockerClient).archiveContainer("123", file.toString());

        try (BufferedReader reader = new BufferedReader(new ContainerFileReader(_dockerClient, _container, file))) {
            Assert.assertEquals("file contents", reader.readLine());
        }
    }

    @Test
    public void testGetContainerFileReaderNotFile() throws DockerException, InterruptedException, IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final TarOutputStream outputStream = new TarOutputStream(byteArrayOutputStream);
        outputStream.putNextEntry(new TarEntry("/var/tmp/"));
        outputStream.closeEntry();
        outputStream.close();

        final InputStream inputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
        final Path file = Paths.get("/var/tmp/");
        Mockito.doReturn("123").when(_container).id();
        Mockito.doReturn(inputStream).when(_dockerClient).archiveContainer("123", file.toString());

        try {
            new ContainerFileReader(_dockerClient, _container, file);
            Assert.fail("Expected exception not thrown");
        } catch (final IOException e) {
            // Expected exception
        }
    }

    @Test
    public void testGetContainerFileReaderNoArchive() throws DockerException, InterruptedException, IOException {
        final InputStream inputStream = Mockito.mock(InputStream.class);
        final Path file = Paths.get("/var/tmp/foo");
        Mockito.doReturn("123").when(_container).id();
        Mockito.doReturn(inputStream).when(_dockerClient).archiveContainer("123", file.toString());
        Mockito.doReturn(-1).when(inputStream).read();
        Mockito.doReturn(-1).when(inputStream).read(Mockito.any());
        Mockito.doReturn(-1).when(inputStream).read(Mockito.any(), Mockito.anyInt(), Mockito.anyInt());

        try {
            new ContainerFileReader(_dockerClient, _container, file);
            Assert.fail("Expected exception not thrown");
        } catch (final IOException e) {
            // Expected exception
        }
    }

    @Test
    public void testGetContainerFileReaderFailure() throws DockerException, InterruptedException, IOException {
        final Path file = Paths.get("/var/tmp/foo");
        Mockito.doReturn("123").when(_container).id();
        Mockito.doThrow(new DockerException("fail")).when(_dockerClient).archiveContainer("123", file.toString());
        try {
            new ContainerFileReader(_dockerClient, _container, file);
            Assert.fail("Expected exception not thrown");
        } catch (final IOException e) {
            // Expected exception
        }
    }
}
