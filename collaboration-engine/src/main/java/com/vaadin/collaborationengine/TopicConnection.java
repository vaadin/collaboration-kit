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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vaadin.collaborationengine.Topic.ChangeDetails;
import com.vaadin.collaborationengine.Topic.ChangeResult;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.function.SerializableSupplier;
import com.vaadin.flow.shared.Registration;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * API for sending and subscribing to updates between clients collaborating on
 * the same collaboration topic.
 *
 * @author Vaadin Ltd
 * @since 1.0
 */
public class TopicConnection {

    class CollaborationMapImplementation implements CollaborationMap {
        private final String name;

        private CollaborationMapImplementation(String name) {
            this.name = name;
        }

        @Override
        public Registration subscribe(MapSubscriber subscriber) {
            ensureActiveConnection();
            Objects.requireNonNull(subscriber, "Subscriber cannot be null");

            synchronized (topic) {
                Consumer<MapChange> mapChangeNotifier = mapChange -> {
                    MapChangeEvent event = new MapChangeEvent(this, mapChange);
                    actionDispatcher.dispatchAction(
                            () -> subscriber.onMapChange(event));
                };
                topic.getMapData(name).forEach(mapChangeNotifier);

                Registration registration = subscribeToMap(name,
                        mapChangeNotifier);
                addRegistration(registration);
                return registration;
            }
        }

        @Override
        public CompletableFuture<Boolean> replace(String key,
                Object expectedValue, Object newValue) {
            ensureActiveConnection();
            Objects.requireNonNull(key, MessageUtil.Required.KEY);

            ObjectNode change = JsonUtil.createReplaceChange(name, key,
                    expectedValue, newValue);
            UUID id = UUID.randomUUID();

            return dispatchChangeWithBooleanResult(id, key, false, change);
        }

        @Override
        public CompletableFuture<Void> put(String key, Object value,
                EntryScope scope) {
            ensureActiveConnection();
            Objects.requireNonNull(key, MessageUtil.Required.KEY);

            boolean connectionScope = scope == EntryScope.CONNECTION;
            ObjectNode change = JsonUtil.createPutChange(name, key, null, value,
                    connectionScope ? topic.getCurrentNodeId() : null);
            UUID id = UUID.randomUUID();

            return dispatchChangeWithVoidResult(id, key, connectionScope,
                    change);
        }

        private CompletableFuture<Void> dispatchChangeWithVoidResult(UUID id,
                String key, boolean connectionScope, ObjectNode change) {
            CompletableFuture<Void> contextFuture = actionDispatcher
                    .createCompletableFuture();

            topic.setChangeResultTracker(id, result -> {
                if (connectionScope && result == ChangeResult.ACCEPTED) {
                    connectionScopedMapKeys
                            .computeIfAbsent(name, k -> new HashMap<>())
                            .put(key, id);
                    if (!cleanupPending) {
                        cleanupScopedData();
                    }
                }
                if (actionDispatcher != null) {
                    actionDispatcher
                            .dispatchAction(() -> contextFuture.complete(null));
                }
            });
            actionDispatcher
                    .dispatchAction(() -> distributor.accept(id, change));

            return contextFuture;
        }

        private CompletableFuture<Boolean> dispatchChangeWithBooleanResult(
                UUID id, String key, boolean connectionScope,
                ObjectNode change) {
            CompletableFuture<Boolean> contextFuture = actionDispatcher
                    .createCompletableFuture();

            topic.setChangeResultTracker(id, result -> {
                if (connectionScope && result == ChangeResult.ACCEPTED) {
                    connectionScopedMapKeys
                            .computeIfAbsent(name, k -> new HashMap<>())
                            .put(key, id);
                    if (!cleanupPending) {
                        cleanupScopedData();
                    }
                }
                if (actionDispatcher != null) {
                    actionDispatcher.dispatchAction(() -> contextFuture
                            .complete(result != ChangeResult.REJECTED));
                }
            });
            actionDispatcher
                    .dispatchAction(() -> distributor.accept(id, change));

            return contextFuture;
        }

        @Override
        public Stream<String> getKeys() {
            ensureActiveConnection();
            synchronized (topic) {
                List<String> snapshot = topic.getMapData(name)
                        .map(MapChange::getKey).collect(Collectors.toList());
                return snapshot.stream();
            }
        }

