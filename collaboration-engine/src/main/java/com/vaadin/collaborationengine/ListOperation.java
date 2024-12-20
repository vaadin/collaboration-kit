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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A list operation providing details such as the operation type, related
 * properties and conditions that should be met for the operation to succeed.
 *
 * @author Vaadin Ltd
 */
public class ListOperation {
    public enum OperationType {
        INSERT_BEFORE, INSERT_AFTER, MOVE_BEFORE, MOVE_AFTER, SET
    }

    /**
     * Creates a list operation to insert the given value as the first item of
     * the list.
     *
     * @param value
     *            the value, not <code>null</code>
     * @return the list operation, not <code>null</code>
     */
    public static ListOperation insertFirst(Object value) {
        Objects.requireNonNull(value);
        return new ListOperation(OperationType.INSERT_AFTER, value, null, null);
    }

    /**
     * Creates a list operation to insert the given value as the last item of
     * the list.
     *
     * @param value
     *            the value, not <code>null</code>
     * @return the list operation, not <code>null</code>
     */
    public static ListOperation insertLast(Object value) {
        Objects.requireNonNull(value);
        return new ListOperation(OperationType.INSERT_BEFORE, value, null,
                null);
    }

    /**
     * Creates a list operation to insert the given value just before the
     * position specified by the given key.
     *
     * @param before
     *            the position key, not <code>null</code>
     * @param value
     *            the value, not <code>null</code>
     * @return the list operation, not <code>null</code>
     */
    public static ListOperation insertBefore(ListKey before, Object value) {
        Objects.requireNonNull(before);
        Objects.requireNonNull(value);
        return new ListOperation(OperationType.INSERT_BEFORE, value, null,
                before);
    }

    /**
     * Creates a list operation to insert the given value just after the
     * position specified by the given key.
     *
     * @param after
     *            the position key, not <code>null</code>
     * @param value
     *            the value, not <code>null</code>
     * @return the list operation, not <code>null</code>
     */
    public static ListOperation insertAfter(ListKey after, Object value) {
        Objects.requireNonNull(after);
        Objects.requireNonNull(value);
        return new ListOperation(OperationType.INSERT_AFTER, value, null,
                after);
    }

    /**
     * Creates a list operation to insert the given value between the positions
     * specified by the given keys. If the given keys are not adjacent, the
     * operation will fail.
     *
     * @param prev
     *            the position of the previous item, not <code>null</code>
     * @param next
     *            the position of the next item, not <code>null</code>
     * @param value
     *            the value, not <code>null</code>
     * @return the list operation, not <code>null</code>
     */
    public static ListOperation insertBetween(ListKey prev, ListKey next,
            Object value) {
        Objects.requireNonNull(prev);
        Objects.requireNonNull(next);
        Objects.requireNonNull(value);
        return insertAfter(prev, value).ifNext(prev, next);
    }

    /**
     * Creates a list operation to move the given entry to just before the
     * position specified by the given key.
     *
     * @param before
     *            the position key, not <code>null</code>
     * @param entry
     *            the entry key to move, not <code>null</code>
     * @return the list operation, not <code>null</code>
     */
    public static ListOperation moveBefore(ListKey before, ListKey entry) {
        Objects.requireNonNull(before);
        Objects.requireNonNull(entry);
        return new ListOperation(OperationType.MOVE_BEFORE, null, entry,
                before);
    }

    /**
     * Creates a list operation to move the given entry to just after the
     * position specified by the given key.
     *
     * @param after
     *            the position key, not <code>null</code>
     * @param entry
     *            the entry key to move, not <code>null</code>
     * @return the list operation, not <code>null</code>
     */
    public static ListOperation moveAfter(ListKey after, ListKey entry) {
        Objects.requireNonNull(after);
        Objects.requireNonNull(entry);
        return new ListOperation(OperationType.MOVE_AFTER, null, entry, after);
    }

    /**
     * Creates a list operation to move the given entry between the positions
     * specified by the given keys. If the given keys are not adjacent, the
     * operation will fail.
     *
     * @param prev
     *            the position of the previous item, not <code>null</code>
     * @param next
     *            the position of the next item, not <code>null</code>
     * @param entry
     *            the entry key to move, not <code>null</code>
     * @return the list operation, not <code>null</code>
     */
    public static ListOperation moveBetween(ListKey prev, ListKey next,
            ListKey entry) {
        Objects.requireNonNull(prev);
        Objects.requireNonNull(next);
        Objects.requireNonNull(entry);
        return moveAfter(prev, entry).ifNext(prev, next);
    }

    /**
     * Creates a list operation to set the given value at the position specified
     * by the given key. By default, the value will be set as topic scope.
     *
     * @param key
     *            the position key, not <code>null</code>
     * @param value
     *            the value
     * @return the list operation, not <code>null</code>
     */
    public static ListOperation set(ListKey key, Object value) {
        Objects.requireNonNull(key);
        return new ListOperation(OperationType.SET, value, key, null);
    }

