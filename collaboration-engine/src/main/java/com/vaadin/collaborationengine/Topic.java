/*
 * Copyright (C) 2020 Vaadin Ltd
 *
 * This program is available under Commercial Vaadin Add-On License 3.0
 * (CVALv3).
 *
 * See the file licensing.txt distributed with this software for more
 * information about licensing.
 *
 * You should have received a copy of the license along with this program.
 * If not, see <http://vaadin.com/license/cval-3>.
 */
package com.vaadin.collaborationengine;

import java.util.ArrayList;
import java.util.List;

class Topic {

    private final String id;

    private final Object lock = new Object();

    private Object value;
    private final List<SingleValueSubscriber> subscribers = new ArrayList<>();

    Topic(String id) {
        this.id = id;
    }

    void subscribe(SingleValueSubscriber subscriber) {
        synchronized (lock) {
            subscribers.add(subscriber);
            subscriber.onValueChange(value);
        }
    }

    void setValue(Object value) {
        synchronized (lock) {
            this.value = value;
            for (SingleValueSubscriber subscriber : new ArrayList<>(
                    subscribers)) {
                subscriber.onValueChange(value);
            }
        }
    }

    Object getValue() {
        return value;
    }
}
