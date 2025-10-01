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
import java.util.List;
import java.util.UUID;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vaadin.collaborationengine.util.MockService;
import com.vaadin.flow.server.VaadinService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

public class JsonUtilTest {

    private UserInfo user;
    private VaadinService service;
    private CollaborationEngine ce;

    @Before
    public void init() {
        service = new MockService();
        ce = new CollaborationEngine();
        service.getContext().setAttribute(CollaborationEngine.class, ce);
        VaadinService.setCurrent(service);

        user = new UserInfo("my-id", "my-name", "my-image");
        user.setAbbreviation("my-abbreviation");
        user.setColorIndex(5);
    }

    @After
    public void cleanUp() {
        VaadinService.setCurrent(null);
    }

    @Test
    public void userInfo_toJsonAndBack_allPropertiesPreserved() {
        UserInfo deserializedUser = JsonUtil
                .toInstance(JsonUtil.toJsonNode(user), UserInfo.class);

        Assert.assertEquals("my-id", deserializedUser.getId());
        Assert.assertEquals("my-name", deserializedUser.getName());
        Assert.assertEquals("my-abbreviation",
                deserializedUser.getAbbreviation());
        Assert.assertEquals("my-image", deserializedUser.getImage());
        Assert.assertEquals(5, deserializedUser.getColorIndex());
    }

    @Test
    public void usersList_toJson_noRedundantData() throws Exception {
        List<UserInfo> users = Collections.singletonList(user);
        JsonNode actualJson = JsonUtil.toJsonNode(users);

        // Create expected JSON structure programmatically to avoid ordering
        // issues
        JsonNode expectedJson = JsonUtil.getObjectMapper().readTree(
                "[{\"id\":\"my-id\",\"name\":\"my-name\",\"abbreviation\":\"my-abbreviation\",\"image\":\"my-image\",\"colorIndex\":5}]");

        Assert.assertEquals(expectedJson, actualJson);
    }

    @Test
    public void userInfo_toJsonNode() {
        JsonNode userJson = JsonUtil.toJsonNode(user);
        Assert.assertEquals("my-id", userJson.get("id").textValue());
        Assert.assertEquals("my-name", userJson.get("name").textValue());
        Assert.assertEquals("my-abbreviation",
                userJson.get("abbreviation").textValue());
        Assert.assertEquals("my-image", userJson.get("image").textValue());
        Assert.assertEquals(5, userJson.get("colorIndex").intValue());
    }

    @Test
    public void literalNull_toUUID_returnsNull() {
        Assert.assertNull(JsonUtil.toUUID(null));
    }

    @Test
    public void nullJsonNode_toUUID_returnsNull() {
        Assert.assertNull(JsonUtil.toUUID(NullNode.instance));
    }

    @Test
    public void uuidString_toUUID_returnsUUID() {
        UUID uuid = UUID.randomUUID();
        ObjectNode node = JsonUtil.getObjectMapper().createObjectNode();
        node.put("uuid", uuid.toString());
        Assert.assertEquals(uuid, JsonUtil.toUUID(node.get("uuid")));
    }
}
