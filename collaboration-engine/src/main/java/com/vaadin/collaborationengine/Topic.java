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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.vaadin.collaborationengine.EntryList.ListEntrySnapshot;
import com.vaadin.collaborationengine.MembershipEvent.MembershipEventType;
import com.vaadin.flow.function.SerializableBiConsumer;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.function.SerializableSupplier;
import com.vaadin.flow.shared.Registration;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

class Topic implements Serializable {

    enum ChangeResult {
        ACCEPTED, REJECTED
    }

    /**
     * Marker interface to have something more specific than Object in method
     * signatures.
     */
    interface ChangeDetails {
        // Marker interface
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static class Entry implements Serializable {

        final UUID revisionId;

        final JsonNode data;

        final UUID scopeOwnerId;

        @JsonCreator
        public Entry(@JsonProperty("id") UUID id,
                @JsonProperty("data") JsonNode data,
                @JsonProperty("scopeOwnerId") UUID scopeOwnerId) {
            this.revisionId = id;
            this.data = data;
            this.scopeOwnerId = scopeOwnerId;
        }
    }

    static class Snapshot implements Serializable {
        private static final TypeReference<Map<String, EntryList>> LISTS_TYPE = new TypeReference<>() {
        };
        private static final TypeReference<Map<String, Map<String, Entry>>> MAPS_TYPE = new TypeReference<>() {
        };
        private static final TypeReference<Map<String, Duration>> TIMEOUTS_TYPE = new TypeReference<>() {
        };
        private static final TypeReference<List<UUID>> NODES_TYPE = new TypeReference<>() {
        };

        private static final String LATEST = "latest";
        private static final String LISTS = "lists";
        private static final String MAPS = "maps";
        private static final String LIST_TIMEOUTS = "list-timeouts";
        private static final String MAP_TIMEOUTS = "map-timeouts";
        private static final String ACTIVE_NODES = "active-nodes";
        private static final String BACKEND_NODES = "backend-nodes";
        private final ObjectNode objectNode;

        Snapshot(ObjectNode objectNode) {
            this.objectNode = Objects.requireNonNull(objectNode);
        }

        static Snapshot fromTopic(Topic topic, UUID latestChangeId) {
            ObjectNode objectNode = JsonUtil.getObjectMapper()
                    .createObjectNode();
            objectNode.put(LATEST, latestChangeId.toString());
            objectNode.set(LISTS, JsonUtil.toJsonNode(topic.namedListData));
            objectNode.set(MAPS, JsonUtil.toJsonNode(topic.namedMapData));
            objectNode.set(LIST_TIMEOUTS,
                    JsonUtil.toJsonNode(topic.listExpirationTimeouts));
            objectNode.set(MAP_TIMEOUTS,
                    JsonUtil.toJsonNode(topic.mapExpirationTimeouts));
            objectNode.set(ACTIVE_NODES,
                    JsonUtil.toJsonNode(topic.activeNodes));
            objectNode.set(BACKEND_NODES,
                    JsonUtil.toJsonNode(topic.backendNodes));
            return new Snapshot(objectNode);
        }

        ObjectNode toObjectNode() {
            return objectNode;
        }

        Map<String, EntryList> getLists() {
            return JsonUtil.toInstance(objectNode.get(LISTS), LISTS_TYPE);
        }

        Map<String, Map<String, Entry>> getMaps() {
            return JsonUtil.toInstance(objectNode.get(MAPS), MAPS_TYPE);
        }

        Map<String, Duration> getListTimeouts() {
            return JsonUtil.toInstance(objectNode.get(LIST_TIMEOUTS),
                    TIMEOUTS_TYPE);
        }

        Map<String, Duration> getMapTimeouts() {
            return JsonUtil.toInstance(objectNode.get(MAP_TIMEOUTS),
                    TIMEOUTS_TYPE);
        }

        List<UUID> getActiveNodes() {
            return JsonUtil.toInstance(objectNode.get(ACTIVE_NODES),
                    NODES_TYPE);
        }

