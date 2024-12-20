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
import java.time.Instant;
import java.util.EventObject;
import java.util.Objects;
import java.util.stream.Stream;

import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.function.SerializableFunction;

/**
 * Persister of {@link CollaborationMessage} items, which enables to read and
 * write messages from/to a backend, for example a database.
 * <p>
 * It can be used with a {@link CollaborationMessageList} to have the component
 * read messages from the backend when attached and write new messages to it
 * when appended to the list with a submitter component, e.g.
 * {@link CollaborationMessageInput}.
 *
 * @author Vaadin Ltd
 * @since 3.1
 */
public interface CollaborationMessagePersister extends Serializable {

    /**
     * A query to fetch messages from a backend. It provides information such as
     * the topic identifier and the timestamp since when messages should be
     * fetched.
     */
    class FetchQuery extends EventObject {
        private final String topicId;
        private final Instant since;

        private boolean getSinceCalled = false;
        private boolean getTopicIdCalled = false;

        FetchQuery(MessageManager manager, String topicId, Instant since) {
            super(manager);
            this.topicId = topicId;
            this.since = since;
        }

        /**
         * Gets the topic identifier.
         *
         * @return the topic identifier
         */
        public String getTopicId() {
            getTopicIdCalled = true;
            return topicId;
        }

        /**
         * Gets the timestamp since when messages should be fetched.
         * <p>
         * Note: You must include the messages sent during or after this
         * timestamp, <b>including</b> the messages sent at this exact time.
         * More formally, you should return all such messages, for which the
         * following condition is true:
         *
         * <pre>
         * message.getTime() >= fetchQuery.getSince()
         * </pre>
         *
         * @return the timestamp
         */
        public Instant getSince() {
            getSinceCalled = true;
            return since;
        }

        @Override
        public MessageManager getSource() {
            return (MessageManager) super.getSource();
        }

        void throwIfPropsNotUsed() {
            if (!getSinceCalled && !getTopicIdCalled) {
                throw new IllegalStateException(
                        "FetchQuery.getSince() and FetchQuery.getTopicId() were not called when fetching messages from the persister. "
                                + "These values need to be used to fetch only the messages belonging to the correct topic and submitted after the already fetched messages. "
                                + "Otherwise the message list will display duplicates or messages from other topics.");
            } else if (!getSinceCalled) {
                throw new IllegalStateException(
                        "FetchQuery.getSince() was not called when fetching messages from the persister. "
                                + "This value needs to be used to fetch only the messages which have been "
                                + "submitted after the already fetched messages. Otherwise the message list "
                                + "will display duplicates.");
            } else if (!getTopicIdCalled) {
                throw new IllegalStateException(
                        "FetchQuery.getTopicId() was not called when fetching messages from the persister. "
                                + "This value needs to be used to fetch only the messages belonging to the correct topic. "
                                + "Otherwise the message list will display messages from other topics.");
            }
        }
    }

    /**
     * A request to persist messages to a backend. It provides information such
     * as the topic identifier and the target {@link CollaborationMessage}.
     */
    class PersistRequest extends EventObject {
        private final String topicId;
        private final CollaborationMessage message;

        PersistRequest(MessageManager manager, String topicId,
                CollaborationMessage message) {
            super(manager);
            this.topicId = topicId;
            this.message = message;
        }

        /**
         * Gets the topic identifier.
         *
         * @return the topic identifier
         */
        public String getTopicId() {
            return topicId;
        }

        /**
         * Gets the message to persist.
         *
         * @return the message
         */
        public CollaborationMessage getMessage() {
            return message;
        }

        @Override
        public MessageManager getSource() {
            return (MessageManager) super.getSource();
        }
    }

    /**
     * Creates an instance of {@link CollaborationMessagePersister} from the
     * provided callbacks.
     *
     * @param fetchCallback
     *            the callback to fetch messages, not null
     * @param persistCallback
     *            the callback to persist messages, not null
     * @return the persister instance
     */
    static CollaborationMessagePersister fromCallbacks(
            SerializableFunction<FetchQuery, Stream<CollaborationMessage>> fetchCallback,
            SerializableConsumer<PersistRequest> persistCallback) {
        Objects.requireNonNull(fetchCallback,
                "The fetch callback cannot be null");
        Objects.requireNonNull(persistCallback,
                "The persist callback cannot be null");
        return new CollaborationMessagePersister() {

            @Override
            public Stream<CollaborationMessage> fetchMessages(
                    FetchQuery query) {
                return fetchCallback.apply(query);
            }

            @Override
            public void persistMessage(PersistRequest request) {
                persistCallback.accept(request);
            }
        };
    }

    /**
     * Reads a stream of {@link CollaborationMessage} items from a persistence
     * backend. The query parameter contains the topic identifier and the
     * timestamp from which messages should be read.
     * <p>
     * Note: You must include the messages sent during or after the timestamp
     * returned from {@link FetchQuery#getSince()}, <b>including</b> the
     * messages sent at that exact time. More formally, you should return all
     * such messages, for which the following condition is true:
     *
     * <pre>
     * message.getTime() >= fetchQuery.getSince()
     * </pre>
     *
     * @param query
     *            the fetch query
     * @return a stream of messages
     */
    Stream<CollaborationMessage> fetchMessages(FetchQuery query);

    /**
     * Writes a {@link CollaborationMessage} to the persistence backend.
     * <p>
     * It is recommended to let the backend set the message timestamp and only
     * use {@link CollaborationMessage#getTime()} as a fallback.
     *
     * @param request
     *            the request to persist the message
     */
    void persistMessage(PersistRequest request);
}