    /**
     * Creates a list operation to delete the value at the position specified by
     * the given key.
     *
     * @param key
     *            the position key, not <code>null</code>
     * @return the list operation, not <code>null</code>
     */
    public static ListOperation delete(ListKey key) {
        return set(key, null);
    }

    private final Object value;

    private final OperationType type;

    private final ListKey changeKey;

    private final ListKey referenceKey;

    private final Map<ListKey, ListKey> conditions = new HashMap<>();

    private EntryScope scope = EntryScope.TOPIC;

    private Boolean empty;

    private final Map<ListKey, Object> valueConditions = new HashMap<>();

    private ListOperation(OperationType type, Object value, ListKey changeKey,
            ListKey referenceKey) {
        this.type = type;
        this.value = value;
        this.changeKey = changeKey;
        this.referenceKey = referenceKey;
    }

    /**
     * Sets the scope of the item affected by this operation. If not set, the
     * default scope will be {@link EntryScope#TOPIC}. Values with
     * {@link EntryScope#CONNECTION} scope will be automatically removed once
     * the connection to the topic which created them is deactivated.
     *
     * @param scope
     *            the scope, not <code>null</code>
     * @return this operation, not <code>null</code>
     */
    public ListOperation withScope(EntryScope scope) {
        this.scope = Objects.requireNonNull(scope);
        return this;
    }

    /**
     * Adds a condition that requires the specified <code>nextKey</code> to be
     * right after the specified <code>key</code> when the operation is applied.
     * A <code>null</code> <code>nextKey</code> can be used to represent the
     * tail of the list.
     *
     * @param key
     *            the reference key, not <code>null</code>
     * @param nextKey
     *            the required key, or <code>null</code> to represent the tail
     *            of the list
     * @return this operation, not <code>null</code>
     */
    public ListOperation ifNext(ListKey key, ListKey nextKey) {
        Objects.requireNonNull(key);
        if (conditions.containsKey(key)) {
            throw new IllegalStateException(
                    "A requirement for the value after this key is already set");
        }
        conditions.put(key, nextKey);
        return this;
    }

    /**
     * Adds a condition that requires the specified <code>key</code> to be the
     * last in the list.
     *
     * @param key
     *            the key, not <code>null</code>
     * @return this operation, not <code>null</code>
     */
    public ListOperation ifLast(ListKey key) {
        Objects.requireNonNull(key);
        return ifNext(key, null);
    }

    /**
     * Adds a condition that requires the specified <code>prevKey</code> to be
     * right before the specified <code>key</code> when the operation is
     * applied. A <code>null</code> <code>prevKey</code> can be used to
     * represent the head of the list.
     *
     * @param key
     *            the reference key, not <code>null</code>
     * @param prevKey
     *            the required key, or <code>null</code> to represent the head
     *            of the list
     * @return this operation, not <code>null</code>
     */
    public ListOperation ifPrev(ListKey key, ListKey prevKey) {
        Objects.requireNonNull(key);
        if (conditions.containsValue(key)) {
            throw new IllegalStateException(
                    "A requirement for the value before this key is already set");
        }
        conditions.put(prevKey, key);
        return this;
    }

    /**
     * Adds a condition that requires the specified <code>key</code> to be the
     * first in the list.
     *
     * @param key
     *            the key, not <code>null</code>
     * @return this operation, not <code>null</code>
     */
    public ListOperation ifFirst(ListKey key) {
        Objects.requireNonNull(key);
        return ifPrev(key, null);
    }

    /**
     * Adds a condition that requires the list to be empty.
     *
     * @return this operation, not <code>null</code>
     */
    public ListOperation ifEmpty() {
        if (Boolean.FALSE.equals(empty)) {
            throw new IllegalStateException(
                    "This operation already requires the list not to be empty.");
        }
        empty = true;
        return this;
    }

    /**
     * Adds a condition that requires the list not to be empty.
     *
     * @return this operation, not <code>null</code>
     */
    public ListOperation ifNotEmpty() {
        if (Boolean.TRUE.equals(empty)) {
            throw new IllegalStateException(
                    "This operation already requires the list to be empty.");
        }
        empty = false;
        return this;
    }

    /**
     * Add a condition that requires the specified <code>key</code> to have the
     * specified <code>value</code>.
     *
     * @param key
     *            the key, not <code>null</code>
     * @param value
     *            the expected value
     * @return this operation, not <code>null</code>
     */
    public ListOperation ifValue(ListKey key, Object value) {
        Objects.requireNonNull(key);
        if (valueConditions.containsKey(key)) {
            throw new IllegalStateException(
                    "A requirement for the value of this key is already set");
        }
        valueConditions.put(key, value);
        return this;
    }

    Object getValue() {
        return value;
    }

    OperationType getType() {
        return type;
    }

    ListKey getReferenceKey() {
        return referenceKey;
    }

    ListKey getChangeKey() {
        return changeKey;
    }

    EntryScope getScope() {
        return scope;
    }

    Map<ListKey, ListKey> getConditions() {
        return Collections.unmodifiableMap(conditions);
    }

    Boolean getEmpty() {
        return empty;
    }

    Map<ListKey, Object> getValueConditions() {
        return Collections.unmodifiableMap(valueConditions);
    }

}
