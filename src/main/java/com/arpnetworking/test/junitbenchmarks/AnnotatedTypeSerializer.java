/*
 * Copyright 2023 Inscope Metrics, Inc
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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.lang.reflect.AnnotatedType;


/**
 * Serializer to convert AnnotatedType to JSON.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 */
public class AnnotatedTypeSerializer extends JsonSerializer<AnnotatedType> {
    @Override
    public void serialize(
    final AnnotatedType value,
    final JsonGenerator jgen,
    final SerializerProvider provider) throws IOException {
            jgen.writeStartObject();
            jgen.writeStringField("type", value.getType().getTypeName());
            jgen.writeEndObject();

    }
}