        List<UUID> getBackendNodes() {
            return JsonUtil.toInstance(objectNode.get(BACKEND_NODES),
                    NODES_TYPE);
        }

    }

    private final String id;
    private final SerializableSupplier<CollaborationEngine> ceSupplier;
    private final Map<String, Map<String, Entry>> namedMapData = new HashMap<>();
    private final Map<String, EntryList> namedListData = new HashMap<>();
    final Map<String, Duration> mapExpirationTimeouts = new HashMap<>();
    final Map<String, Duration> listExpirationTimeouts = new HashMap<>();
    private final List<UUID> activeNodes = new ArrayList<>();
    private Instant lastDisconnected;
    private final List<SerializableBiConsumer<UUID, ChangeDetails>> changeListeners = new ArrayList<>();
    private final Map<UUID, SerializableConsumer<ChangeResult>> changeResultTrackers = new ConcurrentHashMap<>();
    private final List<UUID> backendNodes = new ArrayList<>();
    private final Backend.EventLog eventLog;
    private UUID lastSnapshotId;
    private boolean leader;
    private int changeCount;

    Topic(String id, SerializableSupplier<CollaborationEngine> ceSupplier,
            Backend.EventLog eventLog) {
        this.id = id;
        this.ceSupplier = ceSupplier;
        this.eventLog = eventLog;
        final Backend backend = getBackend();
        backend.addMembershipListener(event -> {
            if (event.getType().equals(MembershipEventType.LEAVE)) {
                handleNodeLeave(event.getNodeId());
            }
        });
        if (eventLog != null) {
            BackendUtil
                    .initializeFromSnapshot(getCollaborationEngine(),
                            this::initializeFromSnapshot)
                    .thenAccept(uuid -> lastSnapshotId = uuid);
        }
    }

    UUID getCurrentNodeId() {
        return getBackend().getNodeId();
    }

    private Backend getBackend() {
        return getCollaborationEngine().getConfiguration().getBackend();
    }

    private CompletableFuture<UUID> initializeFromSnapshot() {
        return getBackend().loadLatestSnapshot(id)
                .thenCompose(this::loadAndSubscribe);
    }

