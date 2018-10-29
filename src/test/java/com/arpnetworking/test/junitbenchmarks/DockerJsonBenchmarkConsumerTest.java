/*
 * Copyright 2018 Inscope Metrics Inc.
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
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.shaded.com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarOutputStream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * Tests a {@link DockerJsonBenchmarkConsumer}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public final class DockerJsonBenchmarkConsumerTest {

    @Mock
    private DockerClient _dockerClient;
    @Mock
    private Container _container;
    @Mock
    private ContainerInfo _containerInfo;
    @Mock
    private ContainerConfig _containerConfig;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
    public void testGetFileSize() throws DockerException, InterruptedException, IOException {
        Mockito.doReturn("running").when(_container).state();
        Mockito.doReturn("foobar").when(_container).image();
        Mockito.doReturn("my-id").when(_container).id();
        Mockito.doReturn(Collections.singletonList(_container)).when(_dockerClient).listContainers(Mockito.any());

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
        Mockito.doReturn(inputStream).when(_dockerClient).archiveContainer("my-id", file.toString());

        final DockerJsonBenchmarkConsumer consumer = new DockerJsonBenchmarkConsumer(
                Paths.get("./target/tmp/testGetFileSize.tmp"),
                Pattern.compile("foo.*"),
                _dockerClient);

        Assert.assertEquals("file contents".length(), consumer.getFileSize(file));
    }

    @Test
    public void testGetFileSizeNoContainer() throws DockerException, InterruptedException {
        Mockito.doReturn("running").when(_container).state();
        Mockito.doReturn("foobar").when(_container).image();
        Mockito.doReturn("my-id").when(_container).id();
        Mockito.doReturn(Collections.singletonList(_container)).when(_dockerClient).listContainers(Mockito.any());

        final DockerJsonBenchmarkConsumer consumer = new DockerJsonBenchmarkConsumer(
                Paths.get("./target/tmp/testGetFileSizeNoContainer.tmp"),
                Pattern.compile("dne.*"),
                _dockerClient);

        try {
            consumer.getFileSize(Paths.get("./target/tmp/foo.dne"));
            Assert.fail("Expected exception not thrown");
        } catch (final IOException e) {
            Assert.assertTrue(e.getMessage().contains("container not found"));
        }
    }

    @Test
    public void testGetJvmArguments() throws DockerException, InterruptedException {
        Mockito.doReturn("running").when(_container).state();
        Mockito.doReturn("foobar").when(_container).image();
        Mockito.doReturn("my-id").when(_container).id();
        Mockito.doReturn(Collections.singletonList(_container)).when(_dockerClient).listContainers(Mockito.any());
        Mockito.doReturn(_containerInfo).when(_dockerClient).inspectContainer("my-id");
        Mockito.doReturn(ImmutableList.of("jvm-arg")).when(_containerInfo).args();
        Mockito.doReturn(_containerConfig).when(_containerInfo).config();
        Mockito.doReturn(ImmutableList.of("env-arg")).when(_containerConfig).env();

        final DockerJsonBenchmarkConsumer consumer = new DockerJsonBenchmarkConsumer(
                Paths.get("./target/tmp/testGetJvmArguments.tmp"),
                Pattern.compile("foo.*"),
                _dockerClient);

        Assert.assertEquals(
                ImmutableList.of("jvm-arg", "env-arg"),
                consumer.getJvmArguments());
    }

    @Test
    public void testGetJvmArgumentsNoContainer() throws DockerException, InterruptedException {
        Mockito.doReturn("running").when(_container).state();
        Mockito.doReturn("foobar").when(_container).image();
        Mockito.doReturn(Collections.singletonList(_container)).when(_dockerClient).listContainers(Mockito.any());

        final DockerJsonBenchmarkConsumer consumer = new DockerJsonBenchmarkConsumer(
                Paths.get("./target/tmp/testGetJvmArgumentsNoContainer.tmp"),
                Pattern.compile("bar.*"),
                _dockerClient);

        Assert.assertTrue(consumer.getJvmArguments().isEmpty());
    }

    @Test
    public void testGetJvmArgumentsDockerFailure() throws DockerException, InterruptedException {
        Mockito.doReturn("running").when(_container).state();
        Mockito.doReturn("foobar").when(_container).image();
        Mockito.doReturn("my-id").when(_container).id();
        Mockito.doReturn(Collections.singletonList(_container)).when(_dockerClient).listContainers(Mockito.any());
        Mockito.doThrow(new DockerException("Test")).when(_dockerClient).inspectContainer("my-id");

        final DockerJsonBenchmarkConsumer consumer = new DockerJsonBenchmarkConsumer(
                Paths.get("./target/tmp/testGetJvmArgumentsDockerFailure.tmp"),
                Pattern.compile("foo.*"),
                _dockerClient);

        Assert.assertTrue(consumer.getJvmArguments().isEmpty());
        Mockito.verify(_dockerClient).listContainers(Mockito.any());
        Mockito.verify(_dockerClient).inspectContainer("my-id");
        Mockito.verifyZeroInteractions(_containerInfo);
        Mockito.verifyZeroInteractions(_containerConfig);
    }

    @Test
    public void testGetContainerFound() throws DockerException, InterruptedException {
        Mockito.doReturn("running").when(_container).state();
        Mockito.doReturn("foobar").when(_container).image();
        Mockito.doReturn(Collections.singletonList(_container)).when(_dockerClient).listContainers(Mockito.any());
        Assert.assertTrue(DockerJsonBenchmarkConsumer.getContainer(_dockerClient, Pattern.compile("foo.*")).isPresent());
    }

    @Test
    public void testGetContainerNone() throws DockerException, InterruptedException {
        Mockito.doReturn(Collections.emptyList()).when(_dockerClient).listContainers(Mockito.any());
        Assert.assertFalse(DockerJsonBenchmarkConsumer.getContainer(_dockerClient, Pattern.compile(".*")).isPresent());
    }

    @Test
    public void testGetContainerNotRunning() throws DockerException, InterruptedException {
        Mockito.doReturn("stopped").when(_container).state();
        Mockito.doReturn("image").when(_container).image();
        Mockito.doReturn(Collections.singletonList(_container)).when(_dockerClient).listContainers(Mockito.any());
        Assert.assertFalse(DockerJsonBenchmarkConsumer.getContainer(_dockerClient, Pattern.compile(".*")).isPresent());
    }

    @Test
    public void testGetContainerNotMatched() throws DockerException, InterruptedException {
        Mockito.doReturn("running").when(_container).state();
        Mockito.doReturn("image").when(_container).image();
        Mockito.doReturn(Collections.singletonList(_container)).when(_dockerClient).listContainers(Mockito.any());
        Assert.assertFalse(DockerJsonBenchmarkConsumer.getContainer(_dockerClient, Pattern.compile("foo.*")).isPresent());
    }

    @Test
    public void testGetContainerFailure() throws DockerException, InterruptedException {
        Mockito.doThrow(new DockerException("fail")).when(_dockerClient).listContainers(Mockito.any());
        Assert.assertFalse(DockerJsonBenchmarkConsumer.getContainer(_dockerClient, Pattern.compile(".*")).isPresent());
    }
}
