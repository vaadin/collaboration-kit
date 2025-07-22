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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.vaadin.collaborationengine.CollaborationAvatarGroup.ImageHandler;
import com.vaadin.collaborationengine.CollaborationAvatarGroup.ImageProvider;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.function.SerializableSupplier;
import com.vaadin.flow.internal.UsageStatistics;
import com.vaadin.flow.shared.Registration;

/**
 * Extension of the {@link MessageList} component which integrates with the
 * {@link CollaborationEngine}. It reads the messages from a topic and renders
 * them within the component. The list is automatically updated when new
 * messages are available. You can use the {@link CollaborationMessageInput}
 * component for submitting messages.
 *
 * @author Vaadin Ltd
 * @since 3.1
 */
public class CollaborationMessageList extends Composite<MessageList>
        implements HasSize, HasStyle {

    /**
     * Configurator callback for messages in a {@link CollaborationMessageList}.
     * It can be used for customizing the properties of the
     * {@link MessageListItem} objects after the component has generated them,
     * before sending those to the user's browser.
     *
     * @see CollaborationMessageList#setMessageConfigurator(MessageConfigurator)
     */
    @FunctionalInterface
    public interface MessageConfigurator {
        /**
         * Configures the provided message after the
         * {@link CollaborationMessageList} has generated it. The configuration
         * should be done by mutating the {@code message} parameter.
         *
         * @param message
         *            the message to configure
         * @param user
         *            the sender of the message
         */
        void configureMessage(MessageListItem message, UserInfo user);
    }

    private final SerializableSupplier<CollaborationEngine> ceSupplier;

    private ImageProvider imageProvider;

    private ImageHandler imageHandler;

    private final UserInfo localUser;

    static {
        UsageStatistics.markAsUsed(
                CollaborationEngine.COLLABORATION_ENGINE_NAME
                        + "/CollaborationMessageList",
                CollaborationEngine.COLLABORATION_ENGINE_VERSION);
    }

    private CollaborationMessageSubmitter submitter;
    private Registration submitterRegistration;

    private MessageConfigurator messageConfigurator;

    private MessageManager messageManager;

    private final CollaborationMessagePersister persister;

    private final List<CollaborationMessage> messageCache = new ArrayList<>();

    /**
     * Creates a new collaboration message list component with the provided
     * topic id.
     * <p>
     * It renders messages received by a {@link CollaborationMessageInput} or a
     * custom submitter component connected to this list via
     * {@link #setSubmitter(CollaborationMessageSubmitter)}
     * <p>
     * If a {@code null} topic id is provided, the component won't display any
     * messages, until connecting to a non-null topic with
     * {@link #setTopic(String)}.
     *
     * @param localUser
     *            the information of the end user, not {@code null}
     * @param topicId
     *            the id of the topic to connect to, or <code>null</code> to not
     *            connect the component to any topic
     */
    public CollaborationMessageList(UserInfo localUser, String topicId) {
        this(localUser, topicId, null, CollaborationEngine::getInstance);
    }

    /**
     * Creates a new collaboration message list component with the provided
     * topic id and persister of {@link CollaborationMessage} items from/to an
     * external source (e.g. a database).
     * <p>
     * It renders messages received by a {@link CollaborationMessageInput} or a
     * custom submitter component connected to this list via
     * {@link #setSubmitter(CollaborationMessageSubmitter)}
     * <p>
     * If a {@code null} topic id is provided, the component won't display any
     * messages, until connecting to a non-null topic with
     * {@link #setTopic(String)}.
     *
     * @param localUser
     *            the information of the end user, not {@code null}
     * @param topicId
     *            the id of the topic to connect to, or <code>null</code> to not
     *            connect the component to any topic
     * @param persister
     *            the persister to read/write messages to an external source
     */
    public CollaborationMessageList(UserInfo localUser, String topicId,
            CollaborationMessagePersister persister) {
        this(localUser, topicId, persister, CollaborationEngine::getInstance);
    }

    CollaborationMessageList(UserInfo localUser, String topicId,
            CollaborationMessagePersister persister,
            SerializableSupplier<CollaborationEngine> ceSupplier) {
        this.localUser = Objects.requireNonNull(localUser,
                "User cannot be null");
        this.ceSupplier = ceSupplier;
        this.persister = persister;
        setTopic(topicId);
    }

    /**
     * Sets the topic to use with this component. The connection to the previous
     * topic (if any) and existing messages are removed. A connection to the new
     * topic is opened and the list of messages in the new topic are populated
     * to this component.
     * <p>
     * If the topic id is {@code null}, no messages will be displayed.
     *
     * @param topicId
     *            the topic id to use, or <code>null</code> to not use any topic
     */
    public void setTopic(String topicId) {
        String currentTopic = messageManager != null
                ? messageManager.getTopicId()
                : null;
        if (Objects.equals(currentTopic, topicId)) {
            return;
        }

        if (messageManager != null) {
            messageManager.close();
            messageManager = null;
            unregisterSubmitter();
            messageCache.clear();
            refreshMessages();
        }

        if (topicId != null) {
            messageManager = new MessageManager(
                    new ComponentConnectionContext(this), localUser, topicId,
                    persister, ceSupplier);
            messageManager.setMessageHandler(context -> {
                CollaborationMessage message = context.getMessage();
                messageCache.add(message);
                refreshMessages();
            });
            messageManager.setActivationHandler(this::onManagerActivation);
        }
    }

    /**
     * Sets a submitter to handle the append of messages to the list. It can be
     * used to connect a custom input component as an alternative to the
     * provided {@link CollaborationMessageInput}. If set to {@code null} the
     * existing submitter will be disconnected from the list.
     *
     * @param submitter
     *            the submitter, or {@code null} to remove the current submitter
     */
    public void setSubmitter(CollaborationMessageSubmitter submitter) {
        unregisterSubmitter();
        this.submitter = submitter;
        if (messageManager != null) {
            messageManager.setActivationHandler(this::onManagerActivation);
        }
    }

    private Registration onManagerActivation() {
        registerSubmitter();
        return () -> {
            unregisterSubmitter();
            refreshMessages();
        };
    }

    private void registerSubmitter() {
        if (submitter != null) {
            submitterRegistration = submitter.onActivation(this::appendMessage);
            Objects.requireNonNull(submitterRegistration,
                    "The submitter should return a non-null registration object");
        }
    }

    private void unregisterSubmitter() {
        if (submitterRegistration != null) {
            submitterRegistration.remove();
            submitterRegistration = null;
        }
    }

    private CollaborationEngine getCollaborationEngine() {
        return ceSupplier.get();
    }

    /**
     * Appends a message to this list and all other lists connected to the same
     * topic. The message will be associated with the current local-user and the
     * current timestamp.
     *
     * @param text
     *            the content of the message, not {@code null}
     */
    void appendMessage(String text) {
        Objects.requireNonNull(text, "Cannot append a null message");

        CollaborationMessage message = new CollaborationMessage(localUser, text,
                getCollaborationEngine().getClock().instant());
        messageManager.submit(message);
    }

    /**
     * Sets an image provider callback for dynamically loading avatar images for
     * a given user. The image can be loaded on-demand from a database or using
     * any other source of IO streams.
     * <p>
     * If no image callback is defined, then the image URL defined by
     * {@link UserInfo#getImage()} is directly passed to the browser. This means
     * that avatar images need to be available as static files or served
     * dynamically from a custom servlet. This is the default.
     * <p>
     *
     * Usage example:
     *
     * <pre>
     * collaborationMessageList.setImageProvider(userInfo -> {
     *     StreamResource streamResource = new StreamResource(
     *             "avatar_" + userInfo.getId(), () -> {
     *                 User userEntity = userRepository
     *                         .findById(userInfo.getId());
     *                 byte[] profilePicture = userEntity.getProfilePicture();
     *                 return new ByteArrayInputStream(profilePicture);
     *             });
     *     streamResource.setContentType("image/png");
     *     return streamResource;
     * });
     * </pre>
     *
     * @param imageProvider
     *            the image provider to use, or <code>null</code> to use image
     *            URLs directly from the user info object
     * @deprecated Use {@link #setImageHandler(ImageHandler)} instead.
     */
    @Deprecated(since = "3.1.0", forRemoval = true)
    public void setImageProvider(ImageProvider imageProvider) {
        this.imageProvider = imageProvider;
        refreshMessages();
    }

    /**
     * Gets the currently used image provider callback.
     *
     * @see #setImageProvider(ImageProvider)
     *
     * @return the current image provider callback, or <code>null</code> if no
     *         callback is set
     * @deprecated Use {@link #setImageHandler(ImageHandler)} instead.
     */
    @Deprecated(since = "3.1.0", forRemoval = true)
    public ImageProvider getImageProvider() {
        return imageProvider;
    }

    /**
     * Sets an image handler callback for dynamically loading avatar images for
     * a given user. The image can be loaded on-demand from a database or using
     * any other source of IO streams.
     * <p>
     * If no image handler is defined, then the image URL defined by
     * {@link UserInfo#getImage()} is directly passed to the browser. This means
     * that avatar images need to be available as static files or served
     * dynamically from a custom servlet. This is the default.
     *
     * Usage example:
     *
     * <pre>
     * collaborationMessageList.setImageHandler(userInfo -> {
     *     DownloadHandler downloadHandler = DownloadHandler
     *             .fromInputStream(context -> {
     *                 context.setFileName("avatar_" + userInfo.getId());
     *                 context.setContentType("image/png");
     *                 User userEntity = userRepository
     *                         .findById(userInfo.getId());
     *                 byte[] profilePicture = userEntity.getProfilePicture();
     *                 return new ByteArrayInputStream(profilePicture);
     *             });
     *     return downloadHandler;
     * });
     * </pre>
     *
     * @param imageHandler
     *            the image handler to use, or <code>null</code> to use image
     *            URLs directly from the user info object
     * @since 3.1.0
     */
    public void setImageHandler(ImageHandler imageHandler) {
        this.imageHandler = imageHandler;
        refreshMessages();
    }

    /**
     * Gets the currently used image handler callback.
     *
     * @see #setImageHandler(ImageHandler)
     *
     * @return the current image handler callback, or <code>null</code> if no
     *         callback is set
     * @since 3.1.0
     */
    public ImageHandler getImageHandler() {
        return imageHandler;
    }

    /**
     * Sets a configurator callback for the messages. It can be used for
     * customizing the properties of the {@link MessageListItem} objects after
     * the component has generated them, before sending them to the user's
     * browser.
     * <p>
     *
     * Usage example:
     *
     * <pre>
     * messageList.setMessageConfigurator((message, user) -> {
     *     message.setUserName(user.getName().toUpperCase());
     * });
     * </pre>
     *
     * @param messageConfigurator
     *            the configurator to set, or {@code null} to remove the current
     *            configurator
     */
    public void setMessageConfigurator(
            MessageConfigurator messageConfigurator) {
        this.messageConfigurator = messageConfigurator;
        refreshMessages();
    }

    /**
     * Gets the current message configurator, if any.
     *
     * @return the current message configurator, or {@code null} if none has
     *         been set
     * @see #setMessageConfigurator(MessageConfigurator)
     */
    public MessageConfigurator getMessageConfigurator() {
        return messageConfigurator;
    }

    /**
     * Wrapper method for {@link MessageList#setMarkdown(boolean)}. Sets whether
     * the messages should be parsed as markdown. By default this is set to
     * {@code false}.
     *
     * @param markdown
     *            {@code true} if the message text is parsed as Markdown.
     */
    public void setMarkdown(boolean markdown) {
        this.getContent().setMarkdown(markdown);
    }

    /**
     * Wrapper method for {@link MessageList#isMarkdown()}. Returns whether the
     * messages are parsed as markdown.
     *
     * @return {@code true} if the message text is parsed as Markdown.
     */
    public boolean isMarkdown() {
        return this.getContent().isMarkdown();
    }

    /**
     * Wrapper method for {@link MessageList#setAnnounceMessages(boolean)}. When
     * set to {@code true}, new messages are announced to assistive technologies
     * using ARIA live regions. By default, this is set to {@code false}.
     *
     * @param announceMessages
     *            {@code true} if new messages should be announced to assistive
     *            technologies.
     */
    public void setAnnounceMessages(boolean announceMessages) {
        this.getContent().setAnnounceMessages(announceMessages);
    }

    /**
     * Wrapper method for {@link MessageList#isAnnounceMessages()}. Returns
     * whether new messages are announced to assistive technologies.
     *
     * @return {@code true} if new messages are announced to assistive
     *         technologies.
     */
    public boolean isAnnounceMessages() {
        return this.getContent().isAnnounceMessages();
    }

    private void refreshMessages() {
        List<MessageListItem> messageListItems = messageCache.stream()
                .map(this::convertToMessageListItem)
                .collect(Collectors.toList());
        getContent().setItems(messageListItems);
    }

    private MessageListItem convertToMessageListItem(
            CollaborationMessage message) {
        MessageListItem messageListItem = new MessageListItem(message.getText(),
                message.getTime(), message.getUser().getName());

        if (imageHandler != null) {
            messageListItem.setUserImageHandler(
                    imageHandler.getDownloadHandler(message.getUser()));
        } else if (imageProvider != null) {
            messageListItem.setUserImageResource(
                    imageProvider.getImageResource(message.getUser()));
        } else {
            messageListItem.setUserImage(message.getUser().getImage());
        }
        messageListItem
                .setUserAbbreviation(message.getUser().getAbbreviation());
        messageListItem.setUserColorIndex(
                getCollaborationEngine().getUserColorIndex(message.getUser()));

        if (messageConfigurator != null) {
            messageConfigurator.configureMessage(messageListItem,
                    message.getUser());
        }

        return messageListItem;
    }
}
