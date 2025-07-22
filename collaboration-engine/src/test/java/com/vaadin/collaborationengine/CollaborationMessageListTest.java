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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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
import com.vaadin.collaborationengine.util.TestDownloadHandler;
import com.vaadin.collaborationengine.util.TestStreamResource;
import com.vaadin.collaborationengine.util.TestUtils;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.function.SerializableSupplier;
import com.vaadin.flow.server.VaadinService;

public class CollaborationMessageListTest {

    private static final String TOPIC_ID = "topic";

    public static class MessageListTestClient {
        final UI ui;
        final UserInfo user;
        CollaborationMessageList messageList;
        SerializableSupplier<CollaborationEngine> ceSupplier;
        String topicId = null;

        MessageListTestClient(int index,
                SerializableSupplier<CollaborationEngine> ceSupplier) {
            this(index, TOPIC_ID, null, ceSupplier);
        }

        MessageListTestClient(int index, String topicId,
                CollaborationMessagePersister persister,
                SerializableSupplier<CollaborationEngine> ceSupplier) {
            this.ceSupplier = ceSupplier;
            this.ui = new MockUI();
            this.user = new UserInfo("id" + index, "name" + index,
                    "image" + index);
            user.setAbbreviation("abbreviation" + index);
            user.setColorIndex(index);
            messageList = new CollaborationMessageList(user, topicId, persister,
                    ceSupplier);
        }

        private List<MessageListItem> getMessages() {
            return messageList.getContent().getItems();
        }

        void attach() {
            ui.add(messageList);
        }

        void setTopic(String topicId) {
            this.topicId = topicId;
            messageList.setTopic(this.topicId);
        }

        public void sendMessage(String content) {
            messageList.appendMessage(content);
        }

        CollaborationEngine getCollaborationEngine() {
            return ceSupplier.get();
        }
    }

    private SerializableSupplier<CollaborationEngine> ceSupplier;

    private MessageListTestClient client1;
    private MessageListTestClient client2;
    private MessageListTestClient client3;

    private Map<String, List<CollaborationMessage>> backend;
    private CollaborationMessagePersister persister;

    @Before
    public void init() {
        VaadinService service = new MockService();
        VaadinService.setCurrent(service);
        CollaborationEngine ce = TestUtil
                .createTestCollaborationEngine(service);
        ceSupplier = () -> ce;
        client1 = new MessageListTestClient(1, ceSupplier);
        client2 = new MessageListTestClient(2, ceSupplier);

        backend = new HashMap<>();
        persister = CollaborationMessagePersister.fromCallbacks(
                query -> backend
                        .computeIfAbsent(
                                query.getTopicId(), t -> new ArrayList<>())
                        .stream()
                        .filter(message -> message.getTime()
                                .compareTo(query.getSince()) >= 0),
                event -> backend
                        .computeIfAbsent(event.getTopicId(),
                                t -> new ArrayList<>())
                        .add(event.getMessage()));

        client3 = new MessageListTestClient(3, TOPIC_ID, persister, ceSupplier);
    }

    @After
    public void cleanUp() {
        backend.clear();
        UI.setCurrent(null);
        VaadinService.setCurrent(null);
    }

    @Test
    public void sendMessage_messageAppearsInMessageList() {
        client1.attach();
        client1.setTopic(TOPIC_ID);
        Assert.assertEquals(Collections.emptyList(), client1.getMessages());
        client1.getCollaborationEngine().setClock(
                Clock.fixed(Instant.ofEpochSecond(10), ZoneOffset.UTC));
        client1.sendMessage("new message");
        List<MessageListItem> messages = client1.getMessages();
        Assert.assertEquals(1, messages.size());
        MessageListItem message = messages.get(0);
        Assert.assertEquals("new message", message.getText());
        Assert.assertEquals(Instant.ofEpochSecond(10), message.getTime());
        Assert.assertEquals("name1", message.getUserName());
        Assert.assertEquals("abbreviation1", message.getUserAbbreviation());
        Assert.assertEquals("image1", message.getUserImage());
        Assert.assertEquals(Integer.valueOf(1), message.getUserColorIndex());
    }

    @Test
    public void noExplicitColorIndex_colorIndexProvidedByCollaborationEngine() {
        client1.user.setColorIndex(-1);
        client1.attach();
        client1.setTopic(TOPIC_ID);
        client1.sendMessage("new message");
        MessageListItem message = client1.getMessages().get(0);
        Assert.assertEquals(Integer.valueOf(0), message.getUserColorIndex());
    }

