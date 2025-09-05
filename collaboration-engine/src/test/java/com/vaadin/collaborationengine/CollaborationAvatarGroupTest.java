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

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vaadin.collaborationengine.util.MockService;
import com.vaadin.collaborationengine.util.MockUI;
import com.vaadin.collaborationengine.util.ReflectionUtils;
import com.vaadin.collaborationengine.util.TestUtils;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.avatar.AvatarGroup;
import com.vaadin.flow.component.avatar.AvatarGroup.AvatarGroupItem;
import com.vaadin.flow.function.SerializableSupplier;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.streams.DownloadHandler;

public class CollaborationAvatarGroupTest {

    private static final String TOPIC_ID = "topic";
    private static final String TOPIC_ID_2 = "topic2";

    public static class AvatarGroupTestClient {
        final UI ui;
        final UserInfo user;
        CollaborationAvatarGroup group;

        AvatarGroupTestClient(int index,
                SerializableSupplier<CollaborationEngine> ceSupplier) {
            this(index, TOPIC_ID, ceSupplier);
        }

        AvatarGroupTestClient(int index, String topicId,
                SerializableSupplier<CollaborationEngine> ceSupplier) {
            this.ui = new MockUI();
            this.user = new UserInfo("id" + index, "name" + index,
                    "image" + index);
            user.setAbbreviation("abbreviation" + index);
            user.setColorIndex(index);
            group = new CollaborationAvatarGroup(user, topicId, ceSupplier);
        }

        void attach() {
            ui.add(group);
        }

        void detach() {
            ui.remove(group);
        }

        void setGroupTopic(String topicId) {
            group.setTopic(topicId);
        }

        List<AvatarGroupItem> getItems() {
            return group.getContent().getItems();
        }

        Set<String> getItemNames() {
            return getItemNamesStream().collect(Collectors.toSet());
        }

        Stream<String> getItemNamesStream() {
            return group.getContent().getItems().stream()
                    .map(AvatarGroupItem::getName);
        }
    }

    private VaadinService service;
    private SerializableSupplier<CollaborationEngine> ceSupplier;

    private AvatarGroupTestClient client1;
    private AvatarGroupTestClient client2;
    private AvatarGroupTestClient client3;

    private AvatarGroupTestClient clientInOtherTopic;

    private AvatarGroupTestClient clientInMultipleTabs1;
    private AvatarGroupTestClient clientInMultipleTabs2;

    @Before
    public void init() {
        service = new MockService();
        VaadinService.setCurrent(service);
        CollaborationEngine ce = TestUtil
                .createTestCollaborationEngine(service);
        ceSupplier = () -> ce;
        client1 = new AvatarGroupTestClient(1, ceSupplier);
        client2 = new AvatarGroupTestClient(2, ceSupplier);
        client3 = new AvatarGroupTestClient(3, ceSupplier);
        clientInOtherTopic = new AvatarGroupTestClient(4, TOPIC_ID_2,
                ceSupplier);
        clientInMultipleTabs1 = new AvatarGroupTestClient(5, ceSupplier);
        clientInMultipleTabs2 = new AvatarGroupTestClient(5, ceSupplier);
    }

    @After
    public void cleanUp() {
        UI.setCurrent(null);
        VaadinService.setCurrent(null);
    }

    @Test
    public void beforeAttach_ownAvatarDisplayed() {
        assertEquals(TestUtils.newHashSet("name1"), client1.getItemNames());
    }

    @Test
    public void beforeAttachNoOwnAvatar_noInitialAvatar() {
        client1.group.setOwnAvatarVisible(false);
        assertEquals(Collections.emptyList(), client1.getItems());
    }

    @Test
    public void attach_ownAvatarDisplayed() {
        client1.attach();
        assertEquals(TestUtils.newHashSet("name1"), client1.getItemNames());
    }

    @Test
    public void attachTwoGroups_bothAvatarsDisplayed() {
        client1.attach();
        client2.attach();
        assertEquals(TestUtils.newHashSet("name1", "name2"),
                client1.getItemNames());
        assertEquals(TestUtils.newHashSet("name1", "name2"),
                client2.getItemNames());
    }

    @Test
    public void attachSameUserTwice_avatarDisplayedOnce() {
        clientInMultipleTabs1.attach();
        clientInMultipleTabs2.attach();
        assertEquals(TestUtils.newHashSet("name5"),
                clientInMultipleTabs1.getItemNames());
        assertEquals(TestUtils.newHashSet("name5"),
                clientInMultipleTabs2.getItemNames());
    }

    @Test
    public void userPropertiesPropagatedToAvatarGroupItems() {
        client1.attach();
        client2.attach();
        AvatarGroupItem item = client2.getItems().get(1);

        assertEquals("name1", item.getName());
        assertEquals("abbreviation1", item.getAbbreviation());
        assertEquals("image1", item.getImage());
        assertEquals(1, item.getColorIndex().intValue());
    }

    @Test
    public void ownAvatarIsAlwaysFirst() {
        client1.attach();
        client2.attach();
        AvatarGroupItem item = client2.getItems().get(0);

        assertEquals("name2", item.getName());
    }

