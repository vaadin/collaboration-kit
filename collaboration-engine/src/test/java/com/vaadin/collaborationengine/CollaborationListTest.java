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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
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

import tools.jackson.core.type.TypeReference;

public class CollaborationListTest {

    private static final TypeReference<String> STRING_REF = new TypeReference<>() {
    };

    private static class ListAppendSpy implements ListSubscriber {

        private final LinkedList<String> expectedItems = new LinkedList<>();

        @Override
        public void onListChange(ListChangeEvent event) {
            String item = event.getValue(String.class);
            String expected = null;
            try {
                expected = expectedItems.removeFirst();
            } catch (NoSuchElementException e) {
                Assert.fail("No expected items");
            }
            Assert.assertEquals(expected, item);
        }

        private void addExpectedEvent(String item) {
            expectedItems.add(item);
        }

        private void assertNoExpectedEvents() {
            Assert.assertTrue(expectedItems.isEmpty());
        }
    }

    private static class EventCollector extends ArrayList<ListChangeEvent>
            implements ListSubscriber {
        @Override
        public void onListChange(ListChangeEvent event) {
            add(event);
        }
    }

    private MockConnectionContext context;
    private CollaborationEngine ce;
    private TopicConnection connection;
    private CollaborationList list;
    private ListAppendSpy appendSpy;
    private EventCollector eventCollector;
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
                    list = connection.getNamedList("foo");
                    return null;
                });
        appendSpy = new ListAppendSpy();
        eventCollector = new EventCollector();
    }

    @Test
    public void listWithItem_getItemsByClass() {
        list.insertLast("foo");
        Assert.assertEquals("foo", list.getItems(String.class).get(0));
    }

    @Test
    public void listWithItem_getItemsByTypeReference() {
        list.insertLast("foo");
        Assert.assertEquals("foo", list.getItems(STRING_REF).get(0));
    }

    @Test
    public void listChangeEvent_getAddedItemByClass()
            throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = new CompletableFuture<>();
        list.subscribe(event -> {
            String item = event.getValue(String.class);
            future.complete(item);
        });
        list.insertLast("foo");
        Assert.assertEquals("foo", future.get());
    }

    @Test
    public void listChangeEvent_getAddedItemByTypeReference()
            throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = new CompletableFuture<>();
        list.subscribe(event -> {
            String item = event.getValue(STRING_REF);
            future.complete(item);
        });
        list.insertLast("foo");
        Assert.assertEquals("foo", future.get());
    }

    @Test
    public void listWithItems_subscribe_eventsForItems() {
        list.insertLast("foo");
        list.insertLast("bar");

        appendSpy.addExpectedEvent("foo");
        appendSpy.addExpectedEvent("bar");

        list.subscribe(appendSpy);

        appendSpy.assertNoExpectedEvents();
    }

    @Test
    public void listWithRemovedSubscriber_appendItem_subscriberNotNotified() {
        list.subscribe(appendSpy).remove();

        list.insertLast("foo"); // Spy would throw if notified of unexpected
                                // "foo"
    }

    @Test
    public void listWithSubscriber_addAnotherSubscriber_noEventForOldSubscriber() {
        list.insertLast("foo");

        appendSpy.addExpectedEvent("foo");
        list.subscribe(appendSpy);
        appendSpy.assertNoExpectedEvents();

        list.subscribe(event -> {
        }); // Spy would throw if notified again
    }

    @Test
    public void listWithSubscriber_actionIsDispatchedInContext() {
        list.subscribe(ignore -> {
        });
        context.resetActionDispatchCount();
        list.insertLast("foo");
        /*-
         * 1 - activation
         * 2 - actual insert
         * 3 - completing the future
         * 4 - event dispatching
        -*/
        Assert.assertEquals(4, context.getDispatchActionCount());
    }

    @Test
    public void listWithMultipleSubscriber_actionsAreDispatchedInOwnContext() {
        MockConnectionContext ctx1 = MockConnectionContext.createEager();
        MockConnectionContext ctx2 = MockConnectionContext.createEager();
        AtomicReference<CollaborationList> list1 = new AtomicReference<>();
        AtomicReference<CollaborationList> list2 = new AtomicReference<>();
        ce.openTopicConnection(ctx1, "topic", SystemUserInfo.getInstance(),
                topicConnection -> {
                    list1.set(topicConnection.getNamedList("foo"));
                    return null;
                });
        ce.openTopicConnection(ctx2, "topic", SystemUserInfo.getInstance(),
                topicConnection -> {
                    list2.set(topicConnection.getNamedList("foo"));
                    return null;
                });
        list1.get().subscribe(ignore -> {
        });
        list2.get().subscribe(ignore -> {
        });
        ctx1.resetActionDispatchCount();
        ctx2.resetActionDispatchCount();
        list1.get().insertLast("foo");

        /*-
         * 1 - activation
         * 2 - actual insert
         * 3 - completing the future
         * 4 - event dispatching
        -*/
        Assert.assertEquals(4, ctx1.getDispatchActionCount());
        /*-
         * 1 - activation
         * 2 - event dispatching
        -*/
        Assert.assertEquals(2, ctx2.getDispatchActionCount());
    }

    @Test
    public void multipleListsWithSubscribers_onlyOwnSubscriberIsNotified() {
        MockConnectionContext ctx1 = MockConnectionContext.createEager();
        MockConnectionContext ctx2 = MockConnectionContext.createEager();
        AtomicReference<CollaborationList> list1 = new AtomicReference<>();
        AtomicReference<CollaborationList> list2 = new AtomicReference<>();
        ce.openTopicConnection(ctx1, "topic", SystemUserInfo.getInstance(),
                topicConnection -> {
                    list1.set(topicConnection.getNamedList("foo"));
                    return null;
                });
        ce.openTopicConnection(ctx2, "topic", SystemUserInfo.getInstance(),
                topicConnection -> {
                    list2.set(topicConnection.getNamedList("bar"));
                    return null;
                });

        ListAppendSpy spy1 = new ListAppendSpy();
        ListAppendSpy spy2 = new ListAppendSpy();

        list1.get().subscribe(spy1);
        list2.get().subscribe(spy2);

        spy1.addExpectedEvent("foo");
        spy2.addExpectedEvent("bar");

        list1.get().insertLast("foo");
        list2.get().insertLast("bar");

        spy1.assertNoExpectedEvents();
        spy2.assertNoExpectedEvents();
    }

    @Test
    public void append_contextCanDispatch_resolved() {
        CompletableFuture<Void> append = list.insertLast("foo")
                .getCompletableFuture();
        Assert.assertTrue(append.isDone());
    }

    @Test
    public void append_contextCannotDispatch_unresolved() {
        context.init(ignore -> {
        }, ignore -> {
        });
        CompletableFuture<Void> append = list.insertLast("foo")
                .getCompletableFuture();
        Assert.assertFalse(append.isDone());
    }

    @Test(expected = IllegalStateException.class)
    public void subscribe_throwsIfInactiveConnection() {
        registration.remove();
        list.subscribe(event -> {
        });
    }

    @Test(expected = IllegalStateException.class)
    public void getItems_throwsIfInactiveConnection() {
        registration.remove();
        list.getItems(String.class);
    }

    @Test(expected = IllegalStateException.class)
    public void append_throwsIfInactiveConnection() {
        registration.remove();
        list.insertLast("foo");
    }

    @Test
    public void expirationTimeout_listClearedAfterTimeout() {
        Duration timeout = Duration.ofMinutes(15);
        list.setExpirationTimeout(timeout);
        list.insertLast("foo");
        registration.remove();
        ce.setClock(Clock.offset(ce.getClock(), timeout.plusMinutes(1)));
        AtomicReference<CollaborationList> newList = new AtomicReference<>();
        ce.openTopicConnection(context, "topic", SystemUserInfo.getInstance(),
                connection -> {
                    newList.set(connection.getNamedList("foo"));
                    return null;
                });
        int listSize = newList.get().getItems(String.class).size();
        Assert.assertEquals(0, listSize);
    }

    @Test
    public void expirationTimeout_listNotClearedBeforeTimeout() {
        Duration timeout = Duration.ofMinutes(15);
        list.setExpirationTimeout(timeout);
        list.insertLast("foo");
        registration.remove();
        ce.setClock(Clock.offset(ce.getClock(), timeout.minusMinutes(1)));
        AtomicReference<CollaborationList> newList = new AtomicReference<>();
        ce.openTopicConnection(context, "topic", SystemUserInfo.getInstance(),
                connection -> {
                    newList.set(connection.getNamedList("foo"));
                    return null;
                });
        String foo = newList.get().getItems(String.class).get(0);
        Assert.assertEquals("foo", foo);
    }

    @Test
    public void expirationTimeout_timeoutRemovedWhenSetToNull() {
        Duration timeout = Duration.ofMinutes(15);
        list.setExpirationTimeout(timeout);
        list.insertLast("foo");
        ce.setClock(Clock.offset(ce.getClock(), timeout.plusMinutes(1)));
        list.setExpirationTimeout(null);
        registration.remove();
        AtomicReference<CollaborationList> newList = new AtomicReference<>();
        ce.openTopicConnection(context, "topic", SystemUserInfo.getInstance(),
                connection -> {
                    newList.set(connection.getNamedList("foo"));
                    return null;
                });
        String foo = newList.get().getItems(String.class).get(0);
        Assert.assertEquals("foo", foo);
    }

    @Test
    public void expirationTimeout_connectionOpened_listClearCancelled() {
        Duration timeout = Duration.ofMinutes(15);
        list.setExpirationTimeout(timeout);
        list.insertLast("foo");
        registration.remove();
        AtomicReference<CollaborationList> newList = new AtomicReference<>();
        ce.openTopicConnection(context, "topic", SystemUserInfo.getInstance(),
                connection -> {
                    newList.set(connection.getNamedList("foo"));
                    return null;
                });
        ce.setClock(Clock.offset(ce.getClock(), timeout.plusMinutes(1)));
        String foo = newList.get().getItems(String.class).get(0);
        Assert.assertEquals("foo", foo);
    }

    @Test
    public void expirationTimeout_multipleConnectionsOpened_closeOne_listClearNotScheduled() {
        Duration timeout = Duration.ofMinutes(15);
        list.setExpirationTimeout(timeout);
        list.insertLast("foo");
        ce.openTopicConnection(context, "topic", SystemUserInfo.getInstance(),
                connection -> null).remove();
        ce.setClock(Clock.offset(ce.getClock(), timeout.plusMinutes(1)));
        String foo = list.getItems(String.class).get(0);
        Assert.assertEquals("foo", foo);
    }

    @Test
    public void threeItems_setFirstToNull_twoRemaining() {
        List<ListKey> keys = insertLast(list, "one", "two", "three");

        list.set(keys.get(0), null);

        assertValues(list, "two", "three");
    }

    @Test
    public void threeItems_setMiddleToNull_twoRemaining() {
        List<ListKey> keys = insertLast(list, "one", "two", "three");

        list.set(keys.get(1), null);

        assertValues(list, "one", "three");
    }

    @Test
    public void threeItems_setLastToNull_twoRemaining() {
        List<ListKey> keys = insertLast(list, "one", "two", "three");

        list.set(keys.get(2), null);

        assertValues(list, "one", "two");
    }

    @Test
    public void oneItem_setItToNull_listEmpty() {
        ListKey key = list.insertLast("one").getKey();

        list.set(key, null);

        assertValues(list);
    }

    @Test
    public void threeItems_removeFirst_twoRemaining() {
        List<ListKey> keys = insertLast(list, "one", "two", "three");

        list.remove(keys.get(0));

        assertValues(list, "two", "three");
    }

    @Test
    public void threeItems_removeMiddle_twoRemaining() {
        List<ListKey> keys = insertLast(list, "one", "two", "three");

        list.remove(keys.get(1));

        assertValues(list, "one", "three");
    }

    @Test
    public void threeItems_removeLast_twoRemaining() {
        List<ListKey> keys = insertLast(list, "one", "two", "three");

        list.remove(keys.get(2));

        assertValues(list, "one", "two");
    }

    @Test
    public void oneItem_removeIt_listEmpty() {
        ListKey key = list.insertLast("one").getKey();

        list.remove(key);

        assertValues(list);
    }

    @Test
    public void listEmptied_addOne_nothingCorrupted() {
        ListKey key = list.insertLast("one").getKey();
        list.set(key, null);

        list.insertLast("another");

        assertValues(list, "another");
    }

    @Test
    public void listWithItem_setValue_valueSet()
            throws InterruptedException, ExecutionException {
        ListKey key = list.insertLast("one").getKey();

        Boolean result = list.set(key, "two").get();

        assertValues(list, "two");
        Assert.assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void emptyList_insertFirst_oneItem() {
        ListKey key = list.insertFirst("new").getKey();

        Assert.assertEquals("new", list.getItem(key, String.class));
        assertValues(list, "new");
    }

    @Test
    public void oneItem_insertFirst_twoItems() {
        insertLast(list, "one");

        ListKey key = list.insertFirst("new").getKey();

        Assert.assertEquals("new", list.getItem(key, String.class));
        assertValues(list, "new", "one");
    }

    @Test
    public void twoItems_insertBeforeFirst_threeItems() {
        List<ListKey> keys = insertLast(list, "one", "two");

        ListKey key = list.insertBefore(keys.get(0), "new").getKey();

        Assert.assertEquals("new", list.getItem(key, String.class));
        assertValues(list, "new", "one", "two");
    }

    @Test
    public void twoItems_insertBeforeLast_threeItems() {
        List<ListKey> keys = insertLast(list, "one", "two");

        ListKey key = list.insertBefore(keys.get(1), "new").getKey();

        Assert.assertEquals("new", list.getItem(key, String.class));
        assertValues(list, "one", "new", "two");
    }

    @Test
    public void twoItems_insertAfterFirst_threeItems() {
        List<ListKey> keys = insertLast(list, "one", "two");

        ListKey key = list.insertAfter(keys.get(0), "new").getKey();

        Assert.assertEquals("new", list.getItem(key, String.class));
        assertValues(list, "one", "new", "two");
    }

    @Test
    public void twoItems_insertAfterLast_threeItems() {
        List<ListKey> keys = insertLast(list, "one", "two");

        ListKey key = list.insertAfter(keys.get(1), "new").getKey();

        Assert.assertEquals("new", list.getItem(key, String.class));
        assertValues(list, "one", "two", "new");
    }

    @Test
    public void threeItems_moveLastBeforeFirst_threeItems() {
        List<ListKey> keys = insertLast(list, "one", "two", "three");

        list.moveBefore(keys.get(0), keys.get(2));

        assertValues(list, "three", "one", "two");
    }

    @Test
    public void threeItems_moveLastBeforeSecond_threeItems() {
        List<ListKey> keys = insertLast(list, "one", "two", "three");

        list.moveBefore(keys.get(1), keys.get(2));

        assertValues(list, "one", "three", "two");
    }

    @Test
    public void threeItems_moveFirstAfterSecond_threeItems() {
        List<ListKey> keys = insertLast(list, "one", "two", "three");

        list.moveAfter(keys.get(1), keys.get(0));

        assertValues(list, "two", "one", "three");
    }

    @Test
    public void threeItems_moveFirstAfterLast_threeItems() {
        List<ListKey> keys = insertLast(list, "one", "two", "three");

        list.moveAfter(keys.get(2), keys.get(0));

        assertValues(list, "two", "three", "one");
    }

    @Test
    public void listWithRemovedItem_setValue_negativeResult()
            throws InterruptedException, ExecutionException {
        ListKey key = list.insertLast("one").getKey();
        list.set(key, null);

        Boolean result = list.set(key, "two").get();

        Assert.assertEquals(Boolean.FALSE, result);
    }

    @Test
    public void emptyListWithSubscriber_appendItems_eventDetailsComplete() {
        list.subscribe(eventCollector);

        ListKey key1 = list.insertLast("one").getKey();
        ListKey key2 = list.insertLast("two").getKey();

        Assert.assertEquals(2, eventCollector.size());
        assertEvent(ListChangeType.INSERT, key1, null, "one", null, null, null,
                null, eventCollector.get(0));

        assertEvent(ListChangeType.INSERT, key2, null, "two", null, key1, null,
                null, eventCollector.get(1));
    }

    @Test
    public void populatedList_subscribe_eventDetailsComplete() {
        ListKey key1 = list.insertLast("one").getKey();
        ListKey key2 = list.insertLast("two").getKey();

        list.subscribe(eventCollector);

        Assert.assertEquals(2, eventCollector.size());
        assertEvent(ListChangeType.INSERT, key1, null, "one", null, null, null,
                null, eventCollector.get(0));

        assertEvent(ListChangeType.INSERT, key2, null, "two", null, key1, null,
                null, eventCollector.get(1));
    }

    @Test
    public void populatedListWithSubscriber_changeItems_eventDetailsComplete() {
        List<ListKey> keys = insertLast(list, "one", "two", "three");
        list.subscribe(eventCollector);
        eventCollector.clear();

        keys.forEach(key -> list.set(key,
                list.getItem(key, String.class).toUpperCase()));

        Assert.assertEquals(3, eventCollector.size());
        assertEvent(ListChangeType.SET, keys.get(0), "one", "ONE", null, null,
                keys.get(1), keys.get(1), eventCollector.get(0));
        assertEvent(ListChangeType.SET, keys.get(1), "two", "TWO", keys.get(0),
                keys.get(0), keys.get(2), keys.get(2), eventCollector.get(1));
        assertEvent(ListChangeType.SET, keys.get(2), "three", "THREE",
                keys.get(1), keys.get(1), null, null, eventCollector.get(2));
    }

    @Test
    public void populatedListWithSubscriber_removeMiddleItem_eventDetailsComplete() {
        List<ListKey> keys = insertLast(list, "one", "two", "three");
        list.subscribe(eventCollector);
        eventCollector.clear();

        list.set(keys.get(1), null);

        Assert.assertEquals(1, eventCollector.size());
        assertEvent(ListChangeType.SET, keys.get(1), "two", null, keys.get(0),
                null, keys.get(2), null, eventCollector.get(0));
    }

    @Test
    public void populatedListWithSubscriber_insertBeforeLastItem_eventDetailsComplete() {
        List<ListKey> keys = insertLast(list, "one", "two");
        list.subscribe(eventCollector);
        eventCollector.clear();

        ListKey key = list.insertBefore(keys.get(1), "new").getKey();

        Assert.assertEquals(1, eventCollector.size());
        assertEvent(ListChangeType.INSERT, key, null, "new", null, keys.get(0),
                null, keys.get(1), eventCollector.get(0));
    }

    @Test
    public void populatedListWithSubscriber_insertAfterFirstItem_eventDetailsComplete() {
        List<ListKey> keys = insertLast(list, "one", "two");
        list.subscribe(eventCollector);
        eventCollector.clear();

        ListKey key = list.insertAfter(keys.get(0), "new").getKey();

        Assert.assertEquals(1, eventCollector.size());
        assertEvent(ListChangeType.INSERT, key, null, "new", null, keys.get(0),
                null, keys.get(1), eventCollector.get(0));
    }

    @Test
    public void populatedListWithSubscriber_moveFirstBeforeLast_eventDetailsComplete() {
        List<ListKey> keys = insertLast(list, "one", "two", "three");
        list.subscribe(eventCollector);
        eventCollector.clear();

        list.moveBefore(keys.get(2), keys.get(0));

        Assert.assertEquals(1, eventCollector.size());
        assertEvent(ListChangeType.MOVE, keys.get(0), "one", "one", null,
                keys.get(1), keys.get(1), keys.get(2), eventCollector.get(0));
    }

    @Test
    public void populatedListWithSubscriber_moveLastAfterFirst_eventDetailsComplete() {
        List<ListKey> keys = insertLast(list, "one", "two", "three");
        list.subscribe(eventCollector);
        eventCollector.clear();

        list.moveAfter(keys.get(0), keys.get(2));

        Assert.assertEquals(1, eventCollector.size());
        assertEvent(ListChangeType.MOVE, keys.get(2), "three", "three",
                keys.get(1), keys.get(0), null, keys.get(1),
                eventCollector.get(0));
    }

    @Test
    public void insertFirstWithConnectionScope_connectionDeactivated_entryRemoved() {
        ListOperation operation = ListOperation.insertFirst("foo")
                .withScope(EntryScope.CONNECTION);
        ListKey key = list.apply(operation).getKey();
        context.deactivate();
        context.activate();
        Assert.assertNull(list.getItem(key, String.class));
        Assert.assertEquals(0, list.getKeys().count());
    }

    @Test
    public void insertFirstWithTopicScope_setWithConnectionScope_connectionDeactivated_entryRemoved() {
        ListKey key = list.insertFirst("foo").getKey();
        ListOperation operation = ListOperation.set(key, "foo")
                .withScope(EntryScope.CONNECTION);
        list.apply(operation);
        context.deactivate();
        context.activate();
        Assert.assertNull(list.getItem(key, String.class));
        Assert.assertEquals(0, list.getKeys().count());
    }

    @Test
    public void insertFirstWithConnectionScope_setWithTopicScope_connectionDeactivated_entryNotRemoved() {
        ListOperation operation = ListOperation.insertFirst("foo")
                .withScope(EntryScope.CONNECTION);
        ListKey key = list.apply(operation).getKey();
        list.set(key, "foo");
        context.deactivate();
        context.activate();
        Assert.assertEquals("foo", list.getItem(key, String.class));
    }

    @Test
    public void insertLastWithConnectionScope_connectionDeactivated_entryRemoved() {
        ListOperation operation = ListOperation.insertLast("foo")
                .withScope(EntryScope.CONNECTION);
        ListKey key = list.apply(operation).getKey();
        context.deactivate();
        context.activate();
        Assert.assertNull(list.getItem(key, String.class));
        Assert.assertEquals(0, list.getKeys().count());
    }

    @Test
    public void insertLastWithTopicScope_setWithConnectionScope_connectionDeactivated_entryRemoved() {
        ListKey key = list.insertLast("foo").getKey();
        ListOperation operation = ListOperation.set(key, "foo")
                .withScope(EntryScope.CONNECTION);
        list.apply(operation);
        context.deactivate();
        context.activate();
        Assert.assertNull(list.getItem(key, String.class));
        Assert.assertEquals(0, list.getKeys().count());
    }

    @Test
    public void insertLastWithConnectionScope_setWithTopicScope_connectionDeactivated_entryNotRemoved() {
        ListOperation operation = ListOperation.insertLast("foo")
                .withScope(EntryScope.CONNECTION);
        ListKey key = list.apply(operation).getKey();
        list.set(key, "foo");
        context.deactivate();
        context.activate();
        Assert.assertEquals("foo", list.getItem(key, String.class));
    }

    @Test
    public void insertBeforeWithConnectionScope_connectionDeactivate_entryRemoved() {
        List<ListKey> keys = insertLast(list, "one", "two");
        ListOperation operation = ListOperation.insertBefore(keys.get(0), "new")
                .withScope(EntryScope.CONNECTION);
        ListKey key = list.apply(operation).getKey();
        context.deactivate();
        context.activate();
        Assert.assertNull(list.getItem(key, String.class));
        Assert.assertEquals(2, list.getKeys().count());
    }

    @Test
    public void insertAfterWithConnectionScope_connectionDeactivate_entryRemoved() {
        List<ListKey> keys = insertLast(list, "one", "two");
        ListOperation operation = ListOperation.insertAfter(keys.get(0), "new")
                .withScope(EntryScope.CONNECTION);
        ListKey key = list.apply(operation).getKey();
        context.deactivate();
        context.activate();
        Assert.assertNull(list.getItem(key, String.class));
        Assert.assertEquals(2, list.getKeys().count());
    }

    @Test
    public void insertWithPrevCondition_conditionMet_operationApplied()
            throws InterruptedException, ExecutionException {
        ListKey fooKey = list.insertLast("foo").getKey();
        ListOperation conditionalOperation = ListOperation.insertLast("bar")
                .ifFirst(fooKey);
        boolean succeeded = list.apply(conditionalOperation)
                .getCompletableFuture().get();
        Assert.assertTrue(succeeded);
    }

    @Test
    public void insertWithPrevCondition_conditionNotMet_operationRejected()
            throws InterruptedException, ExecutionException {
        ListKey fooKey = list.insertLast("foo").getKey();
        list.insertFirst("notFoo");
        ListOperation conditionalOperation = ListOperation.insertLast("bar")
                .ifFirst(fooKey);
        boolean succeeded = list.apply(conditionalOperation)
                .getCompletableFuture().get();
        Assert.assertFalse(succeeded);
    }

    @Test
    public void insertBetween_conditionMet_operationApplied()
            throws InterruptedException, ExecutionException {
        ListKey fooKey = list.insertLast("foo").getKey();
        ListKey bazKey = list.insertLast("baz").getKey();
        ListOperation conditionalOperation = ListOperation.insertBetween(fooKey,
                bazKey, "bar");
        boolean succeded = list.apply(conditionalOperation)
                .getCompletableFuture().get();
        Assert.assertTrue(succeded);
        assertValues(list, "foo", "bar", "baz");
    }

    @Test
    public void insertBetween_conditionNotMet_operationRejected()
            throws InterruptedException, ExecutionException {
        ListKey fooKey = list.insertLast("foo").getKey();
        ListKey bazKey = list.insertLast("baz").getKey();
        list.insertAfter(fooKey, "qux");
        ListOperation conditionalOperation = ListOperation.insertBetween(fooKey,
                bazKey, "bar");
        boolean succeded = list.apply(conditionalOperation)
                .getCompletableFuture().get();
        Assert.assertFalse(succeded);
        assertValues(list, "foo", "qux", "baz");
    }

    @Test
    public void insertWithNextCondition_conditionMet_operationApplied()
            throws InterruptedException, ExecutionException {
        ListKey fooKey = list.insertLast("foo").getKey();
        ListOperation conditionalOperation = ListOperation.insertLast("bar")
                .ifLast(fooKey);
        boolean succeeded = list.apply(conditionalOperation)
                .getCompletableFuture().get();
        Assert.assertTrue(succeeded);
    }

    @Test
    public void insertWithNextCondition_conditionNotMet_operationRejected()
            throws InterruptedException, ExecutionException {
        ListKey fooKey = list.insertLast("foo").getKey();
        list.insertLast("notFoo");
        ListOperation conditionalOperation = ListOperation.insertLast("bar")
                .ifLast(fooKey);
        boolean succeeded = list.apply(conditionalOperation)
                .getCompletableFuture().get();
        Assert.assertFalse(succeeded);
    }

    @Test
    public void insertWithEmptyCondition_conditionMet_operationApplied()
            throws InterruptedException, ExecutionException {
        ListOperation conditionalOperation = ListOperation.insertLast("foo")
                .ifEmpty();
        boolean succeeded = list.apply(conditionalOperation)
                .getCompletableFuture().get();
        Assert.assertTrue(succeeded);
    }

    @Test
    public void insertWithEmptyCondition_conditionNotMet_operationRejected()
            throws InterruptedException, ExecutionException {
        list.insertLast("foo");
        ListOperation conditionalOperation = ListOperation.insertLast("bar")
                .ifEmpty();
        boolean succeeded = list.apply(conditionalOperation)
                .getCompletableFuture().get();
        Assert.assertFalse(succeeded);
    }

    @Test
    public void insertWithNotEmptyCondition_conditionMet_operationApplied()
            throws InterruptedException, ExecutionException {
        list.insertLast("foo");
        ListOperation conditionalOperation = ListOperation.insertLast("bar")
                .ifNotEmpty();
        boolean succeeded = list.apply(conditionalOperation)
                .getCompletableFuture().get();
        Assert.assertTrue(succeeded);
    }

    @Test
    public void insertWithNotEmptyCondition_conditionNotMet_operationRejected()
            throws InterruptedException, ExecutionException {
        ListOperation conditionalOperation = ListOperation.insertLast("foo")
                .ifNotEmpty();
        boolean succeeded = list.apply(conditionalOperation)
                .getCompletableFuture().get();
        Assert.assertFalse(succeeded);
    }

    @Test(expected = IllegalStateException.class)
    public void insertLast_ifEmpty_ifNotEmpty_exceptionIsThrown() {
        ListOperation.insertLast("foo").ifEmpty().ifNotEmpty();
    }

    @Test(expected = IllegalStateException.class)
    public void insertLast_ifNotEmpty_ifEmpty_exceptionIsThrown() {
        ListOperation.insertLast("foo").ifNotEmpty().ifEmpty();
    }

    @Test
    public void setValue_noCondition_operationApplied()
            throws InterruptedException, ExecutionException {
        ListKey fooKey = list.insertLast("foo").getKey();
        ListOperation operation = ListOperation.set(fooKey, "bar");

        boolean succeeded = list.apply(operation).getCompletableFuture().get();
        Assert.assertTrue(succeeded);
        String value = list.getItem(fooKey, String.class);
        Assert.assertEquals("bar", value);
    }

    @Test
    public void setValueWithConnectionScope_connectionDeactivate_entryRemoved()
            throws InterruptedException, ExecutionException {
        ListKey fooKey = list.insertLast("foo").getKey();
        ListOperation operation = ListOperation.set(fooKey, "bar")
                .withScope(EntryScope.CONNECTION);

        boolean succeeded = list.apply(operation).getCompletableFuture().get();
        Assert.assertTrue(succeeded);

        context.deactivate();
        context.activate();
        Assert.assertNull(list.getItem(fooKey, String.class));
    }

    @Test
    public void setValueWithNotEmptyCondition_conditionMet_operationApplied()
            throws InterruptedException, ExecutionException {
        ListKey fooKey = list.insertLast("foo").getKey();
        ListOperation operation = ListOperation.set(fooKey, "bar").ifNotEmpty();

        boolean succeeded = list.apply(operation).getCompletableFuture().get();
        Assert.assertTrue(succeeded);
    }

    @Test
    public void setValueWithEmptyCondition_conditionNotMet_operationRejected()
            throws InterruptedException, ExecutionException {
        ListKey fooKey = list.insertLast("foo").getKey();
        ListOperation operation = ListOperation.set(fooKey, "bar").ifEmpty();

        boolean succeeded = list.apply(operation).getCompletableFuture().get();
        Assert.assertFalse(succeeded);
    }

    @Test
    public void insertWithIfValueCondition_conditionMet_operationApplied()
            throws InterruptedException, ExecutionException {
        ListKey fooKey = list.insertLast("foo").getKey();
        ListOperation conditionalOperation = ListOperation.insertLast("bar")
                .ifValue(fooKey, "foo");
        boolean succeeded = list.apply(conditionalOperation)
                .getCompletableFuture().get();
        Assert.assertTrue(succeeded);
    }

    @Test
    public void insertWithIfValueCondition_conditionNotMet_operationRejected()
            throws InterruptedException, ExecutionException {
        ListKey fooKey = list.insertLast("foo").getKey();
        ListOperation conditionalOperation = ListOperation.insertLast("bar")
                .ifValue(fooKey, "baz");
        boolean succeeded = list.apply(conditionalOperation)
                .getCompletableFuture().get();
        Assert.assertFalse(succeeded);
    }

    @Test(expected = IllegalStateException.class)
    public void duplicateIfValueCondition_exceptionIsThrown() {
        ListKey fooKey = list.insertLast("foo").getKey();
        ListOperation conditionalOperation = ListOperation.insertLast("bar")
                .ifValue(fooKey, "foo").ifValue(fooKey, "bar");
    }

    @Test
    public void insertWithTwoIfValueConditions_bothMet_operationApplied()
            throws InterruptedException, ExecutionException {
        ListKey fooKey = list.insertLast("foo").getKey();
        ListKey barKey = list.insertLast("bar").getKey();
        ListOperation conditionalOperation = ListOperation.insertLast("baz")
                .ifValue(fooKey, "foo").ifValue(barKey, "bar");
        boolean succeeded = list.apply(conditionalOperation)
                .getCompletableFuture().get();
        Assert.assertTrue(succeeded);
    }

    @Test
    public void insertWithTwoIfValueConditions_oneNotMet_operationRejected()
            throws InterruptedException, ExecutionException {
        ListKey fooKey = list.insertLast("foo").getKey();
        ListKey barKey = list.insertLast("bar").getKey();
        ListOperation conditionalOperation = ListOperation.insertLast("baz")
                .ifValue(fooKey, "foo").ifValue(barKey, "quz");
        boolean succeeded = list.apply(conditionalOperation)
                .getCompletableFuture().get();
        Assert.assertFalse(succeeded);
    }

    @Test
    public void moveBeforeWithNoScope_connectionDeactivate_entryNotRemoved() {
        List<ListKey> keys = insertLast(list, "one");
        ListOperation operation = ListOperation.insertLast("two")
                .withScope(EntryScope.CONNECTION);
        ListKey key = list.apply(operation).getKey();

        list.moveBefore(keys.get(0), key);

        context.deactivate();
        context.activate();

        Assert.assertEquals("two", list.getItem(key, String.class));
    }

    @Test
    public void moveBeforeWithConnectionScope_connectionDeactivate_entryRemoved() {
        List<ListKey> keys = insertLast(list, "one", "two");
        ListOperation operation = ListOperation.insertLast("three")
                .withScope(EntryScope.CONNECTION);
        ListKey key = list.apply(operation).getKey();

        ListOperation moveOperation = ListOperation.moveBefore(keys.get(0), key)
                .withScope(EntryScope.CONNECTION);
        list.apply(moveOperation);

        context.deactivate();
        context.activate();

        Assert.assertNull(list.getItem(key, String.class));
        Assert.assertEquals(2, list.getKeys().count());
    }

    @Test
    public void moveBeforeWithTopicScope_connectionDeactivate_entryNotRemoved() {
        List<ListKey> keys = insertLast(list, "one", "two");
        ListOperation operation = ListOperation.insertLast("three")
                .withScope(EntryScope.CONNECTION);
        ListKey key = list.apply(operation).getKey();

        ListOperation moveOperation = ListOperation.moveBefore(keys.get(0), key)
                .withScope(EntryScope.TOPIC);
        list.apply(moveOperation);

        context.deactivate();
        context.activate();

        Assert.assertEquals("three", list.getItem(key, String.class));
    }

    @Test
    public void moveAfterWithConnectionScope_connectionDeactivate_entryRemoved() {
        List<ListKey> keys = insertLast(list, "one", "two");
        ListOperation operation = ListOperation.insertLast("three")
                .withScope(EntryScope.CONNECTION);
        ListKey key = list.apply(operation).getKey();

        ListOperation moveOperation = ListOperation.moveAfter(keys.get(0), key)
                .withScope(EntryScope.CONNECTION);
        list.apply(moveOperation);

        context.deactivate();
        context.activate();

        Assert.assertNull(list.getItem(key, String.class));
        Assert.assertEquals(2, list.getKeys().count());
    }

    @Test
    public void expirationTimeout_unpopulatedList_listClearWithoutExplosions() {
        Duration timeout = Duration.ofMinutes(15);
        list.setExpirationTimeout(timeout);
        registration.remove();
        ce.setClock(Clock.offset(ce.getClock(), timeout.plusMinutes(1)));

        ce.openTopicConnection(context, "topic", SystemUserInfo.getInstance(),
                connection -> null);
        // All is fine if openTopicConnection runs successfully
    }

    @Test
    public void insertAndSubscribe_thenDispatch_subscriberInvokedOnce() {
        dispatcher.hold();
        list.insertLast("foo");
        ListAppendSpy spy = new ListAppendSpy();
        spy.addExpectedEvent("foo");
        list.subscribe(spy);
        dispatcher.release();
        spy.assertNoExpectedEvents();
    }

    private static List<ListKey> insertLast(CollaborationList list,
            String... values) {
        return Stream.of(values).map(list::insertLast)
                .map(ListOperationResult::getKey).collect(Collectors.toList());
    }

    private static void assertEvent(ListChangeType type, ListKey key,
            String oldValue, String value, ListKey oldPrev, ListKey prev,
            ListKey oldNext, ListKey next, ListChangeEvent event) {
        Assert.assertEquals(type, event.getType());
        Assert.assertEquals(key, event.getKey());
        Assert.assertEquals(oldValue, event.getOldValue(String.class));
        Assert.assertEquals(oldValue, event.getOldValue(STRING_REF));
        Assert.assertEquals(value, event.getValue(String.class));
        Assert.assertEquals(value, event.getValue(STRING_REF));
        Assert.assertEquals(oldNext, event.getOldNext());
        Assert.assertEquals(next, event.getNext());
        Assert.assertEquals(oldPrev, event.getOldPrev());
        Assert.assertEquals(prev, event.getPrev());
    }

    private static void assertValues(CollaborationList list, String... values) {
        Assert.assertEquals(Arrays.asList(values), list.getItems(String.class));
        Assert.assertEquals(Arrays.asList(values), list.getItems(STRING_REF));

        Assert.assertEquals(Arrays.asList(values),
                list.getKeys().map(key -> list.getItem(key, String.class))
                        .collect(Collectors.toList()));
        Assert.assertEquals(Arrays.asList(values),
                list.getKeys().map(key -> list.getItem(key, STRING_REF))
                        .collect(Collectors.toList()));
    }
}
