/**
 * Copyright (C) 2000-2022 Vaadin Ltd
 *
 * This program is available under Vaadin Commercial License and Service Terms.
 *
 *
 * See <https://vaadin.com/commercial-license-and-service-terms> for the full
 * license.
 */
package com.vaadin.collaborationengine;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vaadin.collaborationengine.MembershipEvent.MembershipEventType;
import com.vaadin.collaborationengine.TestUtil.MockConfiguration;
import com.vaadin.collaborationengine.util.MockService;
import com.vaadin.collaborationengine.util.TestBackendFactory;

public class BackendTest {
    private static final String LOG_ID = BackendTest.class.getName();

    private TestBackendFactory backendFactory;

    @Before
    public void setup() {
        backendFactory = new TestBackendFactory();
    }

    @After
    public void cleanup() {
    }

    @Test
    public void nodeJoins_eventLogReceivesEvent() {
        CollaborationEngine node = createNode();
        Backend backend = node.getConfiguration().getBackend();
        UUID nodeId = backend.getNodeId();
        AtomicBoolean joined = new AtomicBoolean();

        backend.addMembershipListener(event -> joined
                .set(event.getType().equals(MembershipEventType.JOIN)
                        && event.getNodeId().equals(nodeId)));
        join(node);

        Assert.assertTrue("Join event not received", joined.get());
    }

    @Test
    public void nodeLeaves_eventLogReceivesEvent() {
        CollaborationEngine node = createNode();
        Backend backend = node.getConfiguration().getBackend();
        UUID nodeId = backend.getNodeId();
        AtomicBoolean left = new AtomicBoolean();

        backend.addMembershipListener(event -> left
                .set(event.getType().equals(MembershipEventType.LEAVE)
                        && event.getNodeId().equals(nodeId)));
        leave(node);

        Assert.assertTrue("Leave event not received", left.get());
    }

    @Test
    public void nodeJoins_onlyNode_isTopicLeader() {
        CollaborationEngine node = createNode();
        AtomicBoolean isLeader = new AtomicBoolean();

        join(node);

        node.openTopicConnection(node.getSystemContext(), "topic",
                new UserInfo("foo"), conn -> {
                    isLeader.set(conn.getTopic().isLeader());
                    return null;
                });

        Assert.assertTrue("Node should be topic leader, but it's not",
                isLeader.get());
    }

    @Test
    public void nodeJoins_otherNodePresent_isNotTopicLeader() {
        CollaborationEngine node1 = createNode();
        CollaborationEngine node2 = createNode();
        AtomicBoolean isLeader = new AtomicBoolean(true);

        join(node1);

        node1.openTopicConnection(node1.getSystemContext(), "topic",
                new UserInfo("foo"), conn -> null);

        join(node2);

        node2.openTopicConnection(node2.getSystemContext(), "topic",
                new UserInfo("foo"), conn -> {
                    isLeader.set(conn.getTopic().isLeader());
                    return null;
                });

        Assert.assertFalse("Node should not be topic leader, but it is",
                isLeader.get());
    }

    @Test
    public void twoNodes_leaderLeaves_otherBecomeLeader() {
        CollaborationEngine node1 = createNode();
        CollaborationEngine node2 = createNode();
        AtomicBoolean isLeader = new AtomicBoolean();

        join(node1);

        node1.openTopicConnection(node1.getSystemContext(), "topic",
                new UserInfo("foo"), conn -> null);

        join(node2);

        node2.openTopicConnection(node2.getSystemContext(), "topic",
                new UserInfo("foo"), conn -> null);

        leave(node1);

        node2.openTopicConnection(node2.getSystemContext(), "topic",
                new UserInfo("foo"), conn -> {
                    isLeader.set(conn.getTopic().isLeader());
                    return null;
                });

        Assert.assertTrue("Node has not become topic leader", isLeader.get());
    }

    @Test
    public void mapEntryWithConnectionScope_ownerLeaves_entryRemoved() {
        CollaborationEngine node1 = createNode();
        CollaborationEngine node2 = createNode();

        join(node1);

        node1.openTopicConnection(node1.getSystemContext(), "topic",
                new UserInfo("foo"), conn -> {
                    conn.getNamedMap("map").put("key", "value",
                            EntryScope.CONNECTION);
                    return null;
                });

        node2.openTopicConnection(node2.getSystemContext(), "topic",
                new UserInfo("foo"), conn -> null);

        leave(node1);

        AtomicBoolean staleEntryRemoved = new AtomicBoolean();

        node2.openTopicConnection(node2.getSystemContext(), "topic",
                new UserInfo("foo"), conn -> {
                    String value = conn.getNamedMap("map").get("key",
                            String.class);
                    staleEntryRemoved.set(value == null);
                    return null;
                });

        Assert.assertTrue("Stale entry not removed", staleEntryRemoved.get());
    }

    @Test
    public void listEntryWithConnectionScope_ownerLeaves_entryRemoved() {
        CollaborationEngine node1 = createNode();
        CollaborationEngine node2 = createNode();

        join(node1);

        node1.openTopicConnection(node1.getSystemContext(), "topic",
                new UserInfo("foo"), conn -> {
                    ListOperation operation = ListOperation.insertLast("value")
                            .withScope(EntryScope.CONNECTION);
                    conn.getNamedList("list").apply(operation);
                    return null;
                });

        node2.openTopicConnection(node2.getSystemContext(), "topic",
                new UserInfo("foo"), conn -> null);

        leave(node1);

        AtomicBoolean staleEntryRemoved = new AtomicBoolean();

        node2.openTopicConnection(node2.getSystemContext(), "topic",
                new UserInfo("foo"), conn -> {
                    staleEntryRemoved.set(conn.getNamedList("list")
                            .getItems(String.class).isEmpty());
                    return null;
                });

        Assert.assertTrue("Stale entry not removed", staleEntryRemoved.get());
    }

