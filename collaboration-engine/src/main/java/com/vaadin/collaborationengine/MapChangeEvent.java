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

import java.util.EventObject;
import java.util.Objects;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

/**
 * Event that is fired when the value in a collaboration map changes.
 *
 * @author Vaadin Ltd
 * @since 1.0
 */
public class MapChangeEvent extends EventObject {

    private final String key;
    private final JsonNode oldValue;
    private final JsonNode value;

    /**
     * Creates a new map change event.
     *
     * @param source
     *            the collaboration map for which the event is fired, not
     *            <code>null</code>
     * @param change
     *            detail of the change, not <code>null</code>
     *
     * @since 1.0
     */
    public MapChangeEvent(CollaborationMap source, MapChange change) {
        super(source);
        Objects.requireNonNull(change, "Entry change must not be null");
        this.key = change.getKey();
        this.oldValue = change.getOldValue();
        this.value = change.getValue();
    }

    @Override
    public CollaborationMap getSource() {
        return (CollaborationMap) super.getSource();
    }

    /**
     * Gets the updated map key.
     *
     * @return the updated map key, not <code>null</code>
     *
     * @since 1.0
     */
    public String getKey() {
        return key;
    }

    /**
     * Gets the old value as an instance of the given class.
     *
     * @param type
     *            the expected type of the returned instance
     * @param <T>
     *            the type of the value from <code>type</code> parameter, e.g.
     *            <code>String</code>
     * @return the old map value, or <code>null</code> if no value was present
     *         previously
     *
     * @since 1.0
     */
    public <T> T getOldValue(Class<T> type) {
        return JsonUtil.toInstance(oldValue, type);
    }

    /**
     * Gets the old value as an instance corresponding to the given type
     * reference.
     *
     * @param typeRef
     *            the expected type reference of the returned instance
     * @param <T>
     *            the type reference of the value from <code>typeRef</code>
     *            parameter, e.g. <code>List<String>></code>
     * @return the old map value, or <code>null</code> if no value was present
     *         previously
     *
     * @since 1.0
     */
    public <T> T getOldValue(TypeReference<T> typeRef) {
        return JsonUtil.toInstance(oldValue, typeRef);
    }

    /**
     * Gets the new value as an instance of the given class.
     *
     * @param type
     *            the expected type of the returned instance
     * @param <T>
     *            the type of the value from <code>type</code> parameter, e.g.
     *            <code>String</code>
     * @return the new map value, or <code>null</code> if the association was
     *         removed
     *
     * @since 1.0
     */
    public <T> T getValue(Class<T> type) {
        return JsonUtil.toInstance(value, type);
    }

    /**
     * Gets the new value as an instance corresponding to the given type
     * reference.
     *
     * @param typeRef
     *            the expected type reference of the returned instance
     * @param <T>
     *            the type reference of the value from `typeRef` parameter, e.g.
     *            <code>List<String>></code>
     * @return the new map value, or <code>null</code> if the association was
     *         removed
     *
     * @since 1.0
     */
    public <T> T getValue(TypeReference<T> typeRef) {
        return JsonUtil.toInstance(value, typeRef);
    }

}
