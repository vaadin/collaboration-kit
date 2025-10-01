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
package com.vaadin.collaborationengine.util;

import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.Assert;

import com.vaadin.collaborationengine.CollaborationEngine;
import com.vaadin.collaborationengine.CollaborationMap;
import com.vaadin.collaborationengine.TopicConnection;
import com.vaadin.collaborationengine.UserInfo;

import tools.jackson.databind.node.NullNode;

public class TestUtils {

    public static boolean isGarbageCollected(WeakReference<?> ref)
            throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            System.gc();
            if (ref.get() == null) {
                return true;
            }
        }
        return false;
    }

    public static void clearMap(String topicId, String mapName,
            String... keys) {
        openEagerConnection(topicId, topicConnection -> {
            CollaborationMap map = topicConnection.getNamedMap(mapName);
            for (String key : keys) {
                map.put(key, null);
            }
        });
    }

    public static void openEagerConnection(String topicId,
            Consumer<TopicConnection> handler) {
        openEagerConnection(CollaborationEngine.getInstance(), topicId,
                handler);
    }

    public static void openEagerConnection(CollaborationEngine ce,
            String topicId, Consumer<TopicConnection> handler) {
        ce.openTopicConnection(MockConnectionContext.createEager(), topicId,
                new UserInfo(UUID.randomUUID().toString()), topic -> {
                    handler.accept(topic);
                    return null;
                });
    }

    public static void assertNullNode(String message, Object value) {
        Assert.assertEquals(message, NullNode.class, value.getClass());
    }

    @SafeVarargs
    public static <E> Set<E> newHashSet(E... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    public static <T> T serialize(T object) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new ObjectOutputStream(out).writeObject(object);

            ByteArrayInputStream in = new ByteArrayInputStream(
                    out.toByteArray());
            return (T) new ObjectInputStream(in).readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            fail();
        }
        return null;
    }
}
