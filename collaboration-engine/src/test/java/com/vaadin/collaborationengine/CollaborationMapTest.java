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
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vaadin.collaborationengine.util.MockConnectionContext;
import com.vaadin.collaborationengine.util.MockConnectionContext.MockActionDispatcher;
import com.vaadin.flow.shared.Registration;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;

public class CollaborationMapTest {

    private static class MapSubscriberSpy implements MapSubscriber {
        private final Map<String, String> expectedOld = new HashMap<>();
        private final Map<String, String> expectedNew = new HashMap<>();

        public void addExpectedEvent(String key, String oldValue,
                String newValue) {
            Assert.assertFalse("Should not expect same value again",
                    expectedOld.containsKey(key));

            expectedOld.put(key, oldValue);
            expectedNew.put(key, newValue);
        }

        @Override
        public void onMapChange(MapChangeEvent event) {
            String key = event.getKey();
            Assert.assertTrue("Event should be expected for key " + key,
                    expectedOld.containsKey(key));
            Assert.assertEquals(expectedOld.remove(key),
                    event.getOldValue(String.class));
            Assert.assertEquals(expectedNew.remove(key),
                    event.getValue(String.class));
        }

        public void assertNoExpectedEvents() {
            Assert.assertEquals("There are expected events",
                    Collections.emptySet(), expectedOld.keySet());
        }

    }

    private MockConnectionContext context;
    private TestUtil.TestCollaborationEngine ce;
    private TopicConnection connection;
    private CollaborationMap map;
    private MapSubscriberSpy spy;
    private TopicConnectionRegistration registration;
    private MockActionDispatcher dispatcher;

    @Before
    public void init() {
        context = MockConnectionContext.createEager();
        dispatcher = (MockActionDispatcher) context.getActionDispatcher();
        ce = TestUtil.createTestCollaborationEngine();
        registration = ce.openTopicConnection(context, "topic",
                SystemUserInfo.getInstance(), topicConnection -> {
                    connection = topicConnection;
                    map = connection.getNamedMap("foo");
                    return null;
                });
        spy = new MapSubscriberSpy();
    }

    @Test
    public void mapWithValues_subscribe_eventsForValues() {
        map.put("one", "first");
        map.put("two", "second");

        spy.addExpectedEvent("one", null, "first");
        spy.addExpectedEvent("two", null, "second");

        map.subscribe(spy);

        spy.assertNoExpectedEvents();
    }

    @Test
    public void mapWithPreviousValues_subscribe_noEvents() {
        map.put("one", "first");
        map.put("one", null);

        map.subscribe(spy);
    }

    @Test
    public void mapWithSubscriber_setNewValue_valueChangedAndEventFired() {
        map.subscribe(spy);

        spy.addExpectedEvent("one", null, "first");
        map.put("one", "first");

        spy.assertNoExpectedEvents();

        Assert.assertEquals("first", map.get("one", String.class));
    }

    @Test
    public void mapWithSubscriber_setSameValue_noEventFired() {
        map.put("one", "first");
        spy.addExpectedEvent("one", null, "first");
        map.subscribe(spy);
        spy.assertNoExpectedEvents();

        map.put("one", "first");
        // Spy would throw if an event is fired
    }

    @Test
    public void mapWithSubscriber_replace_valueChangedAndEventFired()
            throws ExecutionException, InterruptedException {
        map.subscribe(spy);

        spy.addExpectedEvent("one", null, "first");
        Boolean succeeded = map.replace("one", null, "first").get();
        Assert.assertTrue("Replace should be successful", succeeded);

        spy.assertNoExpectedEvents();
        Assert.assertEquals("first", map.get("one", String.class));
    }

    @Test
    public void mapWithSubscriber_replaceWrongExpectedValue_noChange()
            throws ExecutionException, InterruptedException {
        map.subscribe(spy);

        Boolean succeeded = map.replace("one", "other", "first").get();
        Assert.assertFalse("Replace should not succeed", succeeded);
        Assert.assertNull("Old value should be present",
                map.get("one", String.class));
    }

