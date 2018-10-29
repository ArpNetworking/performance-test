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
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.shaded.com.google.common.collect.ImmutableList;
import com.spotify.docker.client.shaded.javax.ws.rs.ProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Specialization of {@link JsonBenchmarkConsumer} which targets Java
 * applications under profile inside a Docker container. There are a few
 * requirements for your Docker container to use this:
 * <p>
 * <ul>
 * <li>The container must propagate signals to the target JVM process.</li>
 * <li>The container must have the {@code -agentlib:hprof} argument either in its arguments or environment.</li>
 * </ul>
 * <p>
 * The simplest way to accomplish these is to pass the {@code -agentlib:hprof} to
 * the JVM via {@code docker run} and use {@code exec} when launching your process
 * to ensure that the JVM is the root process in the container and thus will
 * receive the signal.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public class DockerJsonBenchmarkConsumer extends JsonBenchmarkConsumer {

    private static final AtomicInteger NEXT_PROFILE_INDEX = new AtomicInteger(0);
    private static final String DEFAULT_DOCKER_DAEMON_ADDRESS = "unix:///var/run/docker.sock";
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerJsonBenchmarkConsumer.class);

    private final Pattern _targetImageName;
    private final DockerClient _dockerClient;

    /**
     * Public constructor. Overwrites the file by default.
     *
     * @param path {@code Path} of the file to write
     * @param targetImageName the {@code Pattern} for matching the name of the Docker image being profiled
     */
    public DockerJsonBenchmarkConsumer(
            final Path path,
            final Pattern targetImageName) {
        this(path, false, targetImageName, DEFAULT_DOCKER_DAEMON_ADDRESS);
    }

    /**
     * Public constructor.
     *
     * @param path {@code Path} of the file to write
     * @param append whether to append to the file or overwrite
     * @param targetImageName the {@code Pattern} for matching the name of the Docker image being profiled
     * @param dockerDaemonAddress the address of the Docker daemon
     */
    public DockerJsonBenchmarkConsumer(
            final Path path,
            final boolean append,
            final Pattern targetImageName,
            final String dockerDaemonAddress) {
        super(path, append);
        _targetImageName = targetImageName;

        _dockerClient = DefaultDockerClient.builder()
                .uri(dockerDaemonAddress)
                .build();
    }

    DockerJsonBenchmarkConsumer(
            final Path path,
            final Pattern targetImageName,
            final DockerClient dockerClient) {
        super(path, true);
        _targetImageName = targetImageName;
        _dockerClient = dockerClient;
    }

    @Override
    protected void filterProfileData(final Path pathIn, final Path pathOut, final int index) throws IOException {
        // Fetch the docker container
        final Optional<Container> container = getContainer(_dockerClient, _targetImageName);
        if (!container.isPresent()) {
            LOGGER.error("Cannot filter profile data; container not found");
            return;
        }

        // Execute filtering using buffered reader and writer
        final BufferedReader reader = new ContainerFileReader(_dockerClient, container.get(), pathIn);
        try (BufferedWriter writer = Files.newBufferedWriter(pathOut, Charsets.UTF_8)) {
            new HProfFilter().run(reader, writer, index);
        } finally {
            try {
                reader.close();
            } catch (final IOException | ProcessingException e) {
                // Ignore spurious close exceptions from Docker Java client streams
            }
        }
    }

    @Override
    protected int dumpProfileData(final Path profileFile) {
        // Fetch the docker container
        final Optional<Container> container = getContainer(_dockerClient, _targetImageName);
        if (!container.isPresent()) {
            LOGGER.error("Cannot dump profile data; container not found");
            return -1;
        }

        synchronized (this) {
            // Signal the container
            final int nextIndex = NEXT_PROFILE_INDEX.getAndIncrement();
            try {
                _dockerClient.killContainer(container.get().id(), DockerClient.Signal.SIGQUIT);
            } catch (final DockerException | InterruptedException e) {
                LOGGER.error("Unable to dump profile data", e);
                return -1;
            }

            waitForOutput(profileFile, Duration.ofSeconds(3), Duration.ofSeconds(10), Duration.ofSeconds(1));

            LOGGER.info(String.format(
                    "Dumped profile data %d",
                    nextIndex));

            return nextIndex;
        }
    }

    @Override
    protected long getFileSize(final Path file) throws IOException {
        final Optional<Container> container = getContainer(_dockerClient, _targetImageName);
        if (!container.isPresent()) {
            throw new IOException("Cannot determine file size; container not found");
        }
        long fileSize = 0;
        BufferedReader reader = null;
        try {
            reader = new ContainerFileReader(_dockerClient, container.get(), file);
            while (reader.read() >= 0) {
                ++fileSize;
            }
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (final IOException | ProcessingException e) {
                // Ignore spurious close exceptions from Docker Java client streams
            }
        }
        return fileSize;
    }

    @Override
    protected List<String> getJvmArguments() {
        // Fetch the docker container
        final Optional<Container> container = getContainer(_dockerClient, _targetImageName);
        if (!container.isPresent()) {
            LOGGER.error("Cannot retrieve JVM arguments; container not found");
            return Collections.emptyList();
        }

        // Look-up the container arguments
        try {
            final ContainerInfo containerInfo = _dockerClient.inspectContainer(container.get().id());
            final ImmutableList<String> args = containerInfo.args();
            final ImmutableList<String> env = containerInfo.config().env();
            return ImmutableList.<String>builder()
                    .addAll(args)
                    .addAll(env)
                    .build();
        } catch (final DockerException | InterruptedException e) {
            LOGGER.error(
                    String.format(
                            "Docker client failed to retrieve container information: %s",
                            container.get().id()),
                    e);
            return Collections.emptyList();
        }
    }

    static Optional<Container> getContainer(
            final DockerClient dockerClient,
            final Pattern targetImagePattern) {
        try {
            for (final Container container : dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())) {
                if ("running".equals(container.state()) && targetImagePattern.matcher(container.image()).matches()) {
                    return Optional.of(container);
                }
            }
        } catch (final DockerException | InterruptedException e) {
            LOGGER.error("Docker client failed to list containers", e);
        }
        return Optional.empty();
    }
}
