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

import com.carrotsearch.junitbenchmarks.GCSnapshot;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * Serializes a {@link GCSnapshot}.
 *
 * @author Brandon Arp (barp at groupon dot com)
 */
public class GCSnapshotSerializer extends JsonSerializer<GCSnapshot> {

    @Override
    public void serialize(
            final GCSnapshot value,
            final JsonGenerator jgen,
            final SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        jgen.writeObjectField("accumulatedInvocations", value.accumulatedInvocations());
        jgen.writeObjectField("accumulatedTime", value.accumulatedTime());
        jgen.writeEndObject();
    }
}
