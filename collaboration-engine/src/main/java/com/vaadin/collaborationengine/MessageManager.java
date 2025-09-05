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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vaadin.collaborationengine.CollaborationMessagePersister.FetchQuery;
import com.vaadin.collaborationengine.CollaborationMessagePersister.PersistRequest;
import com.vaadin.collaborationengine.MessageHandler.MessageContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.function.SerializableSupplier;
import com.vaadin.flow.internal.UsageStatistics;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.shared.Registration;

/**
 * Manager to handle messages sent to a topic. It allows submitting messages to
 * a topic and set a handler to react when a new message has been submitted.
 *
 * @author Vaadin Ltd
 */
public class MessageManager extends AbstractCollaborationManager {

    static {
        UsageStatistics.markAsUsed(
                CollaborationEngine.COLLABORATION_ENGINE_NAME
                        + "/MessageManager",
                CollaborationEngine.COLLABORATION_ENGINE_VERSION);
    }

    private static final Object FETCH_LOCK = new Object();

    private static final String MISSING_RECENT_MESSAGES = "The messages "
            + "returned invoking CollaborationMessagePersister.fetchMessages() "
            + "do not include the last fetched message of the previous call. "
            + "Please update the implementation to fetch all messages whose "
            + "timestamp is greater OR EQUAL with the query's timestamp.";

    static final String LIST_NAME = MessageManager.class.getName();

    private final CollaborationMessagePersister persister;

    private ConnectionContext context;

    private transient CollaborationList list;

    private MessageHandler messageHandler;

    private CollaborationMessage lastSeenMessage;

    private ListKey lastMessageKey;

    private boolean catchupMode = false;

    private final Map<CompletableFuture<Void>, CollaborationMessage> pendingMessageFutures = new LinkedHashMap<>();

    private final Map<CollaborationMessage, CompletableFuture<Void>> persistedMessageFutures = new LinkedHashMap<>();

    /**
     * Creates a new manager for the given component.
     *
     * @param component
     *            the component which holds UI access, not {@code null}
     * @param localUser
     *            the information of the local user, not {@code null}
     * @param topicId
     *            the id of the topic to connect to, not {@code null}
     */
    public MessageManager(Component component, UserInfo localUser,
            String topicId) {
        this(component, localUser, topicId, null);
    }

    /**
     * Creates a new persisting manager for the given component.
     *
     * @param component
     *            the component which holds UI access, not {@code null}
     * @param localUser
     *            the information of the local user, not {@code null}
     * @param topicId
     *            the id of the topic to connect to, not {@code null}
     * @param persister
     *            the persister to read/write messages to an external source
     */
    public MessageManager(Component component, UserInfo localUser,
            String topicId, CollaborationMessagePersister persister) {
        this(new ComponentConnectionContext(component), localUser, topicId,
                persister, CollaborationEngine::getInstance);
    }

    /**
     * Creates a new manager for the given connection context.
     *
     * @param context
     *            the context that manages connection status, not {@code null}
     * @param localUser
     *            the information of the local user, not {@code null}
     * @param topicId
     *            the id of the topic to connect to, not {@code null}
     * @param ceSupplier
     *            the collaboration engine instance to use, not {@code null}
     */
    public MessageManager(ConnectionContext context, UserInfo localUser,
            String topicId,
            SerializableSupplier<CollaborationEngine> ceSupplier) {
        this(context, localUser, topicId, null, ceSupplier);
    }

    /**
     * Creates a new persisting manager for the given connection context.
     *
     * @param context
     *            the context that manages connection status, not {@code null}
     * @param localUser
     *            the information of the local user, not {@code null}
     * @param topicId
     *            the id of the topic to connect to, not {@code null}
     * @param persister
     *            the persister to read/write messages to an external source
     * @param ceSupplier
     *            the collaboration engine instance to use, not {@code null}
     */
    public MessageManager(ConnectionContext context, UserInfo localUser,
            String topicId, CollaborationMessagePersister persister,
            SerializableSupplier<CollaborationEngine> ceSupplier) {
        super(localUser, topicId, ceSupplier);
        this.context = context;
        this.persister = persister;
        openTopicConnection(context, this::onConnectionActivate);
    }

