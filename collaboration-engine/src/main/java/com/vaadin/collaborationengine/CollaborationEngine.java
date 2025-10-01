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

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.collaborationengine.Backend.EventLog;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.di.Instantiator;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.internal.UsageStatistics;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.shared.Registration;

import jakarta.servlet.ServletContext;
import tools.jackson.databind.node.ObjectNode;

/**
 * CollaborationEngine is an API for creating collaborative experiences in
 * Vaadin applications. It's used by sending and subscribing to changes between
 * collaborators via {@link TopicConnection collaboration topics}.
 * <p>
 * Use {@link #getInstance()} to get a reference to the singleton object in
 * cases where Vaadin's thread locals are defined (such as in UI code invoked by
 * the framework). In other circumstances, an instance can be found as an
 * attribute in the runtime context (typically {@link ServletContext}) using the
 * fully qualified class name of this class as the attribute name. That instance
 * will only be available after explicitly calling
 * {@link #configure(VaadinService, CollaborationEngineConfiguration)} during
 * startup or calling {@link #getInstance()} at least once.
 *
 * @author Vaadin Ltd
 * @since 1.0
 */
public class CollaborationEngine {

    private static class TopicAndEventLog {
        private final Topic topic;
        private final EventLog eventLog;

        public TopicAndEventLog(Topic topic, EventLog eventLog) {
            this.topic = topic;
            this.eventLog = eventLog;
        }
    }

    static final Logger LOGGER = LoggerFactory
            .getLogger(CollaborationEngine.class);

    static final String COLLABORATION_ENGINE_NAME = "vaadin-collaboration-engine";
    static final String COLLABORATION_ENGINE_VERSION = "7.0";

    static final int USER_COLOR_COUNT = 7;

    private Map<String, TopicAndEventLog> topics = new ConcurrentHashMap<>();
    private Map<String, Integer> userColors = new ConcurrentHashMap<>();
    private Map<String, Integer> activeTopicsCount = new ConcurrentHashMap<>();
    private final Set<TopicConnectionRegistration> registrations = ConcurrentHashMap
            .newKeySet();

    private CollaborationEngineConfiguration configuration;

    private final TopicActivationHandler topicActivationHandler;

    private Clock clock = Clock.systemUTC();

    private ExecutorService executorService;

    private VaadinService vaadinService;

    private SystemConnectionContext systemContext;

    private final AtomicBoolean active = new AtomicBoolean(true);

    static {
        UsageStatistics.markAsUsed(COLLABORATION_ENGINE_NAME,
                COLLABORATION_ENGINE_VERSION);
    }

    CollaborationEngine() {
        // package-protected to hide from users but to be usable in unit tests
        this((topicId, isActive) -> {
            // implement network sync to the topic
        });
    }

    CollaborationEngine(TopicActivationHandler topicActivationHandler) {
        this.topicActivationHandler = topicActivationHandler;
    }

    private void updateTopicActivation(String topicId, Boolean isActive) {
        if (isActive) {
            activeTopicsCount.putIfAbsent(topicId, 0);
        }
        activeTopicsCount.computeIfPresent(topicId, (topic, count) -> {
            int newCount = isActive ? count + 1 : count - 1;
            if (newCount <= 0) {
                activeTopicsCount.remove(topicId);
                topicActivationHandler.setActive(topicId, false);
            } else if (isActive && newCount == 1) {
                topicActivationHandler.setActive(topicId, true);
            }
            return newCount;
        });
    }

    /**
     * Gets the {@link CollaborationEngine} instance from the current
     * {@link VaadinService}.
     * <p>
     * Situations without a current {@code VaadinService} can also find the
     * corresponding instance by looking it up from the runtime context (such as
     * {@link ServletContext}) using {@code CollaborationEngine.class.getName()}
     * as the attribute name. That instance will only be available after
     * explicitly calling
     * {@link #configure(VaadinService, CollaborationEngineConfiguration)}
     * during startup or calling {@link #getInstance()} at least once.
     *
     * @return the {@link CollaborationEngine} instance
     *
     * @since 1.0
     */
    public static CollaborationEngine getInstance() {
        VaadinService service = VaadinService.getCurrent();
        if (service == null) {
            throw new IllegalStateException(
                    "Cannot get the current CollaborationEngine instance when there is no current VaadinService instance.");
        }
        return getInstance(service);
    }

    /**
     * Gets the {@link CollaborationEngine} instance from the provided
     * {@link VaadinService}.
     *
     * @return the {@link CollaborationEngine} instance
     *
     * @since 3.0
     */
    public static CollaborationEngine getInstance(VaadinService vaadinService) {
        Objects.requireNonNull(vaadinService, "VaadinService cannot be null");

        return vaadinService.getContext().getAttribute(
                CollaborationEngine.class,
                () -> getOrCreateConfiguration(vaadinService));
    }