    @Test
    public void sendMessage_messagePropagatesToOtherUser() {
        client1.attach();
        client2.attach();
        client1.setTopic(TOPIC_ID);
        client2.setTopic(TOPIC_ID);
        Assert.assertEquals(Collections.emptyList(), client1.getMessages());
        Assert.assertEquals(Collections.emptyList(), client2.getMessages());
        client1.sendMessage("new message");
        Assert.assertEquals(1, client1.getMessages().size());
        Assert.assertEquals(1, client2.getMessages().size());
        MessageListItem message = client2.getMessages().get(0);
        Assert.assertEquals("new message", message.getText());
        Assert.assertEquals("name1", message.getUserName());
    }

    @Test
    public void joinTopic_existingMessagesAreDisplayed() {
        client1.attach();
        client1.setTopic(TOPIC_ID);
        Assert.assertEquals(Collections.emptyList(), client1.getMessages());
        client1.sendMessage("new message");
        Assert.assertEquals(1, client1.getMessages().size());
        Assert.assertEquals(0, client2.getMessages().size());
        client2.attach();
        client2.setTopic(TOPIC_ID);
        Assert.assertEquals(1, client2.getMessages().size());
        MessageListItem message = client2.getMessages().get(0);
        Assert.assertEquals("new message", message.getText());
        Assert.assertEquals("name1", message.getUserName());
    }

    @Test
    public void topicSetToNull_contentIsCleared() {
        client1.attach();
        client1.setTopic(TOPIC_ID);
        client1.sendMessage("new message");
        Assert.assertEquals(1, client1.getMessages().size());
        client1.setTopic(null);
        Assert.assertEquals(0, client1.getMessages().size());
    }

    private static List<String> blackListedMethods = Arrays.asList("getItems",
            "setItems", "addItem", "localeChange");

    @Test
    public void messageList_replicateRelevantAPIs() {
        List<String> messageListMethods = ReflectionUtils
                .getMethodNames(MessageList.class);
        List<String> collaborationMessageListMethods = ReflectionUtils
                .getMethodNames(CollaborationMessageList.class);

        List<String> missingMethods = messageListMethods.stream()
                .filter(m -> !blackListedMethods.contains(m)
                        && !collaborationMessageListMethods.contains(m))
                .collect(Collectors.toList());

        if (!missingMethods.isEmpty()) {
            Assert.fail("Missing wrapper for methods: "
                    + missingMethods.toString());
        }
    }

    @Test(expected = NullPointerException.class)
    public void setSubmitterBeforeTopic_nullRegistration_throws() {
        client1.messageList.setSubmitter(activationContext -> null);
        client1.attach();
        client1.setTopic(TOPIC_ID);
    }

    @Test(expected = NullPointerException.class)
    public void setSubmitterAfterTopic_nullRegistration_throws() {
        client1.attach();
        client1.setTopic(TOPIC_ID);
        client1.messageList.setSubmitter(activationContext -> null);
    }

    @Test
    public void setSubmitterBeforeTopic_submitterActivated() {
        AtomicBoolean submitterActivated = new AtomicBoolean();
        client1.messageList.setSubmitter(activationContext -> {
            submitterActivated.set(true);
            return () -> {
            };
        });
        client1.attach();
        client1.setTopic(TOPIC_ID);
        Assert.assertTrue(submitterActivated.get());
    }

    @Test
    public void setSubmitterAfterTopic_submitterActivated() {
        AtomicBoolean submitterActivated = new AtomicBoolean();
        client1.attach();
        client1.setTopic(TOPIC_ID);
        client1.messageList.setSubmitter(activationContext -> {
            submitterActivated.set(true);
            return () -> {
            };
        });
        Assert.assertTrue(submitterActivated.get());
    }

    @Test
    public void setSubmitterBeforeTopic_clearTopic_registrationRemoved() {
        AtomicBoolean registration = new AtomicBoolean();
        client1.messageList.setSubmitter(
                activationContext -> () -> registration.set(true));
        client1.attach();
        client1.setTopic(TOPIC_ID);
        client1.setTopic(null);
        Assert.assertTrue(registration.get());
    }

    @Test
    public void setSubmitterAfterTopic_clearTopic_registrationRemoved() {
        client1.attach();
        client1.setTopic(TOPIC_ID);
        AtomicBoolean registration = new AtomicBoolean();
        client1.messageList.setSubmitter(
                activationContext -> () -> registration.set(true));
        client1.setTopic(null);
        Assert.assertTrue(registration.get());
    }