        @Override
        public <T> T get(String key, Class<T> type) {
            return JsonUtil.toInstance(get(key), type);
        }

        @Override
        public <T> T get(String key, TypeReference<T> type) {
            return JsonUtil.toInstance(get(key), type);
        }

        private JsonNode get(String key) {
            ensureActiveConnection();
            Objects.requireNonNull(key, MessageUtil.Required.KEY);

            synchronized (topic) {
                return topic.getMapValue(name, key);
            }
        }

        @Override
        public TopicConnection getConnection() {
            return TopicConnection.this;
        }

        @Override
        public Optional<Duration> getExpirationTimeout() {
            Duration expirationTimeout = topic.mapExpirationTimeouts.get(name);
            return Optional.ofNullable(expirationTimeout);
        }

        @Override
        public void setExpirationTimeout(Duration expirationTimeout) {
            ensureActiveConnection();

            ObjectNode change = JsonUtil.createMapTimeoutChange(name,
                    expirationTimeout);

            UUID id = UUID.randomUUID();
            actionDispatcher
                    .dispatchAction(() -> distributor.accept(id, change));
        }

        private Registration subscribeToMap(String mapName,
                Consumer<MapChange> mapChangeNotifier) {
            subscribersPerMap.computeIfAbsent(mapName, key -> new ArrayList<>())
                    .add(mapChangeNotifier);
            return () -> unsubscribeFromMap(mapName, mapChangeNotifier);
        }

        private void unsubscribeFromMap(String mapName,
                Consumer<MapChange> mapChangeNotifier) {
            List<Consumer<MapChange>> notifiers = subscribersPerMap
                    .get(mapName);
            if (notifiers == null) {
                return;
            }
            notifiers.remove(mapChangeNotifier);
            if (notifiers.isEmpty()) {
                subscribersPerMap.remove(mapName);
            }
        }
    }

    class CollaborationListImplementation implements CollaborationList {
        private final String name;

        private CollaborationListImplementation(String name) {
            this.name = name;
        }

        @Override
        public Registration subscribe(ListSubscriber subscriber) {
            ensureActiveConnection();
            Objects.requireNonNull(subscriber, "Subscriber cannot be null");

            synchronized (topic) {
                Consumer<ListChange> changeNotifier = listChange -> {
                    ListChangeEvent event = new ListChangeEvent(this,
                            listChange);
                    actionDispatcher.dispatchAction(
                            () -> subscriber.onListChange(event));
                };
                topic.getListChanges(name).forEach(changeNotifier);

                Registration registration = subscribeToList(name,
                        changeNotifier);
                addRegistration(registration);
                return registration;
            }
        }

        @Override
        public <T> List<T> getItems(Class<T> type) {
            return getItems(JsonUtil.fromJsonConverter(type));
        }

        @Override
        public <T> List<T> getItems(TypeReference<T> type) {
            return getItems(JsonUtil.fromJsonConverter(type));
        }

        private <T> List<T> getItems(Function<JsonNode, T> converter) {
            ensureActiveConnection();
            synchronized (topic) {
                return topic.getListItems(name).map(item -> item.value)
                        .map(converter).collect(Collectors.toList());
            }
        }

        @Override
        public <T> T getItem(ListKey key, Class<T> type) {
            return getItem(key, JsonUtil.fromJsonConverter(type));
        }

        @Override
        public <T> T getItem(ListKey key, TypeReference<T> type) {
            return getItem(key, JsonUtil.fromJsonConverter(type));
        }

        private <T> T getItem(ListKey key, Function<JsonNode, T> converter) {
            ensureActiveConnection();
            Objects.requireNonNull(key);
            synchronized (topic) {
                return converter.apply(topic.getListValue(name, key.getKey()));
            }
        }

        @Override
        public Stream<ListKey> getKeys() {
            ensureActiveConnection();
            synchronized (topic) {
                return topic.getListItems(name)
                        .map(item -> new ListKey(item.id))
                        .collect(Collectors.toList()).stream();
            }
        }

