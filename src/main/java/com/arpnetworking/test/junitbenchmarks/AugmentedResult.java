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

import com.carrotsearch.junitbenchmarks.Result;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Augmented {@code Result} wrapper.
 *
 * @author Brandon Arp (barp at groupon dot com)
 */
final class AugmentedResult {

    public Result getResult() {
        return _result;
    }

    public Optional<Path> getProfileFile() {
        return _profileFile;
    }

    /**
     * Constructor for creating an augmented result.
     *
     * @param result the {@code Result} to extend
     * @param profileFile the profile data file
     */
    AugmentedResult(final Result result, final Path profileFile) {
        _result = result;
        _profileFile = Optional.of(profileFile);
    }

    /**
     * Constructor for creating an augmented result.
     *
     * @param result the {@code Result} to extend
     */
    AugmentedResult(final Result result) {
        _result = result;
        _profileFile = Optional.empty();
    }

    private final Result _result;
    private final Optional<Path> _profileFile;

}