    @Test
    public void replaceSubmitter_existingRegistrationRemoved() {
        client1.attach();
        client1.setTopic(TOPIC_ID);
        AtomicBoolean registration = new AtomicBoolean();
        client1.messageList.setSubmitter(
                activationContext -> () -> registration.set(true));
        client1.messageList.setSubmitter(activationContext -> () -> {
        });
        Assert.assertTrue(registration.get());
    }

    @Test
    public void nullSubmitter_existingRegistrationRemoved() {
        client1.attach();
        client1.setTopic(TOPIC_ID);
        AtomicBoolean registration = new AtomicBoolean();
        client1.messageList.setSubmitter(
                activationContext -> () -> registration.set(true));
        client1.messageList.setSubmitter(null);
        Assert.assertTrue(registration.get());
    }

    @Test
    public void withPersister_attach_messagesAreReadFromBackend() {
        addMessageToBackend(TOPIC_ID, client3.user, "foo", Instant.now());
        client3.attach();
        Assert.assertEquals(1, client3.getMessages().size());
    }

    @Test
    public void withPersister_appendMessage_messagesAreWrittenToBackend() {
        Instant time = Instant.now();
        client3.getCollaborationEngine()
                .setClock(Clock.fixed(time, ZoneOffset.UTC));
        client3.attach();
        client3.messageList.appendMessage("foo");
        CollaborationMessage message = backend.get(TOPIC_ID).get(0);

        // Assert we pass all the correct info to the persister
        Assert.assertEquals("foo", message.getText());
        Assert.assertEquals(client3.user, message.getUser());
        Assert.assertEquals(time, message.getTime());
    }

    @Test
    public void withPersister_fetchPersistedList_onlyNewMessagesAreAppended() {
        addMessageToBackend(TOPIC_ID, client3.user, "foo", Instant.now());

        client3.attach();
        client3.messageList.appendMessage("bar");

        Assert.assertEquals(2, client3.getMessages().size());
    }

    @Test
    public void withPersister_fetchPersistedList_duplicateMessagesRemoved() {
        Instant timestamp = Instant.now();
        addMessageToBackend(TOPIC_ID, client3.user, "foo", timestamp);
        addMessageToBackend(TOPIC_ID, client3.user, "bar", timestamp);
        client3.attach();
        Assert.assertEquals(2, client3.getMessages().size());
    }

    @Test
    public void withPersister_fetchPersistedList_messagesAreSorted() {
        Instant timestamp = Instant.now();
        Instant beforeTimestamp = timestamp.minusSeconds(60);
        addMessageToBackend(TOPIC_ID, client3.user, "bar", timestamp);
        addMessageToBackend(TOPIC_ID, client3.user, "foo", beforeTimestamp);
        client3.attach();
        List<MessageListItem> messages = client3.getMessages();
        String shouldBeFoo = messages.get(0).getText();
        String shouldBeBar = messages.get(1).getText();
        Assert.assertEquals("foo", shouldBeFoo);
        Assert.assertEquals("bar", shouldBeBar);
    }

    @Test(expected = IllegalStateException.class)
    public void withPersister_lastMessageNotFetched_throws() {
        MessageListTestClient client = new MessageListTestClient(1, TOPIC_ID,
                CollaborationMessagePersister.fromCallbacks(
                        query -> backend
                                .computeIfAbsent(query
                                        .getTopicId(), t -> new ArrayList<>())
                                .stream()
                                .filter(message -> message.getTime()
                                        .compareTo(query.getSince()) > 0),
                        event -> backend
                                .computeIfAbsent(event.getTopicId(),
                                        t -> new ArrayList<>())
                                .add(event.getMessage())),
                ceSupplier);
        addMessageToBackend(TOPIC_ID, client.user, "foo", Instant.now());
        client.attach();
        client.messageList.appendMessage("foo");
    }

    @Test(expected = IllegalStateException.class)
    public void persisterReturnsNonEmptyStream_gettersNotCalled_throws() {
        CollaborationMessagePersister persister = CollaborationMessagePersister
                .fromCallbacks(query -> Stream.of(new CollaborationMessage()),
                        event -> {
                        });
        new MessageListTestClient(0, TOPIC_ID, persister, ceSupplier).attach();
    }

