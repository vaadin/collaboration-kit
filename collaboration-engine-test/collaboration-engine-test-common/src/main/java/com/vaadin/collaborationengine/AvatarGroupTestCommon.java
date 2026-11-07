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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;

import com.vaadin.collaborationengine.util.AbstractCollaborativeFormTestCommon;
import com.vaadin.collaborationengine.util.UserTagElement;
import com.vaadin.flow.component.avatar.testbench.AvatarElement;

public class AvatarGroupTestCommon extends AbstractCollaborativeFormTestCommon {

    @Test
    public void openAndCloseClients_avatarsUpdated() throws Exception {
        assertAvatarNames(
                "Expected only own avatar when only one client connected",
                newHashSet("User 1"), client1);

        ClientState client2 = new ClientState(addClient());

        String message = "When another client has joined, expected both to have two avatars";
        Set<String> expected = newHashSet("User 1", "User 2");
        assertAvatarNames(message, expected, client1);
        assertAvatarNames(message, expected, client2);

        ClientState client3 = new ClientState(addClient());

        message = "When three clients joined, expected to see the avatars of the other two";
        expected = newHashSet("User 1", "User 2", "User 3");
        assertAvatarNames(message, expected, client1);
        assertAvatarNames(message, expected, client2);
        assertAvatarNames(message, expected, client3);

        close(client2.client);

        message = "When one of the three clients closed the window, expected one avatar to remain visible for the other two";
        expected = newHashSet("User 1", "User 3");
        assertAvatarNames(message, expected, client1);
        assertAvatarNames(message, expected, client3);
    }

    @Test
    public void avatarAndFieldHighlightHaveSameColorIndex() throws Exception {
        ClientState client2 = new ClientState(addClient());
        client1.focusTextField();

        // Look up the tag and avatar by name instead of by position, so the
        // check does not depend on which other users are in the topic.
        // Remote driver needs a while until tags are there
        UserTagElement userTag = waitUntil(d -> getUserTags(client2.textField)
                .stream().filter(tag -> "User 1".equals(tag.getName()))
                .findFirst().orElse(null), 3);

        Integer fieldColorIndex = userTag.getColorIndex();
        Integer avatarColorIndex = client2.avatars.$(AvatarElement.class).all()
                .stream()
                .filter(avatar -> "User 1"
                        .equals(avatar.getPropertyString("name")))
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError("Avatar of User 1 not found"))
                .getPropertyInteger("colorIndex");

        Assert.assertNotNull(fieldColorIndex);
        Assert.assertEquals(fieldColorIndex, avatarColorIndex);
    }

    /**
     * Presence changes reach the clients asynchronously. For example, a closed
     * browser stays in the topic until the server processes its disconnection,
     * so wait for the expected avatars before asserting.
     */
    private void assertAvatarNames(String message, Set<String> expected,
            ClientState client) {
        try {
            waitUntil(d -> {
                try {
                    return expected.equals(client.getAvatarNames());
                } catch (StaleElementReferenceException e) {
                    return false;
                }
            }, 10);
        } catch (TimeoutException e) {
            // Fall through to the assertion for a descriptive failure
        }
        Assert.assertEquals(message, expected, client.getAvatarNames());
    }

    private <E> Set<E> newHashSet(E... items) {
        return new HashSet<>(Arrays.asList(items));
    }
}