        @Override
        public ListOperationResult<Boolean> apply(ListOperation operation) {
            ensureActiveConnection();
            Objects.requireNonNull(operation, "Operation cannot be null");

            UUID scopeOwnerId = null;
            if (operation.getScope() != null) {
                if (operation.getScope() == EntryScope.CONNECTION) {
                    scopeOwnerId = topic.getCurrentNodeId();
                } else {
                    scopeOwnerId = JsonUtil.TOPIC_SCOPE_ID;
                }
            }

            ListKey referenceKey = operation.getReferenceKey();
            ListKey valueKey = operation.getChangeKey();
            ObjectNode change = JsonUtil.createListChange(operation.getType(),
                    name,
                    valueKey != null ? valueKey.getKey().toString() : null,
                    referenceKey != null ? referenceKey.getKey().toString()
                            : null,
                    operation.getValue(), scopeOwnerId,
                    operation.getConditions(), operation.getValueConditions(),
                    operation.getEmpty());

            UUID id = UUID.randomUUID();
            return new ListOperationResult<>(new ListKey(id),
                    dispatchChangeWithBooleanResult(id,
                            valueKey != null ? valueKey.getKey() : id,
                            operation.getScope() == EntryScope.CONNECTION,
                            change));
        }

        private CompletableFuture<Boolean> dispatchChangeWithBooleanResult(
                UUID id, UUID key, boolean connectionScope, ObjectNode change) {
            CompletableFuture<Boolean> contextFuture = actionDispatcher
                    .createCompletableFuture();

            topic.setChangeResultTracker(id, result -> {
                if (connectionScope && result == ChangeResult.ACCEPTED) {
                    connectionScopedListItems
                            .computeIfAbsent(name, k -> new HashMap<>())
                            .put(key, id);
                    if (!cleanupPending) {
                        cleanupScopedData();
                    }
                }
                if (actionDispatcher != null) {
                    actionDispatcher.dispatchAction(() -> contextFuture
                            .complete(result != ChangeResult.REJECTED));
                }
            });
            actionDispatcher
                    .dispatchAction(() -> distributor.accept(id, change));

            return contextFuture;
        }

        @Override
        public TopicConnection getConnection() {
            return TopicConnection.this;
        }

        @Override
        public Optional<Duration> getExpirationTimeout() {
            Duration expirationTimeout = topic.listExpirationTimeouts.get(name);
            return Optional.ofNullable(expirationTimeout);
        }

        @Override
        public void setExpirationTimeout(Duration expirationTimeout) {
            ensureActiveConnection();

            ObjectNode change = JsonUtil.createListTimeoutChange(name,
                    expirationTimeout);

            UUID id = UUID.randomUUID();
            actionDispatcher
                    .dispatchAction(() -> distributor.accept(id, change));
        }

        private Registration subscribeToList(String listName,
                Consumer<ListChange> changeNotifier) {
            subscribersPerList
                    .computeIfAbsent(listName, key -> new ArrayList<>())
                    .add(changeNotifier);
            return () -> unsubscribeFromList(listName, changeNotifier);
        }

        private void unsubscribeFromList(String listName,
                Consumer<ListChange> changeNotifier) {
            List<Consumer<ListChange>> notifiers = subscribersPerList
                    .get(listName);
            if (notifiers == null) {
                return;
            }
            notifiers.remove(changeNotifier);
            if (notifiers.isEmpty()) {
                subscribersPerList.remove(listName);
            }
        }
    }

    private final Topic topic;
    private final UserInfo localUser;
    private final List<Registration> deactivateRegistrations = new ArrayList<>();
    private final Consumer<Boolean> topicActivationHandler;
    private final Map<String, List<Consumer<MapChange>>> subscribersPerMap = new HashMap<>();
    private final Map<String, List<Consumer<ListChange>>> subscribersPerList = new HashMap<>();
    private final Map<String, Map<String, UUID>> connectionScopedMapKeys = new HashMap<>();
    private final Map<String, Map<UUID, UUID>> connectionScopedListItems = new HashMap<>();

    private volatile boolean cleanupPending;

    private final BiConsumer<UUID, ObjectNode> distributor;
    private final SerializableFunction<TopicConnection, Registration> connectionActivationCallback;
    private Registration closeRegistration;
    private ActionDispatcher actionDispatcher;
    /**
     * Whether activation has happened, which isn't necessarily the same as
     * being active because of the asynchronous step before activation is
     * actually applied.
     */
    private boolean activated;