    @Test(expected = IllegalStateException.class)
    public void persisterReturnsNonEmptyStream_getSinceNotCalled_throws() {
        CollaborationMessagePersister persister = CollaborationMessagePersister
                .fromCallbacks(query -> {
                    query.getTopicId();
                    return Stream.of(new CollaborationMessage());
                }, event -> {
                });
        new MessageListTestClient(0, TOPIC_ID, persister, ceSupplier).attach();
    }

    @Test(expected = IllegalStateException.class)
    public void persisterReturnsNonEmptyStream_getTopicIdNotCalled_throws() {
        CollaborationMessagePersister persister = CollaborationMessagePersister
                .fromCallbacks(query -> {
                    query.getSince();
                    return Stream.of(new CollaborationMessage());
                }, event -> {
                });
        new MessageListTestClient(0, TOPIC_ID, persister, ceSupplier).attach();
    }

    @Test
    public void persisterReturnsEmptyStream_gettersNotCalled_doesntThrow() {
        CollaborationMessagePersister persister = CollaborationMessagePersister
                .fromCallbacks(query -> Stream.empty(), event -> {
                });
        new MessageListTestClient(0, TOPIC_ID, persister, ceSupplier).attach();
    }

    private void addMessageToBackend(String topicId, UserInfo user, String text,
            Instant time) {
        backend.computeIfAbsent(topicId, t -> new ArrayList<>())
                .add(new CollaborationMessage(user, text, time));
    }

    @Test
    public void imageProvider_beforeAttach_streamResourceIsUsed() {
        UI.setCurrent(client1.ui);
        client1.messageList.setImageProvider(
                user -> new TestStreamResource(user.getName()));
        client1.setTopic(TOPIC_ID);
        client2.setTopic(TOPIC_ID);
        client1.attach();
        client2.attach();

        client2.sendMessage("foo");

        List<MessageListItem> items = client1.getMessages();

        Assert.assertEquals(1, items.size());
        MessageListItem item = items.get(0);

        Assert.assertThat(item.getUserImage(),
                CoreMatchers.startsWith("VAADIN/dynamic"));
        Assert.assertEquals("name2", item.getUserImageResource().getName());
    }

    @Test
    public void imageProvider_afterAttach_streamResourceIsUsed() {
        UI.setCurrent(client1.ui);
        client1.setTopic(TOPIC_ID);
        client2.setTopic(TOPIC_ID);
        client1.attach();
        client2.attach();

        client1.messageList.setImageProvider(
                user -> new TestStreamResource(user.getName()));

        client2.sendMessage("foo");

        List<MessageListItem> items = client1.getMessages();

        Assert.assertEquals(1, items.size());
        MessageListItem item = items.get(0);

        Assert.assertThat(item.getUserImage(),
                CoreMatchers.startsWith("VAADIN/dynamic"));
        Assert.assertEquals("name2", item.getUserImageResource().getName());
    }

    @Test
    public void imageProvider_nullStream_noImage() {
        UI.setCurrent(client1.ui);
        client1.setTopic(TOPIC_ID);
        client2.setTopic(TOPIC_ID);
        client1.attach();
        client2.attach();

        client1.messageList.setImageProvider(user -> null);

        client2.sendMessage("foo");

        List<MessageListItem> items = client1.getMessages();
        Assert.assertEquals(1, items.size());

        MessageListItem item = items.get(0);

        Assert.assertNull(item.getUserImage());
        Assert.assertNull(item.getUserImageResource());
    }

    @Test
    public void imageProvider_clearProvider_imageIsSetFromUserInfo() {
        UI.setCurrent(client1.ui);
        client1.messageList.setImageProvider(
                user -> new TestStreamResource(user.getName()));
        client1.setTopic(TOPIC_ID);
        client2.setTopic(TOPIC_ID);
        client1.attach();
        client2.attach();

        client2.sendMessage("foo");

        client1.messageList.setImageProvider(null);

        List<MessageListItem> items = client1.getMessages();
        Assert.assertEquals(1, items.size());
        MessageListItem item = items.get(0);

        Assert.assertNull(item.getUserImageResource());
        Assert.assertEquals("image2", item.getUserImage());
    }