    private static CollaborationEngine getOrCreateConfiguration(
            VaadinService vaadinService) {
        Instantiator instantiator = vaadinService.getInstantiator();
        CollaborationEngineConfiguration configuration = instantiator
                .getOrCreate(CollaborationEngineConfiguration.class);
        return CollaborationEngine.configure(vaadinService, configuration,
                new CollaborationEngine(), false);
    }

    /**
     * Sets the configuration for the Collaboration Engine associated with the
     * given Vaadin service. This configuration is required when running in
     * production mode. It can be set only once.
     * <p>
     * You should register a {@link VaadinServiceInitListener} where you call
     * this method with the service returned by
     * {@link ServiceInitEvent#getSource()}.
     *
     * @param vaadinService
     *            the Vaadin service for which to configure the Collaboration
     *            Engine
     * @param configuration
     *            the configuration to provide for the Collaboration Engine
     * @return the configured Collaboration Engine instance
     *
     * @since 3.0
     */
    public static CollaborationEngine configure(VaadinService vaadinService,
            CollaborationEngineConfiguration configuration) {
        return configure(vaadinService, configuration,
                new CollaborationEngine(), true);
    }

    static CollaborationEngine configure(VaadinService vaadinService,
            CollaborationEngineConfiguration configuration,
            CollaborationEngine ce, boolean storeInService) {
        Objects.requireNonNull(vaadinService, "VaadinService cannot be null");
        Objects.requireNonNull(configuration, "Configuration cannot be null");
        if (vaadinService.getContext()
                .getAttribute(CollaborationEngine.class) != null) {
            throw new IllegalStateException(
                    "Collaboration Engine has been already configured for the provided VaadinService. "
                            + "The configuration can be provided only once.");
        }
        configuration.setVaadinService(vaadinService);
        ce.configuration = configuration;
        ce.vaadinService = vaadinService;
        ce.systemContext = new SystemConnectionContext(() -> ce);

        configuration.getBackend().setCollaborationEngine(ce);

        ExecutorService executorService = ce.configuration.getExecutorService();
        final boolean useManagedExecutorService = executorService == null;
        if (useManagedExecutorService) {
            ce.executorService = Executors.newFixedThreadPool(
                    Runtime.getRuntime().availableProcessors());
        } else {
            ce.executorService = executorService;
        }

        vaadinService.addServiceDestroyListener(event -> {
            ce.active.set(false);
            ce.clearConnections();
            if (useManagedExecutorService) {
                LOGGER.info("Shutting down thread pool");
                ce.executorService.shutdown();
            }
        });
        if (storeInService) {
            // Avoid storing from inside computeIfAbsent
            vaadinService.getContext().setAttribute(CollaborationEngine.class,
                    ce);
        }
        return ce;
    }

    /**
     * Opens a connection to the collaboration topic with the provided id based
     * on a component instance. If the topic with the provided id does not exist
     * yet, it's created on demand.
     *
     * @param component
     *            the component which hold UI access, not {@code null}
     * @param topicId
     *            the id of the topic to connect to, not {@code null}
     * @param localUser
     *            the user who is related to the topic connection, a
     *            {@link SystemUserInfo} can be used for non-interaction
     *            threads. Not {@code null}.
     * @param connectionActivationCallback
     *            the callback to be executed when a connection is activated,
     *            not {@code null}
     * @return the handle that can be used for configuring or closing the
     *         connection
     *
     * @since 1.0
     */
    public TopicConnectionRegistration openTopicConnection(Component component,
            String topicId, UserInfo localUser,
            SerializableFunction<TopicConnection, Registration> connectionActivationCallback) {
        Objects.requireNonNull(component, "Connection context can't be null");
        ConnectionContext context = new ComponentConnectionContext(component);
        return openTopicConnection(context, topicId, localUser,
                connectionActivationCallback);
    }

    ExecutorService getExecutorService() {
        return executorService;
    }

    private void clearConnections() {
        LOGGER.info("Deactivating connections before shutdown");
        List<CompletableFuture<Void>> futures = new ArrayList<>(
                registrations.size());
        LOGGER.debug("Closing {} connections", registrations.size());
        for (TopicConnectionRegistration registration : registrations) {
            registration.remove();
            registration.getPendingFuture().ifPresent(futures::add);
        }

        final int timeoutInSeconds = 1;
        final Instant end = Instant.now().plus(timeoutInSeconds,
                ChronoUnit.SECONDS);
        LOGGER.debug("Waiting for {} asynchronous tasks to complete",
                futures.size());
        for (CompletableFuture<Void> future : futures) {
            if (waitForFuture(future, end, timeoutInSeconds)) {
                break;
            }
        }
        LOGGER.debug("Finished waiting for asynchronous tasks");
        registrations.clear();
    }

