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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

/**
 * A linked list where each entry can be looked up using a generated key.
 *
 * @author Vaadin Ltd
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
class EntryList {
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class ListEntry {
        JsonNode value;
        UUID prev;
        UUID next;
        UUID revisionId;
        UUID scopeOwnerId;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static class ListEntrySnapshot {
        final UUID id;
        final JsonNode value;
        final UUID prev;
        final UUID next;
        final UUID revisionId;
        final UUID scopeOwnerId;

        ListEntrySnapshot(UUID id, ListEntry entry) {
            this.id = id;
            this.value = entry.value;
            this.prev = entry.prev;
            this.next = entry.next;
            this.revisionId = entry.revisionId;
            this.scopeOwnerId = entry.scopeOwnerId;
        }

        @JsonCreator
        ListEntrySnapshot(@JsonProperty("id") UUID id,
                @JsonProperty("value") JsonNode value,
                @JsonProperty("prev") UUID prev,
                @JsonProperty("next") UUID next,
                @JsonProperty("revisionId") UUID revisionId,
                @JsonProperty("scopeOwnerId") UUID scopeOwnerId) {
            this.id = id;
            this.value = value;
            this.prev = prev;
            this.next = next;
            this.revisionId = revisionId;
            this.scopeOwnerId = scopeOwnerId;
        }
    }

    private final Map<UUID, ListEntry> entries = new HashMap<>();
    private UUID head;
    private UUID tail;

    int size() {
        return entries.size();
    }

    void clear() {
        entries.clear();
        head = null;
        tail = null;
    }

    ListEntrySnapshot insertFirst(UUID key, JsonNode value, UUID revisionId,
            UUID scopeOwnerId) {
        ListEntry item = createAndAddItem(key, value, revisionId, scopeOwnerId);

        link(key, null, head);

        return new ListEntrySnapshot(key, item);
    }

    ListEntrySnapshot insertLast(UUID key, JsonNode value, UUID revisionId,
            UUID scopeOwnerId) {
        ListEntry item = createAndAddItem(key, value, revisionId, scopeOwnerId);

        link(key, tail, null);

        return new ListEntrySnapshot(key, item);
    }

    ListEntrySnapshot insertBefore(UUID keyToFind, UUID keyToInsert,
            JsonNode value, UUID revisionId, UUID scopeOwnerId) {
        ListEntry item = createAndAddItem(keyToInsert, value, revisionId,
                scopeOwnerId);

        ListEntry entryToFind = entries.get(keyToFind);
        link(keyToInsert, entryToFind.prev, keyToFind);

        return new ListEntrySnapshot(keyToInsert, item);
    }

    ListEntrySnapshot insertAfter(UUID keyToFind, UUID keyToInsert,
            JsonNode value, UUID revisionId, UUID scopeOwnerId) {
        ListEntry item = createAndAddItem(keyToInsert, value, revisionId,
                scopeOwnerId);

        ListEntry entryToFind = entries.get(keyToFind);
        link(keyToInsert, keyToFind, entryToFind.next);

        return new ListEntrySnapshot(keyToInsert, item);
    }

    ListEntrySnapshot moveBefore(UUID keyToFind, UUID keyToMove,
            UUID revisionId, UUID scopeOwnerId) {
        ListEntry entryToMove = entries.get(keyToMove);
        if (entryToMove != null) {
            entryToMove.revisionId = revisionId;
            if (Objects.equals(scopeOwnerId, JsonUtil.TOPIC_SCOPE_ID)) {
                entryToMove.scopeOwnerId = null;
            } else if (scopeOwnerId != null) {
                entryToMove.scopeOwnerId = scopeOwnerId;
            }
        }

        unlink(entryToMove);

        ListEntry entryToFind = entries.get(keyToFind);
        link(keyToMove, entryToFind.prev, keyToFind);

        return new ListEntrySnapshot(keyToMove, entryToMove);
    }

    ListEntrySnapshot moveAfter(UUID keyToFind, UUID keyToMove, UUID revisionId,
            UUID scopeOwnerId) {
        ListEntry entryToMove = entries.get(keyToMove);
        if (entryToMove != null) {
            entryToMove.revisionId = revisionId;
            if (Objects.equals(scopeOwnerId, JsonUtil.TOPIC_SCOPE_ID)) {
                entryToMove.scopeOwnerId = null;
            } else if (scopeOwnerId != null) {
                entryToMove.scopeOwnerId = scopeOwnerId;
            }
        }

        unlink(entryToMove);

        ListEntry entryToFind = entries.get(keyToFind);
        link(keyToMove, keyToFind, entryToFind.next);

        return new ListEntrySnapshot(keyToMove, entryToMove);
    }

    Stream<ListEntrySnapshot> stream() {
        Builder<ListEntrySnapshot> builder = Stream.builder();

        UUID key = head;
        while (key != null) {
            ListEntry entry = entries.get(key);
            builder.add(new ListEntrySnapshot(key, entry));
            key = entry.next;
        }

        return builder.build();
    }

    JsonNode getValue(UUID key) {
        ListEntry item = entries.get(key);

        if (item == null) {
            return null;
        }

        return item.value;
    }

    ListEntrySnapshot getEntry(UUID key) {
        ListEntry entry = entries.get(key);
        if (entry == null) {
            return null;
        } else {
            return new ListEntrySnapshot(key, entry);
        }
    }

    void remove(UUID key) {
        ListEntry item = entries.remove(key);
        unlink(item);
    }

    private void unlink(ListEntry item) {
        if (item != null) {
            setPrev(item.next, item.prev);
            setNext(item.prev, item.next);
        }
    }

    private void link(UUID key, UUID keyBefore, UUID keyAfter) {
        ListEntry item = entries.get(key);
        if (item != null) {
            item.prev = keyBefore;
            item.next = keyAfter;
            setPrev(item.next, key);
            setNext(item.prev, key);
        }
    }

    private void setNext(UUID target, UUID value) {
        if (target == null) {
            head = value;
        } else {
            entries.get(target).next = value;
        }
    }

    private void setPrev(UUID target, UUID value) {
        if (target == null) {
            tail = value;
        } else {
            entries.get(target).prev = value;
        }
    }

    private ListEntry createAndAddItem(UUID key, JsonNode value,
            UUID revisionId, UUID scopeOwnerId) {
        ListEntry item = new ListEntry();
        item.value = value;
        item.revisionId = revisionId;
        item.scopeOwnerId = scopeOwnerId;
        entries.put(Objects.requireNonNull(key), item);

        return item;
    }

    void setValue(UUID key, JsonNode newValue, UUID revisionId,
            UUID scopeOwnerId) {
        ListEntry listEntry = entries.get(key);
        listEntry.value = newValue;
        listEntry.revisionId = revisionId;
        listEntry.scopeOwnerId = scopeOwnerId;
    }
}