    @Test
    public void truncate_subscribeSuccessful() {
        CollaborationEngine node1 = createNode();
        CollaborationEngine node2 = createNode();

        join(node1);

        node1.openTopicConnection(node1.getSystemContext(), "topic",
                new UserInfo("foo"), conn -> null);

        join(node2);

        node2.openTopicConnection(node2.getSystemContext(), "topic",
                new UserInfo("foo"), conn -> null);

        List<UUID> events1 = new ArrayList<>();

        Backend.EventLog log1 = getEventLog(node1);
        try {
            log1.subscribe(null, (id, value) -> events1.add(id));
        } catch (Backend.EventIdNotFoundException e) {
            Assert.fail();
        }

        log1.truncate(events1.get(1));

        List<UUID> events2 = new ArrayList<>();

        Backend.EventLog log2 = getEventLog(node2);
        try {
            log2.subscribe(null, (id, node) -> events2.add(id));
        } catch (Backend.EventIdNotFoundException e) {
            Assert.fail();
        }

        Assert.assertEquals(3, events2.size());
    }

    @Test
    public void truncateWithMissingId_nothingHappens() {
        CollaborationEngine node1 = createNode();
        CollaborationEngine node2 = createNode();

        join(node1);

        node1.openTopicConnection(node1.getSystemContext(), "topic",
                new UserInfo("foo"), conn -> null);

        join(node2);

        node2.openTopicConnection(node2.getSystemContext(), "topic",
                new UserInfo("foo"), conn -> null);

        List<UUID> events1 = new ArrayList<>();

        Backend.EventLog log1 = getEventLog(node1);
        try {
            log1.subscribe(null, (id, node) -> events1.add(id));
        } catch (Backend.EventIdNotFoundException e) {
            Assert.fail();
        }

        log1.truncate(events1.get(1));
        log1.truncate(events1.get(0));

        List<UUID> events2 = new ArrayList<>();

        Backend.EventLog log2 = getEventLog(node2);
        try {
            log2.subscribe(null, (id, node) -> events2.add(id));
        } catch (Backend.EventIdNotFoundException e) {
            Assert.fail();
        }

        Assert.assertEquals(3, events2.size());
    }

    @Test
    public void truncate_subscribeWithMissingId_subscribeFails() {
        CollaborationEngine node1 = createNode();
        CollaborationEngine node2 = createNode();

        join(node1);

        node1.openTopicConnection(node1.getSystemContext(), "topic",
                new UserInfo("foo"), conn -> null);

        join(node2);

        node2.openTopicConnection(node2.getSystemContext(), "topic",
                new UserInfo("foo"), conn -> null);

        List<UUID> events1 = new ArrayList<>();

        Backend.EventLog log1 = getEventLog(node1);
        try {
            log1.subscribe(null, (id, node) -> events1.add(id));
        } catch (Backend.EventIdNotFoundException e) {
            Assert.fail();
        }

        log1.truncate(events1.get(1));

        List<UUID> events2 = new ArrayList<>();

        Backend.EventLog log2 = getEventLog(node2);
        Assert.assertThrows(Backend.EventIdNotFoundException.class, () -> log2
                .subscribe(events1.get(0), (id, node) -> events2.add(id)));
    }

    @Test
    public void initializeFromSnapshot_retryOnce_initializationSucceeds() {
        CollaborationEngine node = createNode();
        join(node);

        UUID id = BackendUtil
                .initializeFromSnapshot(node, new MockInitializer(1)).join();

        Assert.assertNotNull(id);
    }

    @Test
    public void initializeFromSnapshot_retryFiftyTimes_initializationFails() {
        CollaborationEngine node = createNode();
        join(node);

        UUID id = BackendUtil
                .initializeFromSnapshot(node, new MockInitializer(50)).join();

        Assert.assertNull(id);
    }

    private CollaborationEngine createNode() {
        CollaborationEngineConfiguration conf = new MockConfiguration(e -> {
        });
        conf.setBackend(backendFactory.createBackend());
        return TestUtil.createTestCollaborationEngine(new MockService(), conf);
    }

    private void join(CollaborationEngine node) {
        backendFactory.join(node.getConfiguration().getBackend());
    }

    private void leave(CollaborationEngine node) {
        backendFactory.leave(node.getConfiguration().getBackend());
    }

    private Backend.EventLog getEventLog(CollaborationEngine node) {
        return node.getConfiguration().getBackend().openEventLog(LOG_ID);
    }

    static class MockInitializer implements BackendUtil.Initializer {
        int attempts;
        int attemptCount = 0;

        public MockInitializer(int attempts) {
            this.attempts = attempts;
        }

        public CompletableFuture<UUID> initialize() {
            if (attemptCount < attempts) {
                attemptCount++;
                return CompletableFuture.failedFuture(
                        new Backend.EventIdNotFoundException("Failed"));
            } else {
                return CompletableFuture.completedFuture(UUID.randomUUID());
            }
        }
    }
}