    @Test
    public void imageHandler_beforeAttach_downloadHandlerIsUsed() {
        UI.setCurrent(client1.ui);
        client1.messageList.setImageHandler(user -> new TestDownloadHandler());
        client1.setTopic(TOPIC_ID);
        client2.setTopic(TOPIC_ID);
        client1.attach();
        client2.attach();

        client2.sendMessage("foo");

        List<MessageListItem> items = client1.getMessages();

        Assert.assertEquals(1, items.size());
        MessageListItem item = items.get(0);

        Assert.assertThat(item.getUserImage(),
                CoreMatchers.startsWith("VAADIN/dynamic"));
        Assert.assertEquals("", item.getUserImageResource().getName());
    }

    @Test
    public void imageHandler_afterAttach_downloadHandlerIsUsed() {
        UI.setCurrent(client1.ui);
        client1.setTopic(TOPIC_ID);
        client2.setTopic(TOPIC_ID);
        client1.attach();
        client2.attach();

        client1.messageList.setImageHandler(user -> new TestDownloadHandler());

        client2.sendMessage("foo");

        List<MessageListItem> items = client1.getMessages();

        Assert.assertEquals(1, items.size());
        MessageListItem item = items.get(0);

        Assert.assertThat(item.getUserImage(),
                CoreMatchers.startsWith("VAADIN/dynamic"));
        Assert.assertEquals("", item.getUserImageResource().getName());
    }

    @Test
    public void imageHandler_nullDownloadHandler_noImage() {
        UI.setCurrent(client1.ui);
        client1.setTopic(TOPIC_ID);
        client2.setTopic(TOPIC_ID);
        client1.attach();
        client2.attach();

        client1.messageList.setImageHandler(user -> null);

        client2.sendMessage("foo");

        List<MessageListItem> items = client1.getMessages();
        Assert.assertEquals(1, items.size());

        MessageListItem item = items.get(0);

        Assert.assertNull(item.getUserImage());
        Assert.assertNull(item.getUserImageResource());
    }

    @Test
    public void imageHandler_clearHandler_imageIsSetFromUserInfo() {
        UI.setCurrent(client1.ui);
        client1.messageList.setImageHandler(user -> new TestDownloadHandler());
        client1.setTopic(TOPIC_ID);
        client2.setTopic(TOPIC_ID);
        client1.attach();
        client2.attach();

        client2.sendMessage("foo");

        client1.messageList.setImageHandler(null);

        List<MessageListItem> items = client1.getMessages();
        Assert.assertEquals(1, items.size());
        MessageListItem item = items.get(0);

        Assert.assertNull(item.getUserImageResource());
        Assert.assertEquals("image2", item.getUserImage());
    }

    @Test
    public void setMessageConfigurator_sendMessages_messagesConfigured() {
        client1.messageList.setMessageConfigurator((message, user) -> {
            message.setText(user.getName() + ": " + message.getText());
        });

        client1.setTopic(TOPIC_ID);
        client2.setTopic(TOPIC_ID);
        client1.attach();
        client2.attach();

        client1.sendMessage("foo");
        client2.sendMessage("bar");

        Assert.assertEquals("name1: foo",
                client1.getMessages().get(0).getText());
        Assert.assertEquals("name2: bar",
                client1.getMessages().get(1).getText());
    }

    @Test
    public void sendMessages_setMessageConfigurator_messagesConfigured() {
        client1.setTopic(TOPIC_ID);
        client2.setTopic(TOPIC_ID);
        client1.attach();
        client2.attach();

        client1.sendMessage("foo");
        client2.sendMessage("bar");

        client1.messageList.setMessageConfigurator((message, user) -> {
            message.setText(user.getName() + ": " + message.getText());
        });

        Assert.assertEquals("name1: foo",
                client1.getMessages().get(0).getText());
        Assert.assertEquals("name2: bar",
                client1.getMessages().get(1).getText());
    }

    @Test
    public void serializeMessageList() {
        CollaborationMessageList messageList = client1.messageList;

        CollaborationMessageList deserializedMessageList = TestUtils
                .serialize(messageList);
    }

    @Test
    public void setMarkdownEnabled_markdownIsEnabled() {
        client1.messageList.setMarkdown(true);
        Assert.assertTrue(client1.messageList.isMarkdown());
        Assert.assertTrue(client1.messageList.getContent().isMarkdown());
    }

    @Test
    public void setAnnounceMessagesEnabled_announceMessagesIsEnabled() {
        client1.messageList.setAnnounceMessages(true);
        Assert.assertTrue(client1.messageList.isAnnounceMessages());
        Assert.assertTrue(
                client1.messageList.getContent().isAnnounceMessages());
    }
}