    TopicConnection(
            SerializableSupplier<CollaborationEngine> collaborationEngineSupplier,
            ConnectionContext context, Topic topic,
            BiConsumer<UUID, ObjectNode> distributor, UserInfo localUser,
            Consumer<Boolean> topicActivationHandler,
            SerializableFunction<TopicConnection, Registration> connectionActivationCallback) {
        this.topic = topic;
        this.distributor = distributor;
        this.localUser = localUser;
        this.topicActivationHandler = topicActivationHandler;
        this.connectionActivationCallback = connectionActivationCallback;
        this.closeRegistration = context.init(this::acceptActionDispatcher,
                command -> collaborationEngineSupplier.get()
                        .getExecutorService().execute(command));
    }

    private void handleChange(UUID id, ChangeDetails change) {
        try {
            if (change instanceof MapChange) {
                handleMapChange(id, (MapChange) change);
            } else if (change instanceof ListChange) {
                handleListChange(id, (ListChange) change);
            } else {
                throw new UnsupportedOperationException(
                        "Type '" + change.getClass().getName()
                                + "' is not a supported change type");
            }
        } catch (RuntimeException e) {
            deactivateAndClose();
            throw e;
        }
    }

    private void handleMapChange(UUID id, MapChange mapChange) {
        String mapName = mapChange.getMapName();
        String key = mapChange.getKey();

        Map<String, UUID> keys = connectionScopedMapKeys.get(mapName);
        if (keys != null) {
            if (keys.containsKey(key)
                    && mapChange.getType() == MapChangeType.REPLACE) {
                keys.put(key, mapChange.getRevisionId());
            }
            // If there is a connection scoped entry for the same key with a
            // different id, cleanup the existing entry
            if (!Objects.equals(id, keys.get(key))) {
                UUID uuid = keys.get(key);
                if (!Objects.equals(mapChange.getExpectedId(), uuid)) {
                    keys.remove(key);
                }
            }
        }
        if (mapChange.hasChanges()) {
            EventUtil.fireEvents(subscribersPerMap.get(mapName),
                    notifier -> notifier.accept(mapChange), false);
        }
    }

    private void handleListChange(UUID id, ListChange listChange) {
        String listName = listChange.getListName();
        UUID key = listChange.getKey();

        Map<UUID, UUID> keys = connectionScopedListItems.get(listName);
        if (keys != null) {
            // If there is a connection scoped entry for the same key with a
            // different id, cleanup the existing entry
            if (!Objects.equals(id, keys.get(key))) {
                UUID uuid = keys.get(key);
                if (!Objects.equals(listChange.getExpectedId(), uuid)) {
                    keys.remove(key);
                }
            }
        }
        EventUtil.fireEvents(subscribersPerList.get(listChange.getListName()),
                notifier -> notifier.accept(listChange), false);
    }

    Topic getTopic() {
        return topic;
    }

    /**
     * Gets the user who is related to this topic connection.
     *
     * @return the related user, not {@code null}
     *
     * @since 1.0
     */
    public UserInfo getUserInfo() {
        return localUser;
    }

    private boolean isActive() {
        return this.actionDispatcher != null;
    }

    private void addRegistration(Registration registration) {
        if (registration != null) {
            deactivateRegistrations.add(registration);
        }
    }

    /**
     * Gets a collaboration map that can be used to track multiple values in a
     * single topic.
     *
     * @param name
     *            the name of the map
     * @return the collaboration map, not <code>null</code>
     *
     * @since 1.0
     */
    public CollaborationMap getNamedMap(String name) {
        ensureActiveConnection();
        return new CollaborationMapImplementation(name);
    }

    /**
     * Gets a collaboration list that can be used to track a list of items in a
     * single topic.
     *
     * @param name
     *            the name of the list
     * @return the collaboration list, not <code>null</code>
     */
    public CollaborationList getNamedList(String name) {
        ensureActiveConnection();
        return new CollaborationListImplementation(name);
    }

    CompletableFuture<Void> deactivateAndClose() {
        final CompletableFuture<Void> result;
        try {
            deactivate();
        } finally {
            result = closeWithoutDeactivating();
        }
        return result;
    }

    private void deactivate() {
        try {
            cleanupScopedData();
            EventUtil.fireEvents(deactivateRegistrations, Registration::remove,
                    false);
            deactivateRegistrations.clear();
        } catch (RuntimeException e) {
            if (actionDispatcher != null) {
                this.topicActivationHandler.accept(false);
                this.actionDispatcher = null;
            }
            closeWithoutDeactivating();
            throw e;
        }
    }

