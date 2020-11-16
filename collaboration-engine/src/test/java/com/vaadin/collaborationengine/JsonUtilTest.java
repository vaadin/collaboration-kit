package com.vaadin.collaborationengine;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JsonUtilTest {

    private UserInfo user;

    @Before
    public void init() {
        user = new UserInfo("my-id", "my-name", "my-image");
        user.setAbbreviation("my-abbreviation");
        user.setColorIndex(5);
    }

    @Test
    public void usersList_toJsonAndBack_returnsEqualUsers() {
        List<UserInfo> users = Arrays.asList(new UserInfo("1"),
                new UserInfo("2"));
        JsonNode usersNode = JsonUtil.toJsonNode(users);
        Assert.assertEquals(users,
                JsonUtil.toInstance(usersNode, JsonUtil.LIST_USER_TYPE_REF));
    }

    @Test
    public void emptyUsersList_toJsonAndBack_returnsEmptyList() {
        Assert.assertEquals(Collections.emptyList(), JsonUtil.toInstance(
                JsonUtil.toJsonNode(Collections.emptyList()), List.class));
    }

    @Test
    public void nullUsersList_toJsonAndBack_returnsNull() {
        Assert.assertNull(
                JsonUtil.toInstance(JsonUtil.toJsonNode(null), Object.class));
    }

    @Test
    public void userInfo_toJsonAndBack_allPropertiesPreserved() {
        UserInfo deserializedUser = JsonUtil
                .toInstance(JsonUtil.toJsonNode(Arrays.asList(user)),
                        JsonUtil.LIST_USER_TYPE_REF)
                .get(0);

        Assert.assertEquals("my-id", deserializedUser.getId());
        Assert.assertEquals("my-name", deserializedUser.getName());
        Assert.assertEquals("my-abbreviation",
                deserializedUser.getAbbreviation());
        Assert.assertEquals("my-image", deserializedUser.getImage());
        Assert.assertEquals(5, deserializedUser.getColorIndex());
    }

    @Test
    public void usersList_toJson_noRedundantData() {
        List<UserInfo> users = Collections.singletonList(user);
        String jsonUsers = JsonUtil.toJsonNode(users).toString();
        Assert.assertEquals(
                "[{\"id\":\"my-id\",\"name\":\"my-name\",\"abbreviation\":\"my-abbreviation\",\"image\":\"my-image\",\"colorIndex\":5}]",
                jsonUsers);
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
}