    @Test
    public void detach_avatarRemovedInOtherClient() {
        client1.attach();
        client2.attach();

        client1.detach();
        assertEquals(TestUtils.newHashSet("name2"), client2.getItemNames());
    }

    @Test
    public void threeUsers_attach_detach_avatarsUpdated() {
        client1.attach();
        client2.attach();
        client3.attach();

        Set<String> expected = TestUtils.newHashSet("name1", "name2", "name3");
        assertEquals(expected, client1.getItemNames());
        assertEquals(expected, client2.getItemNames());
        assertEquals(expected, client3.getItemNames());

        client2.detach();

        expected = TestUtils.newHashSet("name1", "name3");
        assertEquals(expected, client1.getItemNames());
        assertEquals(expected, client3.getItemNames());
    }

    @Test
    public void groupsWithDifferentTopicIds_avatarsNotUpdated() {
        client1.attach();
        clientInOtherTopic.attach();

        assertEquals(TestUtils.newHashSet("name1"), client1.getItemNames());
        assertEquals(TestUtils.newHashSet("name4"),
                clientInOtherTopic.getItemNames());
    }

    @Test
    public void detach_onlyLocalAvatarDisplayed() {
        client1.attach();
        client2.attach();
        client3.attach();

        client1.detach();

        assertEquals(TestUtils.newHashSet("name1"), client1.getItemNames());
    }

    @Test
    public void detach_reattach_avatarsUpdated() {
        client1.attach();
        client2.attach();
        client3.attach();

        client1.detach();
        client1.attach();

        Set<String> expected = TestUtils.newHashSet("name2", "name3", "name1");
        assertEquals(expected, client1.getItemNames());
        assertEquals(expected, client2.getItemNames());
    }

    @Test
    public void attachSameUserTwice_detachOne_avatarNotRemoved() {
        clientInMultipleTabs1.attach();
        clientInMultipleTabs2.attach();
        clientInMultipleTabs2.detach();
        assertEquals(TestUtils.newHashSet("name5"),
                clientInMultipleTabs1.getItemNames());
    }

    @Test
    public void attachSameUserTwice_detachBoth_avatarRemoved() {
        client1.attach();
        clientInMultipleTabs1.attach();
        clientInMultipleTabs2.attach();
        clientInMultipleTabs1.detach();
        clientInMultipleTabs2.detach();
        assertEquals(TestUtils.newHashSet("name1"), client1.getItemNames());
    }

    @Test
    public void setTopic_closeExistingConnection() {
        client1.attach();
        client2.attach();

        client1.setGroupTopic("new topic");
        client3.attach();
        assertEquals(TestUtils.newHashSet("name1"), client1.getItemNames());
    }

    @Test
    public void setTopic_showAvatarsFromNewTopic() {
        client1.attach();
        client2.attach();

        AvatarGroupTestClient newClient = new AvatarGroupTestClient(9,
                "new topic", ceSupplier);
        newClient.attach();

        client1.setGroupTopic("new topic");
        assertEquals(TestUtils.newHashSet(newClient.user.getName(),
                client1.user.getName()), client1.getItemNames());

        AvatarGroupTestClient newClient1 = new AvatarGroupTestClient(10,
                "new topic", ceSupplier);
        newClient1.attach();
        assertEquals(
                TestUtils.newHashSet(newClient.user.getName(),
                        client1.user.getName(), newClient1.user.getName()),
                client1.getItemNames());
    }

    @Test
    public void setTopic_changingFromNullUpdatesAvatars() {
        client1.attach();

        AvatarGroupTestClient newClient = new AvatarGroupTestClient(9, null,
                ceSupplier);
        newClient.attach();
        assertEquals(TestUtils.newHashSet(newClient.user.getName()),
                newClient.getItemNames());

        newClient.setGroupTopic("topic");
        assertEquals(TestUtils.newHashSet(client1.user.getName(),
                newClient.user.getName()), newClient.getItemNames());
    }

    @Test
    public void setTopic_nullTopic_closeConnectionAndRemoveRemoteAvatars() {
        client1.attach();
        client2.attach();

        client1.setGroupTopic(null);
        client3.attach();
        assertEquals(TestUtils.newHashSet("name1"), client1.getItemNames());
    }

    @Test
    public void nullTopic_setTopic_avatarsUpdated() {
        client2.attach();
        client3.attach();

        client1.group = new CollaborationAvatarGroup(client1.user, null,
                ceSupplier);
        client1.setGroupTopic(TOPIC_ID);
        client1.attach();
        assertEquals(TestUtils.newHashSet("name2", "name3", "name1"),
                client1.getItemNames());
    }

    private static List<String> blackListedMethods = Arrays.asList("setItems",
            "getItems", "add", "remove");

