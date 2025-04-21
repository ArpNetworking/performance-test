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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Container;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarOutputStream;
import org.junit.After;
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
import java.nio.charset.StandardCharsets;
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
    private AutoCloseable _mocks;

    @Before
    public void setUp() {
        _mocks = MockitoAnnotations.openMocks(this);
    }

    @After
    public void tearDown() throws Exception {
        _mocks.close();
    }

    @Test
    public void testGetContainerFileReader() throws DockerException, IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final TarOutputStream outputStream = new TarOutputStream(byteArrayOutputStream);
        final TarEntry tarEntry = new TarEntry("/var/tmp/foo");
        tarEntry.setSize("file contents".getBytes(StandardCharsets.UTF_8).length);
        outputStream.putNextEntry(tarEntry);
        outputStream.write("file contents".getBytes(StandardCharsets.UTF_8));
        outputStream.closeEntry();
        outputStream.close();

        final InputStream inputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
        final Path file = Paths.get("/var/tmp/foo");
        Mockito.doReturn("123").when(_container).getId();
        final CopyArchiveFromContainerCmd cmd = Mockito.mock(CopyArchiveFromContainerCmd.class);
        Mockito.doReturn(cmd).when(_dockerClient).copyArchiveFromContainerCmd("123", file.toString());
        Mockito.doReturn(inputStream).when(cmd).exec();

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
        Mockito.doReturn("123").when(_container).getId();
        final CopyArchiveFromContainerCmd cmd = Mockito.mock(CopyArchiveFromContainerCmd.class);
        Mockito.doReturn(cmd).when(_dockerClient).copyArchiveFromContainerCmd("123", file.toString());
        Mockito.doReturn(inputStream).when(cmd).exec();

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
        Mockito.doReturn("123").when(_container).getId();
        final CopyArchiveFromContainerCmd cmd = Mockito.mock(CopyArchiveFromContainerCmd.class);
        Mockito.doReturn(cmd).when(_dockerClient).copyArchiveFromContainerCmd("123", file.toString());
        Mockito.doReturn(inputStream).when(cmd).exec();
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
    public void testGetContainerFileReaderFailure() throws DockerException, InterruptedException {
        final Path file = Paths.get("/var/tmp/foo");
        Mockito.doReturn("123").when(_container).getId();
        final CopyArchiveFromContainerCmd cmd = Mockito.mock(CopyArchiveFromContainerCmd.class);
        Mockito.doReturn(cmd).when(_dockerClient).copyArchiveFromContainerCmd("123", file.toString());
        Mockito.doThrow(new DockerException("fail", 500)).when(cmd).exec();
        try {
            new ContainerFileReader(_dockerClient, _container, file);
            Assert.fail("Expected exception not thrown");
        } catch (final IOException e) {
            // Expected exception
        }
    }
}