    @Test
    public void mapWithSubscriber_replaceCurrentValue_succeedNoEvent()
            throws ExecutionException, InterruptedException {
        map.subscribe(spy);

        Boolean succeeded = map.replace("one", null, null).get();
        Assert.assertTrue("Replace should succeed", succeeded);
        // spy would have throw if an event was fired
    }

    @Test
    public void mapWithRemovedValues_getKeys_containsOnlyPresentValues() {
        map.put("one", "first");
        map.put("two", "second");
        map.put("one", null);

        List<String> keys = map.getKeys().collect(Collectors.toList());
        Assert.assertEquals(Arrays.asList("two"), keys);
    }

    @Test
    public void oldKeysSnapshot_newValueAdded_snapshotUnchanged() {
        map.put("one", "first");
        Stream<String> keysStream = map.getKeys();
        map.put("two", "second");

        List<String> keys = keysStream.collect(Collectors.toList());
        Assert.assertEquals(Arrays.asList("one"), keys);
    }

    @Test
    public void mapWithRemovedSubscriber_valueChange_subscriberNotNotified() {
        Registration registration = map.subscribe(spy);

        registration.remove();

        map.put("one", "first");
        // Spy would throw if there was an event
    }

    @Test
    public void mapWithSubscriber_addAnotherSubscriber_noEventForOldSubscriber() {
        map.put("one", "first");

        spy.addExpectedEvent("one", null, "first");
        map.subscribe(spy);
        spy.assertNoExpectedEvents();

        map.subscribe(ignore -> {

        });
        // Spy would throw if there was an event
    }

    @Test
    public void mapWithExistingValue_updateValue_eventHasOldValue() {
        map.put("one", "first");
        spy.addExpectedEvent("one", null, "first");
        map.subscribe(spy);

        spy.addExpectedEvent("one", "first", "updated");
        map.put("one", "updated");

        spy.assertNoExpectedEvents();
    }

    @Test
    public void mapWithExistingValue_setValueToNull_eventNewValueIsNull() {
        map.put("one", "first");
        spy.addExpectedEvent("one", null, "first");
        map.subscribe(spy);

        spy.addExpectedEvent("one", "first", null);
        map.put("one", null);

        spy.assertNoExpectedEvents();
    }

    @Test
    public void mapWithExistingValue_removeValue_eventNewValueIsNull() {
        map.put("one", "first");
        spy.addExpectedEvent("one", null, "first");
        map.subscribe(spy);

        spy.addExpectedEvent("one", "first", null);
        map.remove("one");

        spy.assertNoExpectedEvents();
    }

    @Test
    public void mapWithExistingValue_removeExpectedValue_eventNewValueIsNull() {
        map.put("one", "first");
        spy.addExpectedEvent("one", null, "first");
        map.subscribe(spy);

        spy.addExpectedEvent("one", "first", null);
        map.remove("one", "first");

        spy.assertNoExpectedEvents();
    }

    @Test
    public void put_contextCannotDispatch_unresolved() {
        context.init(ignore -> {
        }, command -> {
        });
        CompletableFuture<Void> put = map.put("one", "first");
        Assert.assertFalse(put.isDone());
    }

    @Test
    public void replace_contextCannotDispatch_unresolved() {
        map.put("one", "first");
        context.init(ignore -> {
        }, command -> {
        });
        CompletableFuture<Boolean> replace = map.replace("one", "first",
                "second");
        Assert.assertFalse(replace.isDone());
    }

    @Test
    public void replace_contextCanDispatch_resolved()
            throws InterruptedException, ExecutionException {
        map.put("one", "first");
        Boolean isReplaced = map.replace("one", "first", "second").get();
        Assert.assertTrue(isReplaced);
    }