    @Test
    public void avatarGroup_replicateRelevantAPIs() {
        List<String> avatarGroupMethods = ReflectionUtils
                .getMethodNames(AvatarGroup.class);
        List<String> collaborationAvatarGroupMethods = ReflectionUtils
                .getMethodNames(CollaborationAvatarGroup.class);

        List<String> missingMethods = avatarGroupMethods.stream()
                .filter(m -> !blackListedMethods.contains(m)
                        && !collaborationAvatarGroupMethods.contains(m))
                .collect(Collectors.toList());

        if (!missingMethods.isEmpty()) {
            Assert.fail("Missing wrapper for methods: "
                    + missingMethods.toString());
        }
    }

    @Test
    public void imageHandler_beforeAttach_downloadHandlerIsUsed() {
        UI.setCurrent(client1.ui);
        client1.group.setImageHandler(user -> DownloadHandler
                .forClassResource(getClass(), user.getImage(), user.getName()));
        client1.attach();
        client2.attach();

        List<AvatarGroupItem> items = client1.getItems();
        assertEquals(2, items.size());
        AvatarGroupItem item = items.get(1);

        Assert.assertThat(item.getImage(),
                CoreMatchers.startsWith("VAADIN/dynamic"));
        assertEquals("name2", item.getImageResource().getName());
    }

    @Test
    public void imageHandler_afterAttach_downloadHandlerIsUsed() {
        UI.setCurrent(client1.ui);
        client1.attach();
        client2.attach();

        client1.group.setImageHandler(user -> DownloadHandler
                .forClassResource(getClass(), user.getImage(), user.getName()));

        List<AvatarGroupItem> items = client1.getItems();
        assertEquals(2, items.size());
        AvatarGroupItem item = items.get(1);

        Assert.assertThat(item.getImage(),
                CoreMatchers.startsWith("VAADIN/dynamic"));
        assertEquals("name2", item.getImageResource().getName());
    }

    @Test
    public void imageHandler_nullHandler_noImage() {
        UI.setCurrent(client1.ui);
        client1.attach();
        client2.attach();

        client1.group.setImageHandler(user -> null);

        List<AvatarGroupItem> items = client1.getItems();
        assertEquals(2, items.size());
        AvatarGroupItem item = items.get(1);

        Assert.assertNull(item.getImage());
        Assert.assertNull(item.getImageResource());
    }

    @Test
    public void imageHandler_clearHandler_imageIsSetFromUserInfo() {
        UI.setCurrent(client1.ui);
        client1.group.setImageHandler(user -> DownloadHandler
                .forClassResource(getClass(), user.getImage(), user.getName()));
        client1.attach();
        client2.attach();

        client1.group.setImageHandler(null);

        List<AvatarGroupItem> items = client1.getItems();
        assertEquals(2, items.size());
        AvatarGroupItem item = items.get(1);

        Assert.assertNull(item.getImageResource());
        assertEquals("image2", item.getImage());
    }

    @Test
    public void setOwnAvatarVisibleFalse_ownAvatarNotIncluded() {
        client1.group.setOwnAvatarVisible(false);
        client2.group.setOwnAvatarVisible(false);

        client1.attach();
        assertEquals(Collections.emptySet(), client1.getItemNames());

        client2.attach();
        assertEquals(TestUtils.newHashSet("name2"), client1.getItemNames());
        assertEquals(TestUtils.newHashSet("name1"), client2.getItemNames());
    }

    @Test
    public void attachGroup_toggleOwnAvatarVisible_avatarsUpdated() {
        client1.attach();
        client2.attach();

        client1.group.setOwnAvatarVisible(false);
        assertEquals(TestUtils.newHashSet("name2"), client1.getItemNames());

        client1.group.setOwnAvatarVisible(true);
        assertEquals(TestUtils.newHashSet("name1", "name2"),
                client1.getItemNames());
    }

    @Test
    public void attachGroup_orderPreserved() {
        client1.attach();
        clientInMultipleTabs1.attach();
        client2.attach();
        client1.getItemNames();
        assertItemsInOrder(client1, "name1", "name5", "name2");
        clientInMultipleTabs2.attach();
        assertItemsInOrder(client1, "name1", "name5", "name2");
        clientInMultipleTabs1.detach();
        assertItemsInOrder(client1, "name1", "name5", "name2");
        clientInMultipleTabs2.detach();
        assertItemsInOrder(client1, "name1", "name2");
    }

    @Test
    public void createOwnAvatar_userInfoAreCorrect() {
        UserInfo user = new UserInfo("john", "John", "someUrl");
        CollaborationAvatarGroup group = new CollaborationAvatarGroup(user,
                "topic");
        Avatar avatar = group.createOwnAvatar();

        assertEquals("John", avatar.getName());
        assertEquals("someUrl", avatar.getImage());
    }

    @Test
    public void serializeAvatarGroup() {
        CollaborationAvatarGroup avatarGroup = client1.group;

        CollaborationAvatarGroup deserializedAvatarGroup = TestUtils
                .serialize(avatarGroup);
    }

    private void assertItemsInOrder(AvatarGroupTestClient client,
            String... expectedItemNames) {
        Assert.assertArrayEquals(expectedItemNames,
                client.getItemNamesStream().toArray(String[]::new));
    }
}
