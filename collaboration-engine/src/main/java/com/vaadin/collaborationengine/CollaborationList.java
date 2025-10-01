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
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.vaadin.flow.shared.Registration;

import tools.jackson.core.type.TypeReference;

/**
 * A list that is shared between multiple users. List instances can be retrieved
 * through a {@link TopicConnection}. Changes performed by one user will be
 * delivered as events to subscribers defined by other users.
 *
 * @author Vaadin Ltd
 * @since 3.1
 */
public interface CollaborationList extends HasExpirationTimeout, Serializable {

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
     * Gets the list item identifier by the given key as instance of the given
     * class.
     *
     * @param <T>
     *            the type of the value from <code>type</code> parameter, e.g.
     *            <code>String</code>
     * @param key
     *            the key of the requested item, not <code>null</code>
     * @param type
     *            the expected type of the item, not <code>null</code>
     * @return the requested item
     * @throws JsonConversionException
     *             if the value in the list cannot be converted to an instance
     *             of the given class
     * @since 4.1
     */
    <T> T getItem(ListKey key, Class<T> type);

    /**
     * Gets the list item identifier by the given key as instance of the given
     * type reference.
     *
     * @param <T>
     *            the type reference of the value from <code>type</code>
     *            parameter, e.g. <code>List<String></code>
     * @param key
     *            the key of the requested item, not <code>null</code>
     * @param type
     *            the expected type reference of the item, not <code>null</code>
     * @return the requested item
     * @throws JsonConversionException
     *             if the value in the list cannot be converted to an instance
     *             of the given class
     * @since 4.1
     */
    <T> T getItem(ListKey key, TypeReference<T> type);

    /**
     * Gets the keys for all the items on the list.
     *
     * @return the keys
     * @since 4.1
     */
    Stream<ListKey> getKeys();

    /**
     * Performs the given list operation. The operation contains the value, its
     * position and other requirements. If any of the conditions are not met,
     * the operation may fail (see {@link ListOperationResult}).
     *
     * @param operation
     *            the list operation, not <code>null</code>
     * @return the result of the operation, not <code>null</code>
     */
    ListOperationResult<Boolean> apply(ListOperation operation);

    /**
     * Inserts the given item as the first item of the list.
     *
     * @param item
     *            the item, not <code>null</code>
     * @return the result of the operation, not <code>null</code>
     */
    default ListOperationResult<Void> insertFirst(Object item) {
        ListOperation operation = ListOperation.insertFirst(item);
        return apply(operation).mapToVoid();
    }

    /**
     * Inserts the given item as the last item of the list.
     *
     * @param item
     *            the item, not <code>null</code>
     * @return the result of the operation, not <code>null</code>
     * @since 4.1
     */
    default ListOperationResult<Void> insertLast(Object item) {
        ListOperation operation = ListOperation.insertLast(item);
        return apply(operation).mapToVoid();
    }

    /**
     * Inserts the given item just before the given key location.
     *
     * @param key
     *            the position key, not <code>null</code>
     * @param item
     *            the item, not <code>null</code>
     * @return the result of the operation, not <code>null</code>
     */
    default ListOperationResult<Boolean> insertBefore(ListKey key,
            Object item) {
        ListOperation operation = ListOperation.insertBefore(key, item);
        return apply(operation);
    }

    /**
     * Inserts the given item just after the given key location.
     *
     * @param key
     *            the position key, not <code>null</code>
     * @param item
     *            the item, not <code>null</code>
     * @return the result of the operation, not <code>null</code>
     */
    default ListOperationResult<Boolean> insertAfter(ListKey key, Object item) {
        ListOperation operation = ListOperation.insertAfter(key, item);
        return apply(operation);
    }

    /**
     * Moves the second given key just before the first given key location.
     *
     * @param key
     *            the position key, not <code>null</code>
     * @param keyToMove
     *            the key to move, not <code>null</code>
     * @return a completable future that is resolved when the operation has
     *         completed, not <code>null</code>
     */
    default CompletableFuture<Boolean> moveBefore(ListKey key,
            ListKey keyToMove) {
        ListOperation operation = ListOperation.moveBefore(key, keyToMove);
        return apply(operation).getCompletableFuture();
    }

    /**
     * Moves the second given key just after the first given key location.
     *
     * @param key
     *            the position key, not <code>null</code>
     * @param keyToMove
     *            the key to move, not <code>null</code>
     * @return a completable future that is resolved when the operation has
     *         completed, not <code>null</code>
     */
    default CompletableFuture<Boolean> moveAfter(ListKey key,
            ListKey keyToMove) {
        ListOperation operation = ListOperation.moveAfter(key, keyToMove);
        return apply(operation).getCompletableFuture();
    }

    /**
     * Sets a new value for the item identified by the given key.
     *
     * @param key
     *            the item key, not <code>null</code>
     * @param value
     *            the new value of the item
     * @return the result of the operation, not <code>null</code>
     * @since 4.1
     */
    default CompletableFuture<Boolean> set(ListKey key, Object value) {
        ListOperation operation = ListOperation.set(key, value);
        return apply(operation).getCompletableFuture();
    }

    /**
     * Removes the value for the item identified by the given key.
     *
     * @param key
     *            the item key, not <code>null</code>
     * @return the result of the operation, not <code>null</code>
     */
    default CompletableFuture<Boolean> remove(ListKey key) {
        ListOperation operation = ListOperation.delete(key);
        return apply(operation).getCompletableFuture();
    }

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

    /**
     * Gets the topic connection which is used to propagate changes to this
     * list.
     *
     * @return the topic connection used by this list, not <code>null</code>
     */
    TopicConnection getConnection();

    /**
     * Gets the optional expiration timeout of this list. An empty
     * {@link Optional} is returned if no timeout is set, which means the list
     * is not cleared when there are no connected users to the related topic
     * (this is the default).
     *
     * @return the expiration timeout
     */
    @Override
    Optional<Duration> getExpirationTimeout();

    /**
     * Sets the expiration timeout of this list. If set, the list content is
     * cleared when {@code expirationTimeout} has passed after the last
     * connection to the topic this list belongs to is closed. If set to
     * {@code null}, the timeout is cancelled.
     *
     * @param expirationTimeout
     *            the expiration timeout
     */
    @Override
    void setExpirationTimeout(Duration expirationTimeout);
}
