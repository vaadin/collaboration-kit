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
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.collaborationengine.PresenceHandler.PresenceContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.function.SerializableSupplier;
import com.vaadin.flow.internal.UsageStatistics;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.shared.Registration;

/**
 * Manager to handle user presence in topics. It allows to set the user presence
 * in the set topic and get a list of users currently set as present in the same
 * topic. A handler can be set to react when the presence of a user changes.
 *
 * @author Vaadin Ltd
 * @since 3.2
 */
public class PresenceManager extends AbstractCollaborationManager {

    static {
        UsageStatistics.markAsUsed(
                CollaborationEngine.COLLABORATION_ENGINE_NAME
                        + "/PresenceManager",
                CollaborationEngine.COLLABORATION_ENGINE_VERSION);
    }

    static final Logger LOGGER = LoggerFactory.getLogger(PresenceManager.class);

    private static class UserEntry implements Serializable {
        private int count = 0;
        private Registration registration;
    }

    static final String LIST_NAME = PresenceManager.class.getName();

    private final Map<String, UserEntry> userEntries = new LinkedHashMap<>();

    private ListKey ownPresenceKey;

    private ConnectionContext context;

    private transient CollaborationList list;

    private PresenceHandler presenceHandler;

    private boolean markAsPresent = false;

    private transient Registration subscribeRegistration;

    /**
     * Creates a new manager for the provided component.
     * <p>
     * The provided user information is used to set the presence of the local
     * user with {@link #markAsPresent(boolean)} (the default is {@code false}).
     * <p>
     *
     * @param component
     *            the component which holds UI access, not {@code null}
     * @param localUser
     *            the information of the local user, not {@code null}
     * @param topicId
     *            the id of the topic to connect to, not {@code null}
     */
    public PresenceManager(Component component, UserInfo localUser,
            String topicId) {
        this(new ComponentConnectionContext(component), localUser, topicId,
                CollaborationEngine::getInstance);
    }

    /**
     * Creates a new manager for the provided connection context.
     * <p>
     * The provided user information is used to set the presence of the local
     * user with {@link #markAsPresent(boolean)} (the default is {@code false}).
     * <p>
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
    public PresenceManager(ConnectionContext context, UserInfo localUser,
            String topicId,
            SerializableSupplier<CollaborationEngine> ceSupplier) {
        super(localUser, topicId, ceSupplier);
        this.context = context;
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
     * Configures the manager to mark the local user present in the topic.
     * <p>
     * If the user wasn't already present in the topic, all managers connected
     * to the same topic will be notified of the change and their handlers will
     * be applied to the user instance.
     *
     * @param markAsPresent
     *            {@code true} to mark the user as present in the topic,
     *            {@code false} to set as not present
     */
    public void markAsPresent(boolean markAsPresent) {
        if (this.markAsPresent != markAsPresent && list != null) {
            if (markAsPresent) {
                addLocalUserToTopic();
            } else {
                removeLocalUserFromTopic();
            }
        }
        this.markAsPresent = markAsPresent;
    }

    private void addLocalUserToTopic() {
        assert ownPresenceKey == null;
        ListOperation operation = ListOperation.insertLast(getLocalUser())
                .withScope(EntryScope.CONNECTION);
        ownPresenceKey = list.apply(operation).getKey();
    }

    private void removeLocalUserFromTopic() {
        assert ownPresenceKey != null;
        list.set(ownPresenceKey, null);
        ownPresenceKey = null;
    }

    /**
     * Sets a handler which will be invoked when a user becomes present.
     * <p>
     * The handler accepts a {@link PresenceContext} instance as a parameter and
     * should return a {@link Registration} which will be removed when the user
     * stops being present.
     * <p>
     * Replacing an existing handler will remove all registrations from the
     * previous one.
     *
     * @param handler
     *            the user presence handler, or {@code null} to remove an
     *            existing handler
     */
    public void setPresenceHandler(PresenceHandler handler) {
        resetEntries();
        this.presenceHandler = handler;
        if (handler != null && list != null) {
            subscribeRegistration = list.subscribe(this::onListChange);
        }
    }

    private Registration onConnectionActivate(TopicConnection topicConnection) {
        list = topicConnection.getNamedList(LIST_NAME);
        if (markAsPresent) {
            addLocalUserToTopic();
        }
        if (this.presenceHandler != null && subscribeRegistration == null) {
            subscribeRegistration = list.subscribe(this::onListChange);
        }
        return this::onConnectionDeactivate;
    }

    private void onConnectionDeactivate() {
        ownPresenceKey = null;
        list = null;
        resetEntries();
    }

    private void onListChange(ListChangeEvent event) {
        switch (event.getType()) {
        case INSERT:
            handleNewUser(event.getValue(UserInfo.class));
            break;
        case SET:
            if (event.getValue(UserInfo.class) == null) {
                handleRemovedUser(event.getOldValue(UserInfo.class));
            } else {
                throw new UnsupportedOperationException(
                        "Cannot update an existing entry");
            }
            break;
        case MOVE:
            // Unexpected, but no problem in ignoring
            break;
        }
    }

    private void handleRemovedUser(UserInfo removedUser) {
        UserEntry userEntry = userEntries.get(removedUser.getId());
        logUserOperation("remove", removedUser, userEntry != null);
        assert userEntry != null;
        if (--userEntry.count == 0) {
            removeRegistration(userEntry);
            userEntries.remove(removedUser.getId());
        }
    }

    private void logUserOperation(String operation, UserInfo userInfo,
            boolean present) {
        LOGGER.debug("{}: handle {} user {} ({}): {}present", this, operation,
                userInfo.getName(), userInfo.getId(), !present ? "not " : "");
    }

    private void handleNewUser(UserInfo addedUser) {
        UserEntry userEntry = userEntries.computeIfAbsent(addedUser.getId(),
                ignore -> new UserEntry());
        logUserOperation("add", addedUser, userEntry.count == 0);
        if (userEntry.count++ == 0) {
            if (presenceHandler != null) {
                assert userEntry.registration == null;
                userEntry.registration = presenceHandler
                        .handlePresence(new DefaultPresenceContext(addedUser));
            }
        }
    }

    private void removeRegistration(UserEntry entry) {
        Registration registration = entry.registration;
        if (registration != null) {
            registration.remove();
            entry.registration = null;
        }
    }

    private void resetEntries() {
        if (subscribeRegistration != null) {
            subscribeRegistration.remove();
            subscribeRegistration = null;
        }

        userEntries.values().forEach(this::removeRegistration);
        LOGGER.debug("{}: clear user entries", this);
        userEntries.clear();
    }

    static class DefaultPresenceContext implements PresenceContext {

        private final UserInfo user;

        public DefaultPresenceContext(UserInfo user) {
            this.user = user;
        }

        @Override
        public UserInfo getUser() {
            return user;
        }
    }
}
