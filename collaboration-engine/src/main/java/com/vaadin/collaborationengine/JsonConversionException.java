/*
 * Copyright 2000-2024 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.collaborationengine;

/**
 * Thrown when unable to convert back and forth between a Java object instance
 * and a Jackson {@link tools.jackson.databind.JsonNode} instance. The
 * conversion is necessary for sending the Java object over the network.
 *
 * @author Vaadin Ltd
 * @since 1.0
 */
public class JsonConversionException extends RuntimeException {

    /**
     * Constructs a new Json conversion exception
     *
     * @param message
     *            the detail message
     * @param cause
     *            the cause
     */
    public JsonConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
