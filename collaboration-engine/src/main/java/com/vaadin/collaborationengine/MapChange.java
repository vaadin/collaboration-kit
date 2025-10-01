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

import java.util.Objects;
import java.util.UUID;

import com.vaadin.collaborationengine.Topic.ChangeDetails;

import tools.jackson.databind.JsonNode;

class MapChange implements ChangeDetails {
    private final String mapName;
    private final MapChangeType type;
    private final String key;
    private final JsonNode value;
    private final JsonNode oldValue;
    private final UUID expectedId;
    private final UUID revisionId;

    MapChange(String mapName, MapChangeType type, String key, JsonNode oldValue,
            JsonNode newValue, UUID expectedId, UUID revisionId) {
        Objects.requireNonNull(mapName, "Map name can not be null.");
        Objects.requireNonNull(key, MessageUtil.Required.KEY);
        this.mapName = mapName;
        this.type = type;
        this.key = key;
        this.value = newValue;
        this.oldValue = oldValue;
        this.expectedId = expectedId;
        this.revisionId = revisionId;
    }

    JsonNode getOldValue() {
        return oldValue;
    }

    MapChangeType getType() {
        return type;
    }

    String getMapName() {
        return mapName;
    }

    String getKey() {
        return key;
    }

    JsonNode getValue() {
        return value;
    }

    UUID getExpectedId() {
        return expectedId;
    }

    UUID getRevisionId() {
        return revisionId;
    }

    boolean hasChanges() {
        return !Objects.equals(getOldValue(), getValue());
    }
}
