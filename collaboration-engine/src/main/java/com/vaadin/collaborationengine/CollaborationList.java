/*
 * Copyright 2000-2021 Vaadin Ltd.
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

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.core.type.TypeReference;

import com.vaadin.flow.shared.Registration;

/**
 * A list that is shared between multiple users. List instances can be retrieved
 * through a {@link TopicConnection}. Changes performed by one user will be
 * delivered as events to subscribers defined by other users.
 * 
 * @author Vaadin Ltd
 */
public interface CollaborationList {

    /**
     * Gets the list items as instances of the given class.
     *
     * @param type
     *            the expected type of the items
     * @param <T>
     *            the type of the class given as the argument
     * @return a list of the items
     * @throws JsonConversionException
     *             if one or more values in the list cannot be converted to an
     *             instance of the given class
     */
    <T> List<T> getItems(Class<T> type);

    /**
     * Gets the list items as instances of the given type reference.
     *
     * @param type
     *            the reference of the expected type of the items
     * @param <T>
     *            the type of the reference given as the argument
     * @return a list of the items
     * @throws JsonConversionException
     *             if one or more values in the list cannot be converted to an
     *             instance of the given type reference
     */
    <T> List<T> getItems(TypeReference<T> type);

    /**
     * Appends the given item to the list.
     * <p>
     * The given item must be JSON-serializable so it can be sent over the
     * network when Collaboration Engine is hosted in a standalone server.
     *
     * @param item
     *            the item to append, not <code>null</code>
     * @return a completable future that is resolved when the item has been
     *         appended to the list
     * @throws JsonConversionException
     *             if the given item isn't serializable as JSON string
     */
    CompletableFuture<Void> append(Object item);

    /**
     * Subscribes to changes to this list. When subscribing, the subscriber will
     * receive an event for each item already in the list.
     *
     * @param subscriber
     *            the subscriber to use, not <code>null</code>
     * @return a handle that can be used for removing the subscription, not
     *         <code>null</code>
     */
    Registration subscribe(ListSubscriber subscriber);
}