    @Test
    public void replace_mapValueImmutable() {
        List<String> data = Arrays.asList("foo", "bar");
        ArrayNode jsonNode = (ArrayNode) JsonUtil.toJsonNode(data);
        map.replace("key", null, jsonNode);

        data.set(0, "qux");
        jsonNode.add("lorem");

        List<String> mapData = map.get("key", MockJson.LIST_STRING_TYPE_REF);
        Assert.assertEquals(
                "Map data shouldn't be changed by the original instances.",
                mapData, Arrays.asList("foo", "bar"));
    }

    @Test
    public void get_mapValueImmutable() {
        List<String> data = Arrays.asList("foo", "bar");
        map.put("key", data);

        ArrayNode mapDataNode = map.get("key", ArrayNode.class);
        mapDataNode.set(0, JsonNodeFactory.instance.textNode("baz"));

        Assert.assertTrue(
                "The returned value shouldn't affect map data in the topic.",
                data.containsAll(
                        map.get("key", MockJson.LIST_STRING_TYPE_REF)));
    }

    @Test
    public void expirationTimeout_mapClearedAfterTimeout() {
        Duration timeout = Duration.ofMinutes(15);
        map.setExpirationTimeout(timeout);
        map.put("foo", "foo");
        registration.remove();
        ce.setClock(Clock.offset(ce.getClock(), timeout.plusMinutes(1)));
        AtomicReference<CollaborationMap> newMap = new AtomicReference<>();
        ce.openTopicConnection(context, "topic", SystemUserInfo.getInstance(),
                connection -> {
                    newMap.set(connection.getNamedMap("foo"));
                    return null;
                });
        Assert.assertEquals(0, newMap.get().getKeys().count());
    }

    @Test
    public void expirationTimeout_mapNotClearedBeforeTimeout() {
        Duration timeout = Duration.ofMinutes(15);
        map.setExpirationTimeout(timeout);
        map.put("foo", "foo");
        registration.remove();
        ce.setClock(Clock.offset(ce.getClock(), timeout.minusMinutes(1)));
        AtomicReference<CollaborationMap> newMap = new AtomicReference<>();
        ce.openTopicConnection(context, "topic", SystemUserInfo.getInstance(),
                connection -> {
                    newMap.set(connection.getNamedMap("foo"));
                    return null;
                });
        String foo = newMap.get().get("foo", String.class);
        Assert.assertEquals("foo", foo);
    }

    @Test
    public void expirationTimeout_timeoutRemovedWhenSetToNull() {
        Duration timeout = Duration.ofMinutes(15);
        map.setExpirationTimeout(timeout);
        map.put("foo", "foo");
        ce.setClock(Clock.offset(ce.getClock(), timeout.plusMinutes(1)));
        map.setExpirationTimeout(null);
        registration.remove();
        AtomicReference<CollaborationMap> newMap = new AtomicReference<>();
        ce.openTopicConnection(context, "topic", SystemUserInfo.getInstance(),
                connection -> {
                    newMap.set(connection.getNamedMap("foo"));
                    return null;
                });
        String foo = newMap.get().get("foo", String.class);
        Assert.assertEquals("foo", foo);
    }

    @Test
    public void expirationTimeout_connectionOpened_mapClearCancelled() {
        Duration timeout = Duration.ofMinutes(15);
        map.setExpirationTimeout(timeout);
        map.put("foo", "foo");
        registration.remove();
        AtomicReference<CollaborationMap> newMap = new AtomicReference<>();
        ce.openTopicConnection(context, "topic", SystemUserInfo.getInstance(),
                connection -> {
                    newMap.set(connection.getNamedMap("foo"));
                    return null;
                });
        ce.setClock(Clock.offset(ce.getClock(), timeout.plusMinutes(1)));
        String foo = newMap.get().get("foo", String.class);
        Assert.assertEquals("foo", foo);
    }

    @Test
    public void expirationTimeout_multipleConnectionsOpened_closeOne_mapClearNotScheduled() {
        Duration timeout = Duration.ofMinutes(15);
        map.setExpirationTimeout(timeout);
        map.put("foo", "foo");
        ce.openTopicConnection(context, "topic", SystemUserInfo.getInstance(),
                connection -> null).remove();
        ce.setClock(Clock.offset(ce.getClock(), timeout.plusMinutes(1)));
        String foo = map.get("foo", String.class);
        Assert.assertEquals("foo", foo);
    }

