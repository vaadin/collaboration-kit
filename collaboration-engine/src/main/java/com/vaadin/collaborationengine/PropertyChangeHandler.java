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

import java.io.Serializable;

import tools.jackson.core.type.TypeReference;

/**
 * Functional interface that defines how to handle value changes for properties
 * in a topic.
 *
 * @see FormManager#setPropertyChangeHandler(PropertyChangeHandler)
 * @author Vaadin Ltd
 */
@FunctionalInterface
public interface PropertyChangeHandler extends Serializable {
    /**
     * The property change event.
     */
    interface PropertyChangeEvent {
        /**
         * Gets the property name.
         *
         * @return the property name, not {@code null}
         */
        String getPropertyName();

        /**
         * Gets the new value.
         *
         * @return the value, not {@code null}
         */
        Object getValue();

        /**
         * Gets the new value as an instance of the given class.
         *
         * @param type
         *            the expected type of the returned instance
         * @param <T>
         *            the type of the value from <code>type</code> parameter,
         *            e.g. <code>String</code>
         * @return throws an {@link UnsupportedOperationException}
         */
        default <T> T getValue(Class<T> type) {
            throw new UnsupportedOperationException(
                    "This method is not implemented");
        }

        /**
         * Gets the new value as an instance of the given type reference.
         *
         * @param typeRef
         *            the expected type reference of the returned instance
         * @param <T>
         *            the type reference of the value from <code>typeRef</code>
         *            parameter, e.g. <code>List<String>></code>
         * @return throws an {@link UnsupportedOperationException}
         */
        default <T> T getValue(TypeReference<T> typeRef) {
            throw new UnsupportedOperationException(
                    "This method is not implemented");
        }
    }

    /**
     * Handles a change of value for a property in a topic.
     *
     * @param event
     *            the property change event, not {@code null}
     */
    void handlePropertyChange(PropertyChangeEvent event);
}
