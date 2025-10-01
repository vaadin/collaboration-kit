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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.vaadin.flow.shared.Registration;

import tools.jackson.core.type.TypeReference;

/**
 * A map that is shared between multiple users. Map instances can be retrieved
 * through a {@link TopicConnection}. Changes performed by one user will be
 * delivered as events to subscribers defined by other users.
 *
 * @author Vaadin Ltd
 * @since 1.0
 */
public interface CollaborationMap extends HasExpirationTimeout, Serializable {

    /**
     * Gets the map value for the given key as an instance of the given class.
     *
     * @param key
     *            the string key for which to get a value, not <code>null</code>
     * @param type
     *            the expected type
     * @return the value associated with the key, or <code>null</code> if no
     *         value is present
     * @throws JsonConversionException
     *             if the value in the map cannot be converted to an instance of
     *             the given class
     *
     * @since 1.0
     */
    <T> T get(String key, Class<T> type);

    /**
     * Gets the map value for the given key as an instance corresponding to the
     * given type reference.
     *
     * @param key
     *            the string key for which to get a value, not <code>null</code>
     * @param type
     *            the type reference of the expected type to get
     * @return the value associated with the key, or <code>null</code> if no
     *         value is present
     * @throws JsonConversionException
     *             if the value in the map cannot be converted to an instance of
     *             the given type reference
     *
     * @since 1.0
     */
    <T> T get(String key, TypeReference<T> type);

    /**
     * Associates the given value with the given key. This method can also be
     * used to remove an association by passing <code>null</code> as the value.
     * Subscribers are notified if the new value isn't <code>equals()</code>
     * with the old value.
     * <p>
     * The given value must be JSON-serializable so it can be sent over the
     * network when Collaboration Engine is hosted in a standalone server.
     *
     * @param key
     *            the string key for which to make an association, not
     *            <code>null</code>
     * @param value
     *            the value to set, or <code>null</code> to remove the
     *            association
     * @return a completable future that is resolved when the data update is
     *         completed.
     * @throws JsonConversionException
     *             if the given value isn't serializable as JSON string
     *
     * @since 1.0
     */
    default CompletableFuture<Void> put(String key, Object value) {
        return put(key, value, EntryScope.TOPIC);
    }

    /**
     * Removes the value with the given key. Subscribers are notified if there
     * exists a non-null value.
     *
     * @param key
     *            the string key for which to make an association, not
     *            <code>null</code>
     * @return a completable future that is resolved when the data update is
     *         completed.
     */
    default CompletableFuture<Void> remove(String key) {
        return put(key, null);
    }

    /**
     * Removes the value with the given key only if and only if the current
     * value is as expected. Subscribers are notified if the current value is
     * non-null.
     *
     * @param key
     *            the string key for which to make an association, not
     *            <code>null</code>
     * @param expectedValue
     *            the value to compare with the current value to determine
     *            whether to remove the value, or <code>null</code> to expect
     *            that no value is present.
     * @return a boolean completable future that is resolved when the removal is
     *         completed. The resolved value is <code>true</code> if the
     *         expected value was present so that the operation could proceed;
     *         <code>false</code> if the expected value was not present
     */
    default CompletableFuture<Boolean> remove(String key,
            Object expectedValue) {
        return replace(key, expectedValue, null);
    }

    /**
     * Associates the given value with the given key and scope. This method can
     * also be used to remove an association by passing <code>null</code> as the
     * value. Subscribers are notified if the new value isn't
     * <code>equals()</code> with the old value.
     * <p>
     * The given value must be JSON-serializable so it can be sent over the
     * network when Collaboration Engine is hosted in a standalone server.
     * <p>
     * The <code>scope</code> parameter specifies the scope of the entry, which
     * is either one of {@link EntryScope#TOPIC} to keep the entry in the map
     * until explicitly removed, or {@link EntryScope#CONNECTION} to
     * automatically remove the entry when the connection which put the entry is
     * deactivated. Putting the same value will update the ownership and the
     * scope of the entry, but listeners won't be invoked.
     *
     * @param key
     *            the string key for which to make an association, not
     *            <code>null</code>
     * @param value
     *            the value to set, or <code>null</code> to remove the
     *            association
     * @param scope
     *            the scope of the entry, not <code>null</code>
     * @return a completable future that is resolved when the data update is
     *         completed.
     * @throws JsonConversionException
     *             if the given value isn't serializable as JSON string
     *
     * @since 4.0
     */
    CompletableFuture<Void> put(String key, Object value, EntryScope scope);

    /**
     * Atomically replaces the value for a key if and only if the current value
     * is as expected. Subscribers are notified if the new value isn't
     * <code>equals()</code> with the old value. <code>equals()</code> is also
     * used to compare the current value with the expected value.
     * <p>
     * The given value must be JSON-serializable so it can be sent over the
     * network when Collaboration Engine is hosted in a standalone server.
     *
     * @param key
     *            the string key for which to make an association, not
     *            <code>null</code>
     * @param expectedValue
     *            the value to compare with the current value to determine
     *            whether to make an update, or <code>null</code> to expect that
     *            no value is present
     * @param newValue
     *            the new value to set, or <code>null</code> to remove the
     *            association
     * @return a boolean completable future that is resolved when the data
     *         update is completed. The resolved value is <code>true</code> if
     *         the expected value was present so that the operation could
     *         proceed; <code>false</code> if the expected value was not present
     * @throws JsonConversionException
     *             if the given value isn't serializable as JSON string
     *
     * @since 1.0
     */
    CompletableFuture<Boolean> replace(String key, Object expectedValue,
            Object newValue);

    /**
     * Gets a stream of the currently available keys. The stream is backed by a
     * current snapshot of the available keys and will thus not update even if
     * keys are added or removed before the stream is processed.
     *
     * @return the stream of keys, not <code>null</code>
     *
     * @since 1.0
     */
    Stream<String> getKeys();

    /**
     * Subscribes to changes to this map. When subscribing, the subscriber will
     * receive an event for each current value association.
     *
     * @param subscriber
     *            the subscriber to use, not <code>null</code>
     * @return a handle that can be used for removing the subscription, not
     *         <code>null</code>
     *
     * @since 1.0
     */
    Registration subscribe(MapSubscriber subscriber);

    /**
     * Gets the topic connection which is used to propagate changes to this map.
     *
     * @return the topic connection used by this map, not <code>null</code>
     *
     * @since 1.0
     */
    TopicConnection getConnection();

    /**
     * Gets the optional expiration timeout of this map. An empty
     * {@link Optional} is returned if no timeout is set, which means the map is
     * not cleared when there are no connected users to the related topic (this
     * is the default).
     *
     * @return the expiration timeout
     */
    @Override
    Optional<Duration> getExpirationTimeout();

    /**
     * Sets the expiration timeout of this map. If set, this map data is cleared
     * when {@code expirationTimeout} has passed after the last connection to
     * the topic this map belongs to is closed. If set to {@code null}, the
     * timeout is cancelled.
     *
     * @param expirationTimeout
     *            the expiration timeout
     */
    @Override
    void setExpirationTimeout(Duration expirationTimeout);
}
