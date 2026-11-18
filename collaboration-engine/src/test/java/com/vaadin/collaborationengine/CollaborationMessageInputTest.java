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
import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vaadin.collaborationengine.util.MockService;
import com.vaadin.collaborationengine.util.MockUI;
import com.vaadin.collaborationengine.util.ReflectionUtils;
import com.vaadin.collaborationengine.util.TestUtils;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.function.SerializableSupplier;
import com.vaadin.flow.server.VaadinService;

public class CollaborationMessageInputTest {

    private static final String TOPIC_ID = "topic";

    public static class MessageInputTestClient {
        SerializableSupplier<CollaborationEngine> ceSupplier;
        final UI ui;
        final UserInfo user;
        CollaborationMessageList messageList;
        CollaborationMessageInput messageInput;
        String topicId = null;

        MessageInputTestClient(int index,
                SerializableSupplier<CollaborationEngine> ceSupplier) {
            this(index, TOPIC_ID, ceSupplier);
        }

        MessageInputTestClient(int index, String topicId,
                SerializableSupplier<CollaborationEngine> ceSupplier) {
            this.ceSupplier = ceSupplier;
            this.ui = new MockUI();
            this.user = new UserInfo("id" + index, "name" + index,
                    "image" + index);
            user.setAbbreviation("abbreviation" + index);
            user.setColorIndex(index);
            messageList = new CollaborationMessageList(this.user, null, null,
                    ceSupplier);
            messageInput = new CollaborationMessageInput(messageList);

        }

        private List<MessageListItem> getMessages() {
            return messageList.getContent().getItems();
        }

        void attach() {
            ui.add(messageList, messageInput);
        }

        void setTopic(String topicId) {
            this.topicId = topicId;
            messageList.setTopic(topicId);
        }

        void submitMessage(String message) {
            MessageInput.SubmitEvent submitEvent = new MessageInput.SubmitEvent(
                    messageInput.getContent(), true, message);
            ComponentUtil.fireEvent(messageInput.getContent(), submitEvent);
        }

        boolean isEnabled() {
            return messageInput.getContent().isEnabled();
        }
    }

    private MessageInputTestClient client1;

    @Before
    public void init() {
        VaadinService service = new MockService();
        VaadinService.setCurrent(service);
        CollaborationEngine ce = TestUtil
                .createTestCollaborationEngine(service);
        client1 = new MessageInputTestClient(1, () -> ce);
    }

    @After
    public void cleanUp() {
        UI.setCurrent(null);
        VaadinService.setCurrent(null);
    }

    @Test
    public void sendMessage_messageAppearsInTopic() {
        client1.attach();
        client1.setTopic(TOPIC_ID);
        Assert.assertTrue(client1.getMessages().isEmpty());
        client1.submitMessage("new message");
        List<MessageListItem> messages = client1.getMessages();
        assertEquals(1, messages.size());
        MessageListItem message = messages.get(0);
        assertEquals("new message", message.getText());
        assertEquals("name1", message.getUserName());
        assertEquals("image1", message.getUserImage());
        assertEquals("abbreviation1", message.getUserAbbreviation());
        assertEquals(1, message.getUserColorIndex().intValue());
    }

    @Test
    public void initialState_componentDisabled() {
        client1.attach();
        Assert.assertFalse(client1.isEnabled());
    }

    @Test
    public void setTopicOnList_componentEnabled() {
        client1.attach();
        client1.setTopic("foo");
        Assert.assertTrue(client1.isEnabled());
    }

    @Test
    public void clearTopicOnList_componentDisabled() {
        client1.attach();
        client1.setTopic("foo");
        client1.setTopic(null);
        Assert.assertFalse(client1.isEnabled());
    }

    @Test
    public void setSubmitDisabled_delegatesToMessageInput() {
        CollaborationMessageInput messageInput = client1.messageInput;
        Assert.assertFalse(messageInput.isSubmitDisabled());

        messageInput.setSubmitDisabled(true);
        Assert.assertTrue(messageInput.isSubmitDisabled());
        Assert.assertTrue(messageInput.getContent().isSubmitDisabled());

        messageInput.setSubmitDisabled(false);
        Assert.assertFalse(messageInput.getContent().isSubmitDisabled());
    }

    private static List<String> blackListedMethods = Arrays
            .asList("addSubmitListener", "isEnabled", "setEnabled");

    @Test
    public void messageInput_replicateRelevantAPIs() {
        List<String> messageInputMethods = ReflectionUtils
                .getMethodNames(MessageInput.class);
        List<String> collaborationMessageInputMethods = ReflectionUtils
                .getMethodNames(CollaborationMessageInput.class);

        List<String> missingMethods = messageInputMethods.stream()
                .filter(m -> !blackListedMethods.contains(m)
                        && !collaborationMessageInputMethods.contains(m))
                .collect(Collectors.toList());

        if (!missingMethods.isEmpty()) {
            Assert.fail("Missing wrapper for methods: "
                    + missingMethods.toString());
        }
    }

    @Test
    public void serializeMessageInput() {
        CollaborationMessageInput messageInput = client1.messageInput;

        CollaborationMessageInput deserializedMessageInput = TestUtils
                .serialize(messageInput);
    }
}
