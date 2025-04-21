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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerConfig;
import com.google.common.collect.ImmutableList;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
    private InspectContainerResponse _containerInfo;
    @Mock
    private ContainerConfig _containerConfig;
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
    @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
    public void testGetFileSize() throws DockerException, IOException {
        Mockito.doReturn("running").when(_container).getState();
        Mockito.doReturn("foobar").when(_container).getImage();
        Mockito.doReturn("my-id").when(_container).getId();
        final ListContainersCmd listContainerMock = Mockito.mock(ListContainersCmd.class);
        Mockito.doReturn(listContainerMock).when(_dockerClient).listContainersCmd();
        Mockito.doReturn(Collections.singletonList(_container)).when(listContainerMock).exec();

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
        final CopyArchiveFromContainerCmd copyArchiveMock = Mockito.mock(CopyArchiveFromContainerCmd.class);
        Mockito.doReturn(copyArchiveMock).when(_dockerClient).copyArchiveFromContainerCmd("my-id", file.toString());
        Mockito.doReturn(inputStream).when(copyArchiveMock).exec();

        final DockerJsonBenchmarkConsumer consumer = new DockerJsonBenchmarkConsumer(
                Paths.get("./target/tmp/testGetFileSize.tmp"),
                Pattern.compile("foo.*"),
                _dockerClient);

        Assert.assertEquals("file contents".length(), consumer.getFileSize(file));
    }

    @Test
    public void testGetFileSizeNoContainer() throws DockerException, InterruptedException {
        Mockito.doReturn("running").when(_container).getState();
        Mockito.doReturn("foobar").when(_container).getImage();
        Mockito.doReturn("my-id").when(_container).getId();
        final ListContainersCmd listContainerMock = Mockito.mock(ListContainersCmd.class);
        Mockito.doReturn(listContainerMock).when(_dockerClient).listContainersCmd();
        Mockito.doReturn(Collections.singletonList(_container)).when(listContainerMock).exec();

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
        Mockito.doReturn("running").when(_container).getState();
        Mockito.doReturn("foobar").when(_container).getImage();
        Mockito.doReturn("my-id").when(_container).getId();
        final ListContainersCmd listContainerMock = Mockito.mock(ListContainersCmd.class);
        Mockito.doReturn(listContainerMock).when(_dockerClient).listContainersCmd();
        Mockito.doReturn(Collections.singletonList(_container)).when(listContainerMock).exec();

        final InspectContainerCmd inspectContainerMock = Mockito.mock(InspectContainerCmd.class);
        Mockito.doReturn(inspectContainerMock).when(_dockerClient).inspectContainerCmd("my-id");
        Mockito.doReturn(_containerInfo).when(inspectContainerMock).exec();
        Mockito.doReturn(new String[]{"jvm-arg"}).when(_containerInfo).getArgs();
        Mockito.doReturn(_containerConfig).when(_containerInfo).getConfig();
        Mockito.doReturn(new String[]{"env-arg"}).when(_containerConfig).getEnv();

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
        Mockito.doReturn("running").when(_container).getState();
        Mockito.doReturn("foobar").when(_container).getImage();
        final ListContainersCmd listContainerMock = Mockito.mock(ListContainersCmd.class);
        Mockito.doReturn(listContainerMock).when(_dockerClient).listContainersCmd();
        Mockito.doReturn(Collections.singletonList(_container)).when(listContainerMock).exec();

        final DockerJsonBenchmarkConsumer consumer = new DockerJsonBenchmarkConsumer(
                Paths.get("./target/tmp/testGetJvmArgumentsNoContainer.tmp"),
                Pattern.compile("bar.*"),
                _dockerClient);

        Assert.assertTrue(consumer.getJvmArguments().isEmpty());
    }

    @Test
    public void testGetJvmArgumentsDockerFailure() throws DockerException, InterruptedException {
        Mockito.doReturn("running").when(_container).getState();
        Mockito.doReturn("foobar").when(_container).getImage();
        Mockito.doReturn("my-id").when(_container).getId();
        final ListContainersCmd listContainerMock = Mockito.mock(ListContainersCmd.class);
        Mockito.doReturn(listContainerMock).when(_dockerClient).listContainersCmd();
        Mockito.doReturn(Collections.singletonList(_container)).when(listContainerMock).exec();
        Mockito.doThrow(new DockerException("Test", 500)).when(_dockerClient).inspectContainerCmd("my-id");

        final DockerJsonBenchmarkConsumer consumer = new DockerJsonBenchmarkConsumer(
                Paths.get("./target/tmp/testGetJvmArgumentsDockerFailure.tmp"),
                Pattern.compile("foo.*"),
                _dockerClient);

        Assert.assertTrue(consumer.getJvmArguments().isEmpty());
        Mockito.verify(_dockerClient).listContainersCmd();
        Mockito.verify(_dockerClient).inspectContainerCmd("my-id");
        Mockito.verifyNoInteractions(_containerInfo);
        Mockito.verifyNoInteractions(_containerConfig);
    }

    @Test
    public void testGetContainerFound() throws DockerException, InterruptedException {
        Mockito.doReturn("running").when(_container).getState();
        Mockito.doReturn("foobar").when(_container).getImage();
        final ListContainersCmd listContainerMock = Mockito.mock(ListContainersCmd.class);
        Mockito.doReturn(listContainerMock).when(_dockerClient).listContainersCmd();
        Mockito.doReturn(Collections.singletonList(_container)).when(listContainerMock).exec();
        Assert.assertTrue(DockerJsonBenchmarkConsumer.getContainer(_dockerClient, Pattern.compile("foo.*")).isPresent());
    }

    @Test
    public void testGetContainerNone() throws DockerException, InterruptedException {
        final ListContainersCmd listContainerMock = Mockito.mock(ListContainersCmd.class);
        Mockito.doReturn(listContainerMock).when(_dockerClient).listContainersCmd();
        Mockito.doReturn(Collections.singletonList(_container)).when(listContainerMock).exec();
        Assert.assertFalse(DockerJsonBenchmarkConsumer.getContainer(_dockerClient, Pattern.compile(".*")).isPresent());
    }

    @Test
    public void testGetContainerNotRunning() throws DockerException, InterruptedException {
        Mockito.doReturn("stopped").when(_container).getState();
        Mockito.doReturn("image").when(_container).getImage();
        final ListContainersCmd listContainerMock = Mockito.mock(ListContainersCmd.class);
        Mockito.doReturn(listContainerMock).when(_dockerClient).listContainersCmd();
        Mockito.doReturn(Collections.singletonList(_container)).when(listContainerMock).exec();
        Assert.assertFalse(DockerJsonBenchmarkConsumer.getContainer(_dockerClient, Pattern.compile(".*")).isPresent());
    }

    @Test
    public void testGetContainerNotMatched() throws DockerException, InterruptedException {
        Mockito.doReturn("running").when(_container).getState();
        Mockito.doReturn("image").when(_container).getImage();
        final ListContainersCmd listContainerMock = Mockito.mock(ListContainersCmd.class);
        Mockito.doReturn(listContainerMock).when(_dockerClient).listContainersCmd();
        Mockito.doReturn(Collections.singletonList(_container)).when(listContainerMock).exec();
        Assert.assertFalse(DockerJsonBenchmarkConsumer.getContainer(_dockerClient, Pattern.compile("foo.*")).isPresent());
    }

    @Test
    public void testGetContainerFailure() throws DockerException, InterruptedException {
        final ListContainersCmd listContainerMock = Mockito.mock(ListContainersCmd.class);
        Mockito.doReturn(listContainerMock).when(_dockerClient).listContainersCmd();
        Mockito.doReturn(Collections.singletonList(_container)).when(listContainerMock).exec();
        Assert.assertFalse(DockerJsonBenchmarkConsumer.getContainer(_dockerClient, Pattern.compile(".*")).isPresent());
    }
}