    @Serial
    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        CollaborationEngineServiceInitListener
                .addReinitializer(this::reinitialize);
    }

    private void reinitialize(VaadinService service) {
        openTopicConnection(context, this::onConnectionActivate);
    }

    /**
     * Sets a handler which will be invoked for all messages already in the
     * topic and when a new message is submitted.
     * <p>
     * The handler accepts a {@link MessageContext} as a parameter which
     * contains a reference to the sent message.
     *
     * @param handler
     *            the message handler, or {@code null} to remove an existing
     *            handler
     */
    public void setMessageHandler(MessageHandler handler) {
        messageHandler = handler;
        lastSeenMessage = null;
        catchupMode = false;
        if (messageHandler != null) {
            getMessages().forEach(this::applyHandler);
        }
    }

    /**
     * Submits a message to the topic as the current local user.
     *
     * @param text
     *            the text of the message, not {@code null}
     * @return a future which will complete when the message has been added to
     *         the topic, not {@code null}
     */
    public CompletableFuture<Void> submit(String text) {
        Objects.requireNonNull(text);
        UserInfo user = getLocalUser();
        Instant now = getCollaborationEngine().getClock().instant();
        CollaborationMessage message = new CollaborationMessage(user, text,
                now);
        return submit(message);
    }

    /**
     * Submits a message to the topic.
     *
     * @param message
     *            the message, not {@code null}
     * @return a future which will complete when the message has been added to
     *         the topic, not {@code null}
     */
    public CompletableFuture<Void> submit(CollaborationMessage message) {
        Objects.requireNonNull(message);
        if (list == null) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            pendingMessageFutures.put(future, message);
            return future;
        } else {
            return appendOrPersist(message);
        }
    }

    private CompletableFuture<Void> appendOrPersist(
            CollaborationMessage message) {
        if (persister != null) {
            String topicId = getTopicId();
            PersistRequest request = new PersistRequest(this, topicId, message);
            persister.persistMessage(request);
            CompletableFuture<Void> future = new CompletableFuture<>();
            persistedMessageFutures.put(message, future);
            fetchPersistedList();
            return future;
        } else {
            return list.insertLast(message).getCompletableFuture();
        }
    }

    private Registration onConnectionActivate(TopicConnection topicConnection) {
        list = topicConnection.getNamedList(LIST_NAME);
        list.subscribe(this::onListChange);
        fetchPersistedList();
        pendingMessageFutures.entrySet().removeIf(entry -> {
            CompletableFuture<Void> future = entry.getKey();
            CollaborationMessage message = entry.getValue();
            appendOrPersist(message).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    future.completeExceptionally(throwable);
                } else {
                    future.complete(result);
                }
            });
            return true;
        });
        return this::onConnectionDeactivate;
    }

    private void onConnectionDeactivate() {
        list = null;
        catchupMode = true;
    }

    private void onListChange(ListChangeEvent event) {
        CollaborationMessage message = event
                .getValue(CollaborationMessage.class);
        lastMessageKey = event.getKey();
        if (message != null) {
            CompletableFuture<Void> future = persistedMessageFutures
                    .remove(message);
            if (future != null) {
                future.complete(null);
            }
            applyHandler(message);
        }
    }

    private void applyHandler(CollaborationMessage message) {
        if (!catchupMode) {
            lastSeenMessage = message;
            if (messageHandler != null) {
                MessageContext context = new DefaultMessageContext(message);
                messageHandler.handleMessage(context);
            }
        } else if (message.equals(lastSeenMessage)) {
            catchupMode = false;
        }
    }

    private void fetchPersistedList() {
        if (persister != null && list != null) {
            String topicId = getTopicId();
            synchronized (FETCH_LOCK) {
                List<CollaborationMessage> recentMessages = getRecentMessages();
                Instant since = recentMessages.isEmpty() ? Instant.EPOCH
                        : recentMessages.get(0).getTime();
                FetchQuery query = new FetchQuery(this, topicId, since);
                List<CollaborationMessage> messages = persister
                        .fetchMessages(query)
                        .sorted(Comparator
                                .comparing(CollaborationMessage::getTime))
                        .filter(message -> !recentMessages.remove(message))
                        .collect(Collectors.toList());
                if (!recentMessages.isEmpty()) {
                    throw new IllegalStateException(MISSING_RECENT_MESSAGES);
                }
                if (!messages.isEmpty()) {
                    query.throwIfPropsNotUsed();
                    insertPersistedMessages(messages);
                }
            }
        }
    }

    private void insertPersistedMessages(List<CollaborationMessage> messages) {
        ListKey ifLast = lastMessageKey;
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (CollaborationMessage message : messages) {
            ListOperation op = ListOperation.insertLast(message);
            if (ifLast != null) {
                op.ifLast(ifLast);
            } else {
                op.ifEmpty();
            }
            ListOperationResult<Boolean> insert = list.apply(op);
            futures.add(insert.getCompletableFuture());
            ifLast = insert.getKey();
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenAccept(result -> fetchPersistedList());
    }

    private List<CollaborationMessage> getRecentMessages() {
        List<CollaborationMessage> messages = getMessages()
                .collect(Collectors.toList());
        CollaborationMessage lastMessage = messages.isEmpty() ? null
                : messages.get(messages.size() - 1);
        List<CollaborationMessage> recentMessages = new ArrayList<>();
        if (lastMessage != null) {
            Instant lastMessageTime = lastMessage.getTime();
            for (int i = messages.size() - 1; i >= 0; i--) {
                CollaborationMessage m = messages.get(i);
                if (m.getTime().equals(lastMessageTime)) {
                    recentMessages.add(m);
                } else {
                    break;
                }
            }
        }
        return recentMessages;
    }

    // Package protected for testing
    Stream<CollaborationMessage> getMessages() {
        if (list != null) {
            return list.getItems(CollaborationMessage.class).stream();
        } else {
            return Stream.empty();
        }
    }

    static class DefaultMessageContext implements MessageContext {

        private final CollaborationMessage message;

        public DefaultMessageContext(CollaborationMessage message) {
            this.message = message;
        }

        @Override
        public CollaborationMessage getMessage() {
            return message;
        }
    }
}