    @Test(expected = IllegalStateException.class)
    public void subscribe_throwsIfInactiveConnection() {
        registration.remove();
        map.subscribe(event -> {
        });
    }

    @Test(expected = IllegalStateException.class)
    public void replace_throwsIfInactiveConnection() {
        registration.remove();
        map.replace("foo", "foo", "bar");
    }

    @Test(expected = IllegalStateException.class)
    public void getKeys_throwsIfInactiveConnection() {
        registration.remove();
        map.getKeys();
    }

    @Test(expected = IllegalStateException.class)
    public void get_throwsIfInactiveConnection() {
        registration.remove();
        map.get("foo", String.class);
    }

    @Test(expected = IllegalStateException.class)
    public void put_throwsIfInactiveConnection() {
        registration.remove();
        map.put("foo", "foo");
    }

    @Test
    public void put_useThreadPool() {
        ce.setAsynchronous(true);
        Thread current = Thread.currentThread();
        map.subscribe(event -> {
            Assert.assertNotSame(current, Thread.currentThread());
        });
        map.put("foo", "foo");

    }

    @Test
    public void putWithConnectionScope_connectionRemainsActive_entryRetained() {
        map.put("foo", "foo", EntryScope.CONNECTION);
        String foo = map.get("foo", String.class);
        Assert.assertEquals("foo", foo);
    }

    @Test
    public void reactivatedConnection_putWithConnectionScope_entryRetained() {
        context.deactivate();
        context.activate();
        map.put("foo", "foo", EntryScope.CONNECTION);
        String foo = map.get("foo", String.class);
        Assert.assertEquals("foo", foo);
    }

    @Test
    public void putWithConnectionScope_connectionDeactivated_entryRemoved() {
        map.put("foo", "foo", EntryScope.CONNECTION);
        context.deactivate();
        context.activate();
        String foo = map.get("foo", String.class);
        Assert.assertNull(foo);
    }

    @Test
    public void putWithTopicScope_putSameValueWithConnectionScope_connectionDeactivated_entryRemoved() {
        map.put("foo", "foo");
        map.put("foo", "foo", EntryScope.CONNECTION);
        context.deactivate();
        context.activate();
        String foo = map.get("foo", String.class);
        Assert.assertNull(foo);
    }

    @Test
    public void putWithConnectionScope_putWithTopicScope_connectionDeactivated_entryNotRemoved() {
        map.put("foo", "foo", EntryScope.CONNECTION);
        map.put("foo", "bar");
        context.deactivate();
        context.activate();
        String foo = map.get("foo", String.class);
        Assert.assertEquals("bar", foo);
    }

    @Test
    public void putWithConnectionScope_replace_connectionDeactivated_entryRemoved() {
        map.put("foo", "foo", EntryScope.CONNECTION);
        map.replace("foo", "foo", "bar");
        context.deactivate();
        context.activate();
        String foo = map.get("foo", String.class);
        Assert.assertNull(foo);
    }

    @Test
    public void expirationTimeout_unpopulatedMap_mapClearWithoutExplosions() {
        Duration timeout = Duration.ofMinutes(15);
        map.setExpirationTimeout(timeout);
        registration.remove();
        ce.setClock(Clock.offset(ce.getClock(), timeout.plusMinutes(1)));

        ce.openTopicConnection(context, "topic", SystemUserInfo.getInstance(),
                connection -> null);
        // All is fine if openTopicConnection runs successfully
    }

    @Test
    public void putAndSubscribe_thenDispatch_subscriberInvokedOnce() {
        dispatcher.hold();
        map.put("foo", "foo");
        MapSubscriberSpy spy = new MapSubscriberSpy();
        spy.addExpectedEvent("foo", null, "foo");
        map.subscribe(spy);
        dispatcher.release();
        spy.assertNoExpectedEvents();
    }
}
