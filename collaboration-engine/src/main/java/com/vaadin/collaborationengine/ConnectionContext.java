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
import java.util.concurrent.Executor;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.shared.Registration;

/**
 * The context in which a topic connection is active. This makes it possible for
 * a connection to become deactivate when it is no longer needed and activate it
 * again if the context status changes. The context does also handle
 * synchronization of date change notifications delivered to application code.
 * <p>
 * {@link ComponentConnectionContext} is used internally by all high-level
 * components such as {@link CollaborationAvatarGroup} and by shorthand methods
 * such as
 * {@link CollaborationEngine#openTopicConnection(com.vaadin.flow.component.Component, String, UserInfo, com.vaadin.flow.function.SerializableFunction)}
 * that take a component instance as the context. This implementation activates
 * the topic connection whenever the target component is attached and
 * deactivates it when the component is detached.
 * {@link UI#access(com.vaadin.flow.server.Command)} is used for
 * synchronization.
 * <p>
 * {@link SystemConnectionContext} is intended for application logic that
 * integrates with external services that are not directly related to UI
 * components. This context implementation is immediately active and remains
 * active until the application is shut down (based on
 * {@link VaadinService#addServiceDestroyListener(com.vaadin.flow.server.ServiceDestroyListener)}.
 * Each use site (for instance each individual topic connection) gets its own
 * synchronization to ensure events are delivered in the expected order, but
 * still avoiding contention between other use sites.
 *
 * @author Vaadin Ltd
 * @since 1.0
 */
public interface ConnectionContext extends Serializable {

    /**
     * Initializes the connection context with a {@link ActivationHandler} and
     * an {@link Executor}.
     * <p>
     * The method {@link ActivationHandler#accept(Object)} from the provided
     * {@link ActivationHandler} should be called with an
     * {@link ActionDispatcher} when this ConnectionContext is activated. When
     * this ConnectionContext is deactivated, it should call
     * {@link ActivationHandler#accept(Object)} with a null parameter.
     * <p>
     * The {@link ActionDispatcher} should ensure synchronization within the
     * context of this ConnectionContext.
     *
     * @param activationHandler
     *            the handler for activation changes
     * @param executor
     *            executor that should be used by the handler to execute
     *            background tasks. Not <code>null</code>
     * @return the registration for any logic that needs to be cleaned up if the
     *         connection is closed permanently, or <code>null</code> if there
     *         is nothing to clean up
     */
    Registration init(ActivationHandler activationHandler, Executor executor);

}