    private CompletableFuture<Void> closeWithoutDeactivating() {
        if (closeRegistration != null) {
            try {
                closeRegistration.remove();
                if (closeRegistration instanceof AsyncRegistration) {
                    return ((AsyncRegistration) closeRegistration).getFuture();
                }
            } finally {
                closeRegistration = null;
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    private void cleanupScopedData() {
        synchronized (topic) {
            connectionScopedMapKeys.forEach(
                    (mapName, mapKeys) -> mapKeys.forEach((key, id) -> {
                        ObjectNode change = JsonUtil.createPutChange(mapName,
                                key, null, null, null);
                        change.put(JsonUtil.CHANGE_EXPECTED_ID, id.toString());
                        distributor.accept(UUID.randomUUID(), change);
                    }));
            connectionScopedMapKeys.clear();
            connectionScopedListItems.forEach(
                    (listName, listItems) -> listItems.forEach((key, id) -> {
                        ObjectNode change = JsonUtil.createListChange(
                                ListOperation.OperationType.SET, listName,
                                key.toString(), null, null, null,
                                Collections.emptyMap(), Collections.emptyMap(),
                                null);
                        change.put(JsonUtil.CHANGE_EXPECTED_ID, id.toString());
                        distributor.accept(UUID.randomUUID(), change);
                    }));
            connectionScopedListItems.clear();
            cleanupPending = false;
        }
    }

    private void ensureActiveConnection() {
        if (!isActive()) {
            throw new IllegalStateException("Cannot perform this "
                    + "operation on a connection that is inactive or about to become inactive.");
        }
    }

    private void acceptActionDispatcher(ActionDispatcher actionDispatcher) {
        if (actionDispatcher != null) {
            if (activated) {
                throw new IllegalStateException(
                        "The topic connection is already active.");
            }
            activated = true;

            if (this.actionDispatcher != null) {
                /*
                 * Activation while already active based on non-null dispatcher
                 * means that deactivation has been triggered but its dispatch
                 * has not yet run. The dispatch will be ignored based on the
                 * flag which means that there's also no need to activate things
                 * again.
                 */
                return;
            }

            actionDispatcher.dispatchAction(() -> {
                if (!activated) {
                    /*
                     * Activation canceled while waiting for dispatch.
                     */
                    return;
                }
                if (this.actionDispatcher != null) {
                    throw new IllegalStateException(
                            "Activation dispatch is run out-of-order.");
                }

                this.actionDispatcher = actionDispatcher;
                cleanupPending = true;
                topicActivationHandler.accept(true);
                Registration changeRegistration = subscribeToChange();
                Registration callbackRegistration = connectionActivationCallback
                        .apply(this);
                addRegistration(callbackRegistration);
                addRegistration(() -> {
                    synchronized (topic) {
                        changeRegistration.remove();
                    }
                });

                distributor.accept(UUID.randomUUID(),
                        JsonUtil.createNodeActivate(topic.getCurrentNodeId()));
            });
        } else {
            if (!activated) {
                throw new IllegalStateException(
                        "The topic connection is already inactive.");
            }
            activated = false;
            if (this.actionDispatcher == null) {
                /*
                 * Deactivation while already inactive based on null dispatcher
                 * means that activation has been triggered but its dispatch has
                 * not yet run. The dispatch will be ignored based on the flag
                 * which means that there's also no need to deactivate things
                 * again.
                 */
                return;
            }
            this.actionDispatcher.dispatchAction(() -> {
                if (activated) {
                    /*
                     * Deactivation canceled while waiting for dispatch.
                     */
                    return;
                }
                if (this.actionDispatcher == null) {
                    throw new IllegalStateException(
                            "Deactivation dispatch is run out-of-order.");
                }

                try {
                    distributor.accept(UUID.randomUUID(), JsonUtil
                            .createNodeDeactivate(topic.getCurrentNodeId()));
                    this.actionDispatcher = null;
                    this.deactivate();
                } finally {
                    topicActivationHandler.accept(false);
                }
            });
        }
    }

    private Registration subscribeToChange() {
        synchronized (topic) {
            return topic.subscribeToChange((id, change) -> {
                // Dispatch only if we're still active
                if (actionDispatcher != null) {
                    actionDispatcher
                            .dispatchAction(() -> handleChange(id, change));
                }
            });
        }
    }
}
