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
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Container;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * Reader for files in Docker containers.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
class ContainerFileReader extends InputStreamReader {

    /**
     * Public constructor.
     *
     * @param dockerClient the {@code DockerClient} instance
     * @param container the container to read the file from
     * @param file the path to the file inside the container
     * @throws IOException if the reader cannot be initialized
     */
    ContainerFileReader(
            final DockerClient dockerClient,
            final Container container,
            final Path file) throws IOException {
        super(createReader(dockerClient, container, file), StandardCharsets.UTF_8);
    }

    private static InputStream createReader(
            final DockerClient dockerClient,
            final Container container,
            final Path file) throws IOException {
        // Fetch the file from the container
        final InputStream fileArchiveStream;
        try {
            fileArchiveStream = dockerClient.copyArchiveFromContainerCmd(container.getId(), file.toString()).exec();
        } catch (final DockerException e) {
            throw new IOException(
                    String.format(
                            "Docker client failed to archive file from container: %s",
                            file),
                    e);
        }

        // Create a buffered reader of the first file in the tar stream
        final TarArchiveInputStream tarInputStream = new TarArchiveInputStream(fileArchiveStream);
        @Nullable final TarArchiveEntry tarArchive = tarInputStream.getNextEntry();
        if (tarArchive == null || !tarArchive.isFile()) {
            throw new IOException(
                    String.format(
                            "Docker client return unsupported archive for: %s",
                            file));
        }
        return tarInputStream;
    }
}
