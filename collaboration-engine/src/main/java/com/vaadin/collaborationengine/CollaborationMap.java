/*
 * Copyright (C) 2020 Vaadin Ltd
 *
 * This program is available under Commercial Vaadin Runtime License 1.0
 * (CVRLv1).
 *
 * For the full License, see http://vaadin.com/license/cvrl-1
 */
package com.vaadin.collaborationengine;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;

import com.vaadin.flow.shared.Registration;

/**
 * A map that is shared between multiple users. Map instances can be retrieved
 * through a {@link TopicConnection}. Changes performed by one user will be
 * delivered as events to subscribers defined by other users.
 *
 * @author Vaadin Ltd
 */
public interface CollaborationMap {

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
     */
    CompletableFuture<Void> put(String key, Object value);

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
     */
    CompletableFuture<Boolean> replace(String key, Object expectedValue,
            Object newValue);

    /**
     * Gets a stream of the currently available keys. The stream is backed by a
     * current snapshot of the available keys and will thus not update even if
     * keys are added or removed before the stream is processed.
     *
     * @return the stream of keys, not <code>null</code>
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
     */
    Registration subscribe(MapSubscriber subscriber);

    /**
     * Gets the topic connection which is used to propagate changes to this map.
     *
     * @return the topic connection used by this map, not <code>null</code>
     */
    TopicConnection getConnection();
}