    // Waits for the future and returns true if there is a timeout
    private static boolean waitForFuture(CompletableFuture<Void> future,
            Instant end, long timeoutInSeconds) {
        boolean timeout = false;
        final String timeoutMessage = "Timeout reached when waiting for "
                + "topic connections to be closed";
        try {
            LOGGER.trace("Waiting for future to complete");
            future.get(timeoutInSeconds, TimeUnit.SECONDS);
            LOGGER.trace("Future completed successfully");
            if (Instant.now().isAfter(end)) {
                LOGGER.warn(timeoutMessage);
                timeout = true;
            }
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.info("Exception caught when closing topic connections", e);
        } catch (TimeoutException e) {
            LOGGER.warn(timeoutMessage, e);
            timeout = true;
        }
        return timeout;
    }

    private void assertConfigured() {
        if (configuration == null) {
            throw new IllegalStateException(
                    "Collaboration Engine is missing required configuration "
                            + "that should be provided by a VaadinServiceInitListener. "
                            + "Collaboration Engine is supported only in a Vaadin application, "
                            + "where VaadinService initialization is expected to happen before usage.");
        }
    }

    /**
     * Opens a connection to the collaboration topic with the provided id based
     * on a generic context definition. If the topic with the provided id does
     * not exist yet, it's created on demand.
     *
     * @param context
     *            context for the connection
     * @param topicId
     *            the id of the topic to connect to, not {@code null}
     * @param localUser
     *            the user who is related to the topic connection, a
     *            {@link SystemUserInfo} can be used for non-interaction
     *            threads. Not {@code null}.
     * @param connectionActivationCallback
     *            the callback to be executed when a connection is activated,
     *            not {@code null}
     * @return the handle that can be used for configuring or closing the
     *         connection
     *
     * @since 1.0
     */
    public TopicConnectionRegistration openTopicConnection(
            ConnectionContext context, String topicId, UserInfo localUser,
            SerializableFunction<TopicConnection, Registration> connectionActivationCallback) {
        Objects.requireNonNull(context, "Connection context can't be null");
        Objects.requireNonNull(topicId, "Topic id can't be null");
        Objects.requireNonNull(localUser, "User can't be null");
        Objects.requireNonNull(connectionActivationCallback,
                "Callback for connection activation can't be null");

        assertConfigured();

        if (!active.get()) {
            LOGGER.info("Tried to open a connection to a closed collaboration"
                    + " engine instance");
            return createFailedTopicConnectionRegistration(context);
        }

        TopicAndEventLog topicAndConnection = topics.computeIfAbsent(topicId,
                this::createTopicAndEventLog);
        BiConsumer<UUID, ObjectNode> distributor = (id,
                node) -> topicAndConnection.eventLog.submitEvent(id,
                        JsonUtil.toString(node));
        TopicConnection connection = new TopicConnection(() -> this, context,
                topicAndConnection.topic, distributor, localUser,
                isActive -> updateTopicActivation(topicId, isActive),
                connectionActivationCallback);

        TopicConnectionRegistration registration = new TopicConnectionRegistration(
                connection, context,
                command -> getExecutorService().execute(command),
                registrations::remove);
        registrations.add(registration);
        if (!active.get()) {
            registration.remove();
            LOGGER.info("Tried to open a connection to a closed collaboration"
                    + " engine instance");
            return createFailedTopicConnectionRegistration(context);
        }
        return registration;
    }

    private TopicConnectionRegistration createFailedTopicConnectionRegistration(
            ConnectionContext context) {
        return new TopicConnectionRegistration(null, context,
                command -> getExecutorService().execute(command), r -> {
                    // No op
                });
    }

    private TopicAndEventLog createTopicAndEventLog(String id) {
        EventLog eventLog = configuration.getBackend().openEventLog(id);

        Topic topic = new Topic(id, () -> this, eventLog);
        return new TopicAndEventLog(topic, eventLog);
    }

    /**
     * Gets the color index of a user if different to -1, or let Collaboration
     * Engine provide one. If the color index for a user id does not exist yet,
     * it's created on demand based on the user id.
     *
     * @param userInfo
     *            user info
     * @return the color index
     *
     * @since 3.1
     */
    public int getUserColorIndex(UserInfo userInfo) {
        int currentColorIndex = userInfo.getColorIndex();
        if (currentColorIndex != -1) {
            return currentColorIndex;
        }
        String userId = userInfo.getId();
        if (configuration.getBackend() instanceof LocalBackend) {
            return userColors.computeIfAbsent(userId,
                    id -> userColors.size() % USER_COLOR_COUNT);
        } else {
            return Math.abs(userId.hashCode()) % USER_COLOR_COUNT;
        }
    }

    CollaborationEngineConfiguration getConfiguration() {
        return configuration;
    }

    Clock getClock() {
        return clock;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    // For testing
    Topic getTopic(String topicId) {
        return topics.get(topicId).topic;
    }

    VaadinService getVaadinService() {
        return vaadinService;
    }

    /**
     * Gets a system connection context for this collaboration engine instance.
     * The system connection context can be used when Collaboration Engine is
     * used in situations that aren't directly associated with a UI, such as
     * from a background thread or when integrating with external services.
     *
     * @return a system connection context instance, not <code>null</code>
     */
    public SystemConnectionContext getSystemContext() {
        assertConfigured();

        return systemContext;
    }
}
