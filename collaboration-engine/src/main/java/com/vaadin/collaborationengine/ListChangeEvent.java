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

import java.util.EventObject;

import tools.jackson.core.type.TypeReference;

/**
 * Event that is fired when the value in a collaboration list changes.
 *
 * @author Vaadin Ltd
 * @since 3.1
 */
public class ListChangeEvent extends EventObject {

    private final ListChange change;

    /**
     * Creates a new event.
     *
     * @param list
     *            the list source of the event
     * @param change
     *            the list change
     */
    ListChangeEvent(CollaborationList list, ListChange change) {
        super(list);
        this.change = change;
    }

    @Override
    public CollaborationList getSource() {
        return (CollaborationList) super.getSource();
    }

    ListChangeType getType() {
        return change.getType();
    }

    /**
     * Gets the key of the list item affected by the change.
     *
     * @return the key of the changed item, not <code>null</code>
     */
    public ListKey getKey() {
        return new ListKey(change.getKey());
    }

    /**
     * Gets the current value of the list item affected by the change as
     * instance of the given class.
     * <p>
     * If the item was removed by the change, this method returns
     * <code>null</code> and {@link #getOldValue(Class)} return the removed item
     * value.
     *
     * @param <T>
     *            the type of the value from <code>type</code> parameter, e.g.
     *            <code>String</code>
     * @param type
     *            the expected type of the returned instance
     * @return the current value of the item affected by the change
     */
    public <T> T getValue(Class<T> type) {
        return JsonUtil.toInstance(change.getValue(), type);
    }

    /**
     * Gets the current value of the list item affected by the change as
     * instance corresponding to the given type reference.
     * <p>
     * If the item was removed by the change, this method returns
     * <code>null</code> and {@link #getOldValue(TypeReference)} return the
     * removed item value.
     *
     * @param <T>
     *            the type reference of the value from <code>type</code>
     *            parameter, e.g. <code>List<String></code>
     * @param type
     *            the expected type reference of the returned instance
     * @return the current value of the item affected by the change
     */
    public <T> T getValue(TypeReference<T> type) {
        return JsonUtil.toInstance(change.getValue(), type);
    }

    /**
     * Gets the old value of the list item affected by the change as instance of
     * the given class.
     *
     * @param <T>
     *            the type of the value from <code>type</code> parameter, e.g.
     *            <code>String</code>
     * @param type
     *            the expected type of the returned instance
     * @return the old value of the item affected by the change
     */
    public <T> T getOldValue(Class<T> type) {
        return JsonUtil.toInstance(change.getOldValue(), type);
    }

    /**
     * Gets the old value of the list item affected by the change as instance
     * corresponding to the given type reference.
     *
     * @param <T>
     *            the type reference of the value from <code>type</code>
     *            parameter, e.g. <code>List<String></code>
     * @param type
     *            the expected type reference of the returned instance
     * @return the old value of the item affected by the change
     */
    public <T> T getOldValue(TypeReference<T> type) {
        return JsonUtil.toInstance(change.getOldValue(), type);
    }

    /**
     * Gets the key of the item which is after the current item after the
     * change.
     *
     * @return the key of the item which is after the current item, or
     *         <code>null</code> if there is none
     */
    public ListKey getNext() {
        return ListKey.of(change.getNext());
    }

    /**
     * Gets the key of the item which was after the current item before the
     * change.
     *
     * @return the key of the item which was after the current item, or
     *         <code>null</code> if there was none
     */
    public ListKey getOldNext() {
        return ListKey.of(change.getOldNext());
    }

    /**
     * Gets the key of the item which is before the current item after the
     * change.
     *
     * @return the key of the item which is before the current item, or
     *         <code>null</code> if there is none
     */
    public ListKey getPrev() {
        return ListKey.of(change.getPrev());
    }

    /**
     * Gets the key of the item which was before the current item before the
     * change.
     *
     * @return the key of the item which was before the current item, or
     *         <code>null</code> if there was none
     */
    public ListKey getOldPrev() {
        return ListKey.of(change.getOldPrev());
    }
}