    private CompletableFuture<UUID> loadAndSubscribe(
            Backend.Snapshot snapshot) {
        CompletableFuture<UUID> future = new CompletableFuture<>();
        try {
            UUID latestChange = null;
            if (snapshot != null) {
                ObjectNode payload = JsonUtil.fromString(snapshot.getPayload());
                latestChange = JsonUtil.toUUID(payload.get(Snapshot.LATEST));
                loadSnapshot(new Snapshot(payload));
                eventLog.subscribe(latestChange, this::applyChange);
            } else {
                eventLog.subscribe(null, this::applyChange);
            }

            ObjectNode nodeEvent = JsonUtil.createNodeJoin(getCurrentNodeId());

            eventLog.submitEvent(UUID.randomUUID(),
                    JsonUtil.toString(nodeEvent));

            future.complete(latestChange);
        } catch (Backend.EventIdNotFoundException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    synchronized void handleNodeLeave(UUID nodeId) {
        Backend backend = getCollaborationEngine().getConfiguration()
                .getBackend();
        backendNodes.remove(nodeId);
        if (!backendNodes.isEmpty()
                && backendNodes.get(0).equals(backend.getNodeId())) {
            becomeLeader();
        }
        if (leader) {
            cleanupStaleEntries(nodeId::equals);
        }
    }

    private void cleanupStaleEntries(Predicate<UUID> isStale) {
        namedMapData.entrySet().stream()
                .flatMap(map -> map.getValue().entrySet().stream().filter(
                        entry -> isStale.test(entry.getValue().scopeOwnerId))
                        .map(entry -> {
                            ObjectNode change = JsonUtil.createPutChange(
                                    map.getKey(), entry.getKey(), null, null,
                                    null);
                            change.put(JsonUtil.CHANGE_EXPECTED_ID,
                                    entry.getValue().revisionId.toString());
                            return change;
                        }))
                .toList()
                .forEach(change -> eventLog.submitEvent(UUID.randomUUID(),
                        JsonUtil.toString(change)));
        namedListData.entrySet().stream()
                .flatMap(list -> list.getValue().stream()
                        .filter(entry -> isStale.test(entry.scopeOwnerId))
                        .map(entry -> {
                            ObjectNode change = JsonUtil.createListChange(
                                    ListOperation.OperationType.SET,
                                    list.getKey(), entry.id.toString(), null,
                                    null, null, Collections.emptyMap(),
                                    Collections.emptyMap(), null);
                            change.put(JsonUtil.CHANGE_EXPECTED_ID,
                                    entry.revisionId.toString());
                            return change;
                        }))
                .toList()
                .forEach(change -> eventLog.submitEvent(UUID.randomUUID(),
                        JsonUtil.toString(change)));
    }

    Registration subscribeToChange(
            SerializableBiConsumer<UUID, ChangeDetails> changeListener) {
        clearExpiredData();
        changeListeners.add(changeListener);
        return () -> changeListeners.remove(changeListener);
    }

    private void clearExpiredData() {
        Clock clock = getCollaborationEngine().getClock();
        if (isLeader() && lastDisconnected != null) {
            Instant now = clock.instant();
            mapExpirationTimeouts.entrySet().stream()
                    .filter(entry -> now
                            .isAfter(lastDisconnected.plus(entry.getValue()))
                            && namedMapData.containsKey(entry.getKey()))
                    .map(Map.Entry::getKey).toList().forEach(name -> {
                        namedMapData.get(name).entrySet().stream()
                                .map(entry -> {
                                    ObjectNode change = JsonUtil
                                            .createPutChange(name,
                                                    entry.getKey(), null, null,
                                                    null);
                                    change.put(JsonUtil.CHANGE_EXPECTED_ID,
                                            entry.getValue().revisionId
                                                    .toString());
                                    return change;
                                }).toList()
                                .forEach(change -> eventLog.submitEvent(
                                        UUID.randomUUID(),
                                        JsonUtil.toString(change)));
                        mapExpirationTimeouts.remove(name);
                    });
            listExpirationTimeouts.entrySet().stream()
                    .filter(entry -> now
                            .isAfter(lastDisconnected.plus(entry.getValue()))
                            && namedListData.containsKey(entry.getKey()))
                    .map(Map.Entry::getKey).toList().forEach(name -> {
                        namedListData.get(name).stream().map(entry -> {
                            ObjectNode change = JsonUtil.createListChange(
                                    ListOperation.OperationType.SET, name,
                                    entry.id.toString(), null, null, null,
                                    Collections.emptyMap(),
                                    Collections.emptyMap(), null);
                            change.put(JsonUtil.CHANGE_EXPECTED_ID,
                                    entry.revisionId.toString());
                            return change;
                        }).toList()
                                .forEach(change -> eventLog.submitEvent(
                                        UUID.randomUUID(),
                                        JsonUtil.toString(change)));
                        listExpirationTimeouts.remove(name);
                    });
        }
    }

    Stream<MapChange> getMapData(String mapName) {
        Map<String, Entry> mapData = namedMapData.get(mapName);
        if (mapData == null) {
            return Stream.empty();
        }
        return mapData.entrySet().stream()
                .map(entry -> new MapChange(mapName, MapChangeType.PUT,
                        entry.getKey(), null, entry.getValue().data, null,
                        entry.getValue().revisionId));
    }

    JsonNode getMapValue(String mapName, String key) {
        Map<String, Entry> map = namedMapData.get(mapName);
        if (map == null || !map.containsKey(key)) {
            return null;
        }
        return map.get(key).data.deepCopy();
    }

    synchronized ChangeResult applyChange(UUID trackingId, String payload) {
        ObjectNode change = JsonUtil.fromString(payload);
        changeCount++;
        String type = change.get(JsonUtil.CHANGE_TYPE).asText();
        ChangeDetails details;
        switch (type) {
        case JsonUtil.CHANGE_TYPE_PUT:
            details = applyMapPut(trackingId, change);
            break;
        case JsonUtil.CHANGE_TYPE_REPLACE:
            details = applyMapReplace(trackingId, change);
            break;
        case JsonUtil.CHANGE_TYPE_INSERT_BEFORE:
            details = applyListInsert(trackingId, change, true);
            break;
        case JsonUtil.CHANGE_TYPE_INSERT_AFTER:
            details = applyListInsert(trackingId, change, false);
            break;
        case JsonUtil.CHANGE_TYPE_MOVE_BEFORE:
            details = applyListMove(trackingId, change, true);
            break;
        case JsonUtil.CHANGE_TYPE_MOVE_AFTER:
            details = applyListMove(trackingId, change, false);
            break;
        case JsonUtil.CHANGE_TYPE_LIST_SET:
            details = applyListSet(trackingId, change);
            break;
        case JsonUtil.CHANGE_TYPE_MAP_TIMEOUT:
            applyMapTimeout(change);
            return ChangeResult.ACCEPTED;
        case JsonUtil.CHANGE_TYPE_LIST_TIMEOUT:
            applyListTimeout(change);
            return ChangeResult.ACCEPTED;
        case JsonUtil.CHANGE_NODE_ACTIVATE: {
            UUID nodeId = UUID
                    .fromString(change.get(JsonUtil.CHANGE_NODE_ID).asText());
            activeNodes.add(nodeId);
            lastDisconnected = null;
            return ChangeResult.ACCEPTED;
        }
        case JsonUtil.CHANGE_NODE_DEACTIVATE: {
            UUID nodeId = UUID
                    .fromString(change.get(JsonUtil.CHANGE_NODE_ID).asText());
            activeNodes.remove(nodeId);
            if (activeNodes.isEmpty()) {
                lastDisconnected = getCollaborationEngine().getClock()
                        .instant();
            }
            return ChangeResult.ACCEPTED;
        }
        case JsonUtil.CHANGE_NODE_JOIN: {
            UUID nodeId = UUID
                    .fromString(change.get(JsonUtil.CHANGE_NODE_ID).asText());
            if (backendNodes.isEmpty()
                    && getCollaborationEngine().getConfiguration().getBackend()
                            .getNodeId().equals(nodeId)) {
                becomeLeader();
            }
            backendNodes.add(nodeId);
            return ChangeResult.ACCEPTED;
        }
        default:
            throw new UnsupportedOperationException(
                    "Type '" + type + "' is not a supported change type");
        }
        ChangeResult result = details != null ? ChangeResult.ACCEPTED
                : ChangeResult.REJECTED;

        SerializableConsumer<ChangeResult> changeResultTracker = changeResultTrackers
                .remove(trackingId);
        if (changeResultTracker != null) {
            changeResultTracker.accept(result);
        }
        if (ChangeResult.ACCEPTED.equals(result)) {
            EventUtil.fireEvents(changeListeners,
                    listener -> listener.accept(trackingId, details), true);
        }
        if (lastSnapshotId == null) {
            UUID newId = UUID.randomUUID();
            String snapshot = JsonUtil.toString(
                    Topic.Snapshot.fromTopic(this, trackingId).toObjectNode());
            getBackend().replaceSnapshot(id, null, newId, snapshot);
            getBackend().loadLatestSnapshot(id)
                    .thenAccept(s -> lastSnapshotId = s.getId());
        }
        if (leader && changeCount % 100 == 0) {
            UUID newId = UUID.randomUUID();
            ObjectNode snapshot = Topic.Snapshot.fromTopic(this, trackingId)
                    .toObjectNode();
            getBackend()
                    .replaceSnapshot(id, lastSnapshotId, newId,
                            JsonUtil.toString(snapshot))
                    .thenAccept(s -> eventLog.truncate(lastSnapshotId));
            lastSnapshotId = newId;
        }
        return result;
    }

    void loadSnapshot(Snapshot snapshot) {
        if (!namedListData.isEmpty() || !namedMapData.isEmpty()
                || !backendNodes.isEmpty()) {
            throw new IllegalStateException(
                    "You can only load snapshots for empty topics");
        }
        namedListData.putAll(snapshot.getLists());
        namedMapData.putAll(snapshot.getMaps());
        listExpirationTimeouts.putAll(snapshot.getListTimeouts());
        mapExpirationTimeouts.putAll(snapshot.getMapTimeouts());
        activeNodes.addAll(snapshot.getActiveNodes());
        backendNodes.addAll(snapshot.getBackendNodes());
    }

    private void becomeLeader() {
        leader = true;
        Set<UUID> backendNodesCopy = new HashSet<>(backendNodes);
        cleanupStaleEntries(id -> id != null && !backendNodesCopy.contains(id));
    }

    boolean isLeader() {
        return leader;
    }

    ChangeDetails applyMapPut(UUID changeId, ObjectNode change) {
        String mapName = change.get(JsonUtil.CHANGE_NAME).asText();
        String key = change.get(JsonUtil.CHANGE_KEY).asText();
        JsonNode expectedValue = change.get(JsonUtil.CHANGE_EXPECTED_VALUE);
        JsonNode expectedId = change.get(JsonUtil.CHANGE_EXPECTED_ID);
        JsonNode newValue = change.get(JsonUtil.CHANGE_VALUE);

        Map<String, Entry> map = namedMapData.computeIfAbsent(mapName,
                name -> new HashMap<>());
        JsonNode oldValue = map.containsKey(key) ? map.get(key).data
                : NullNode.getInstance();
        UUID oldChangeId = map.containsKey(key) ? map.get(key).revisionId
                : null;

        if (expectedId != null
                && !Objects.equals(oldChangeId, JsonUtil.toUUID(expectedId))) {
            return null;
        }
        if (expectedValue != null && !Objects.equals(oldValue, expectedValue)) {
            return null;
        }

        if (newValue instanceof NullNode) {
            map.remove(key);
        } else {
            map.put(key, new Entry(changeId, newValue.deepCopy(),
                    JsonUtil.toUUID(change.get(JsonUtil.CHANGE_SCOPE_OWNER))));
        }
        return new MapChange(mapName, MapChangeType.PUT, key, oldValue,
                newValue, JsonUtil.toUUID(expectedId), changeId);
    }

    ChangeDetails applyMapReplace(UUID changeId, ObjectNode change) {
        String mapName = change.get(JsonUtil.CHANGE_NAME).asText();
        String key = change.get(JsonUtil.CHANGE_KEY).asText();
        JsonNode expectedValue = change.get(JsonUtil.CHANGE_EXPECTED_VALUE);
        JsonNode newValue = change.get(JsonUtil.CHANGE_VALUE);

        Map<String, Entry> map = namedMapData.computeIfAbsent(mapName,
                name -> new HashMap<>());
        JsonNode oldValue = map.containsKey(key) ? map.get(key).data
                : NullNode.getInstance();

        if (expectedValue != null && !Objects.equals(oldValue, expectedValue)) {
            return null;
        }

        if (newValue instanceof NullNode) {
            map.remove(key);
        } else {
            map.put(key, new Entry(changeId, newValue.deepCopy(),
                    JsonUtil.toUUID(change.get(JsonUtil.CHANGE_SCOPE_OWNER))));
        }
        return new MapChange(mapName, MapChangeType.REPLACE, key, oldValue,
                newValue, null, changeId);
    }

    ChangeDetails applyListInsert(UUID id, ObjectNode change, boolean before) {
        String listName = change.get(JsonUtil.CHANGE_NAME).asText();
        UUID key = JsonUtil.toUUID(change.get(JsonUtil.CHANGE_POSITION_KEY));
        JsonNode item = change.get(JsonUtil.CHANGE_VALUE);
        UUID scopeOwnerId = JsonUtil
                .toUUID(change.get(JsonUtil.CHANGE_SCOPE_OWNER));
        if (Objects.equals(scopeOwnerId, JsonUtil.TOPIC_SCOPE_ID)) {
            scopeOwnerId = null;
        }
        EntryList list = getOrCreateList(listName);

        if (!conditionsMet(change)) {
            return null;
        }

        ListEntrySnapshot insertedEntry;
        if (key == null) {
            if (before) { // insert before null -> insert last
                insertedEntry = list.insertLast(id, item, id, scopeOwnerId);
            } else { // insert after null -> insert first
                insertedEntry = list.insertFirst(id, item, id, scopeOwnerId);
            }
        } else {
            ListEntrySnapshot entry = list.getEntry(key);
            if (entry == null) {
                return null;
            }
            if (before) {
                insertedEntry = list.insertBefore(key, id, item, id,
                        scopeOwnerId);
            } else {
                insertedEntry = list.insertAfter(key, id, item, id,
                        scopeOwnerId);
            }
        }

        return new ListChange(listName, ListChangeType.INSERT, id, null, item,
                null, insertedEntry.prev, null, insertedEntry.next, null, id);
    }

    ChangeDetails applyListMove(UUID id, ObjectNode change, boolean before) {
        String listName = change.get(JsonUtil.CHANGE_NAME).asText();
        UUID positionKey = JsonUtil
                .toUUID(change.get(JsonUtil.CHANGE_POSITION_KEY));
        UUID changeKey = JsonUtil.toUUID(change.get(JsonUtil.CHANGE_KEY));
        UUID scopeOwnerId = JsonUtil
                .toUUID(change.get(JsonUtil.CHANGE_SCOPE_OWNER));
        EntryList list = getOrCreateList(listName);

        if (!conditionsMet(change)) {
            return null;
        }

        ListEntrySnapshot positionEntry = list.getEntry(positionKey);
        if (positionEntry == null) {
            return null;
        }
        ListEntrySnapshot moveEntry = list.getEntry(changeKey);
        if (moveEntry == null) {
            return null;
        }

        ListEntrySnapshot insertedEntry;
        if (before) {
            insertedEntry = list.moveBefore(positionKey, changeKey, id,
                    scopeOwnerId);
        } else {
            insertedEntry = list.moveAfter(positionKey, changeKey, id,
                    scopeOwnerId);
        }

        return new ListChange(listName, ListChangeType.MOVE, changeKey,
                moveEntry.value, moveEntry.value, moveEntry.prev,
                insertedEntry.prev, moveEntry.next, insertedEntry.next, null,
                id);
    }

    private ChangeDetails applyListSet(UUID trackingId, ObjectNode change) {
        String listName = change.get(JsonUtil.CHANGE_NAME).asText();
        UUID key = JsonUtil.toUUID(change.get(JsonUtil.CHANGE_KEY));
        JsonNode newValue = change.get(JsonUtil.CHANGE_VALUE);
        UUID expectedId = JsonUtil
                .toUUID(change.get(JsonUtil.CHANGE_EXPECTED_ID));
        EntryList list = getOrCreateList(listName);

        if (!conditionsMet(change)) {
            return null;
        }

        ListEntrySnapshot entry = list.getEntry(key);
        if (entry == null) {
            return null;
        }
        if (expectedId != null
                && !Objects.equals(entry.revisionId, expectedId)) {
            return null;
        }
        if (newValue.isNull()) {
            list.remove(key);
            return new ListChange(listName, ListChangeType.SET, key,
                    entry.value, null, entry.prev, null, entry.next, null,
                    expectedId, null);
        } else {
            JsonNode oldValue = entry.value;
            UUID scopeOwnerId = JsonUtil
                    .toUUID(change.get(JsonUtil.CHANGE_SCOPE_OWNER));
            if (Objects.equals(scopeOwnerId, JsonUtil.TOPIC_SCOPE_ID)) {
                scopeOwnerId = null;
            }
            list.setValue(key, newValue, trackingId, scopeOwnerId);
            return new ListChange(listName, ListChangeType.SET, key, oldValue,
                    newValue, entry.prev, entry.prev, entry.next, entry.next,
                    expectedId, trackingId);
        }
    }

    private boolean conditionsMet(ObjectNode change) {
        String listName = change.get(JsonUtil.CHANGE_NAME).asText();
        EntryList list = getOrCreateList(listName);
        if (change.has(JsonUtil.CHANGE_EMPTY)) {
            boolean empty = change.get(JsonUtil.CHANGE_EMPTY).asBoolean();
            if (empty && list.size() > 0) {
                return false;
            } else if (!empty && list.size() == 0) {
                return false;
            }
        }

        for (JsonNode condition : change
                .withArray(JsonUtil.CHANGE_CONDITIONS)) {
            UUID leftKey = JsonUtil.toUUID(condition.get(JsonUtil.CHANGE_KEY));
            UUID rightKey = JsonUtil
                    .toUUID(condition.get(JsonUtil.CHANGE_POSITION_KEY));
            // If the left key of the condition is null, right key must be the
            // first i.e. have a null prev otherwise we reject the operation
            if (leftKey == null
                    && getListEntry(listName, rightKey).prev != null) {
                return false;
            } else if (leftKey != null && !Objects
                    .equals(getListEntry(listName, leftKey).next, rightKey)) {
                return false;
            }
        }

        for (JsonNode valueCondition : change
                .withArray(JsonUtil.CHANGE_VALUE_CONDITIONS)) {
            UUID refKey = JsonUtil
                    .toUUID(valueCondition.get(JsonUtil.CHANGE_KEY));
            JsonNode expectedValue = valueCondition
                    .get(JsonUtil.CHANGE_EXPECTED_VALUE);
            if (refKey == null || getListEntry(listName, refKey) == null
                    || !Objects.equals(getListEntry(listName, refKey).value,
                            expectedValue)) {
                return false;
            }
        }

        return true;
    }

    void applyMapTimeout(ObjectNode change) {
        String mapName = change.get(JsonUtil.CHANGE_NAME).asText();
        JsonNode newValue = change.get(JsonUtil.CHANGE_VALUE);

        if (newValue instanceof NullNode) {
            mapExpirationTimeouts.remove(mapName);
        } else {
            Duration timeout = JsonUtil.toInstance(newValue, Duration.class);
            mapExpirationTimeouts.put(mapName, timeout);
        }
    }

    void applyListTimeout(ObjectNode change) {
        String listName = change.get(JsonUtil.CHANGE_NAME).asText();
        JsonNode newValue = change.get(JsonUtil.CHANGE_VALUE);

        if (newValue instanceof NullNode) {
            listExpirationTimeouts.remove(listName);
        } else {
            Duration timeout = JsonUtil.toInstance(newValue, Duration.class);
            listExpirationTimeouts.put(listName, timeout);
        }
    }

    Stream<ListChange> getListChanges(String listName) {
        return getListItems(listName).map(item -> new ListChange(listName,
                ListChangeType.INSERT, item.id, null, item.value, null,
                item.prev, null, null, null, item.revisionId));
    }

    Stream<ListEntrySnapshot> getListItems(String listName) {
        return getList(listName).map(EntryList::stream)
                .orElseGet(Stream::empty);
    }

    ListEntrySnapshot getListEntry(String listName, UUID key) {
        return getList(listName).map(list -> list.getEntry(key)).orElse(null);
    }

    JsonNode getListValue(String listName, UUID key) {
        return getList(listName).map(list -> list.getValue(key)).orElse(null);
    }

    private EntryList getOrCreateList(String listName) {
        return namedListData.computeIfAbsent(listName, name -> new EntryList());
    }

    private Optional<EntryList> getList(String listName) {
        return Optional.ofNullable(namedListData.get(listName));
    }

    void setChangeResultTracker(UUID id,
            SerializableConsumer<ChangeResult> changeResultTracker) {
        SerializableConsumer<ChangeResult> oldTracker = changeResultTrackers
                .putIfAbsent(id, changeResultTracker);
        if (oldTracker != null) {
            throw new IllegalStateException(
                    "Cannot set a change-result tracker for an id with one already set");
        }
    }

    // For testing
    boolean hasChangeListeners() {
        return !changeListeners.isEmpty();
    }

    private CollaborationEngine getCollaborationEngine() {
        return ceSupplier.get();
    }
}
