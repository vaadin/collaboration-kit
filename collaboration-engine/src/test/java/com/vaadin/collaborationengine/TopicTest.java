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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vaadin.collaborationengine.Topic.ChangeResult;
import com.vaadin.collaborationengine.util.TestUtils;

import tools.jackson.databind.node.ObjectNode;

public class TopicTest {

    private Topic topic;

    @Before
    public void init() {
        topic = new Topic("id", TestUtil::createTestCollaborationEngine, null);
    }

    @Test
    public void applyChange_newMap_mapCreatedWithNewEntry() {
        ObjectNode change = JsonUtil.createPutChange("foo", "bar", null,
                MockJson.BAZ, null);
        topic.applyChange(UUID.randomUUID(), JsonUtil.toString(change));
        Assert.assertEquals("baz", topic.getMapValue("foo", "bar").textValue());
    }

    @Test
    public void applyChange_existingMap_mapEntryUpdated() {
        ObjectNode change = JsonUtil.createPutChange("foo", "bar", null,
                MockJson.BAZ, null);
        topic.applyChange(UUID.randomUUID(), JsonUtil.toString(change));
        ObjectNode change1 = JsonUtil.createPutChange("foo", "bar", null,
                MockJson.QUX, null);
        topic.applyChange(UUID.randomUUID(), JsonUtil.toString(change1));

        Assert.assertEquals("qux", topic.getMapValue("foo", "bar").textValue());
    }

    @Test
    public void applyChange_existingMap_emptyValue_mapEntryRemoved() {
        ObjectNode change = JsonUtil.createPutChange("foo", "bar", null,
                MockJson.BAZ, null);
        topic.applyChange(UUID.randomUUID(), JsonUtil.toString(change));
        ObjectNode change1 = JsonUtil.createPutChange("foo", "bar", null, null,
                null);
        topic.applyChange(UUID.randomUUID(), JsonUtil.toString(change1));

        Assert.assertNull(topic.getMapValue("foo", "bar"));
    }

    @Test
    public void applyReplace_havingLatestExpectedValue_success() {
        ObjectNode change = JsonUtil.createPutChange("foo", "bar", null,
                MockJson.BAZ, null);
        topic.applyChange(UUID.randomUUID(), JsonUtil.toString(change));
        ObjectNode replaceChange = JsonUtil.createPutChange("foo", "bar",
                MockJson.BAZ, MockJson.QUX, null);

        Assert.assertEquals(ChangeResult.ACCEPTED, topic.applyChange(
                UUID.randomUUID(), JsonUtil.toString(replaceChange)));
        Assert.assertEquals("qux", topic.getMapValue("foo", "bar").textValue());
    }

    @Test
    public void applyReplace_havingWrongExpectedValue_fail() {
        ObjectNode change = JsonUtil.createPutChange("foo", "bar", null,
                MockJson.BAZ, null);
        topic.applyChange(UUID.randomUUID(), JsonUtil.toString(change));
        ObjectNode replaceChange = JsonUtil.createPutChange("foo", "bar",
                MockJson.FOO, MockJson.QUX, null);

        Assert.assertEquals(ChangeResult.REJECTED, topic.applyChange(
                UUID.randomUUID(), JsonUtil.toString(replaceChange)));
        Assert.assertEquals("baz", topic.getMapValue("foo", "bar").textValue());
    }

    @Test
    public void throwingSubscriber_removedAndOthersStillInvoked() {
        AtomicInteger count = new AtomicInteger(0);

        topic.subscribeToChange((id, event) -> {
            // Assert doesn't throw a runtime exception, which means that it
            // would not be caught
            Assert.assertEquals(
                    "Throwing subscriber should be run before working subscriber",
                    0, count.get());
            throw new RuntimeException("Fail on purpose");
        });

        topic.subscribeToChange((id, event) -> count.getAndIncrement());

        try {
            ObjectNode change = JsonUtil.createPutChange("map", "key", null,
                    MockJson.BAZ, null);
            topic.applyChange(UUID.randomUUID(), JsonUtil.toString(change));
            Assert.fail("Exception expected");
        } catch (RuntimeException expected) {
        }

        Assert.assertEquals(
                "Working subscriber should be notified even though another subscriber failed",
                1, count.get());

        // No try-catch needed - failing subscriber should have been removed
        ObjectNode change = JsonUtil.createPutChange("map", "key", null,
                MockJson.QUX, null);
        topic.applyChange(UUID.randomUUID(), JsonUtil.toString(change));

        Assert.assertEquals("Non-failing subscriber should still be notified",
                2, count.get());
    }

    @Test
    public void applyChange_listContainsAppendedItem() {
        ObjectNode change = JsonUtil.createListChange(
                ListOperation.OperationType.INSERT_AFTER, "foo", null, null,
                MockJson.FOO, null, Collections.emptyMap(),
                Collections.emptyMap(), null);
        topic.applyChange(UUID.randomUUID(), JsonUtil.toString(change));
        Assert.assertEquals("foo",
                topic.getListItems("foo").findFirst().get().value.textValue());
    }

    @Test
    public void throwingListSubscriber_removedAndOthersStillInvoked() {
        AtomicInteger count = new AtomicInteger(0);

        topic.subscribeToChange((id, event) -> {
            // Assert doesn't throw a runtime exception, which means that it
            // would not be caught
            Assert.assertEquals(
                    "Throwing subscriber should be run before working subscriber",
                    0, count.get());
            throw new RuntimeException("Fail on purpose");
        });

        topic.subscribeToChange((id, event) -> count.getAndIncrement());

        try {
            ObjectNode change = JsonUtil.createListChange(
                    ListOperation.OperationType.INSERT_AFTER, "foo", null, null,
                    MockJson.BAZ, null, Collections.emptyMap(),
                    Collections.emptyMap(), null);
            topic.applyChange(UUID.randomUUID(), JsonUtil.toString(change));
            Assert.fail("Exception expected");
        } catch (RuntimeException expected) {
        }

        Assert.assertEquals(
                "Working subscriber should be notified even though another subscriber failed",
                1, count.get());

        // No try-catch needed - failing subscriber should have been removed
        ObjectNode change = JsonUtil.createListChange(
                ListOperation.OperationType.INSERT_AFTER, "foo", null, null,
                MockJson.QUX, null, Collections.emptyMap(),
                Collections.emptyMap(), null);
        topic.applyChange(UUID.randomUUID(), JsonUtil.toString(change));

        Assert.assertEquals("Non-failing subscriber should still be notified",
                2, count.get());
    }

    @Test
    public void serializeTopic() {
        Topic deserializedTopic = TestUtils.serialize(topic);
    }
}
