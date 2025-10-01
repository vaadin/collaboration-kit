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

import java.util.UUID;

import com.vaadin.collaborationengine.Topic.ChangeDetails;

import tools.jackson.databind.JsonNode;

class ListChange implements ChangeDetails {
    private final String listName;

    private final ListChangeType type;
    private final UUID key;

    private final JsonNode oldValue;
    private final JsonNode value;

    private final UUID oldPrev;
    private final UUID prev;

    private final UUID oldNext;
    private final UUID next;

    private final UUID expectedId;

    private final UUID revisionId;

    ListChange(String listName, ListChangeType type, UUID key,
            JsonNode oldValue, JsonNode value, UUID oldPrev, UUID prev,
            UUID oldNext, UUID next, UUID expectedId, UUID revisionId) {
        this.listName = listName;
        this.type = type;
        this.key = key;
        this.oldValue = oldValue;
        this.value = value;
        this.oldPrev = oldPrev;
        this.prev = prev;
        this.oldNext = oldNext;
        this.next = next;
        this.expectedId = expectedId;
        this.revisionId = revisionId;
    }

    String getListName() {
        return listName;
    }

    ListChangeType getType() {
        return type;
    }

    UUID getKey() {
        return key;
    }

    JsonNode getOldValue() {
        return oldValue;
    }

    JsonNode getValue() {
        return value;
    }

    UUID getOldNext() {
        return oldNext;
    }

    UUID getNext() {
        return next;
    }

    UUID getOldPrev() {
        return oldPrev;
    }

    UUID getPrev() {
        return prev;
    }

    UUID getExpectedId() {
        return expectedId;
    }

    UUID getRevisionId() {
        return revisionId;
    }
}
