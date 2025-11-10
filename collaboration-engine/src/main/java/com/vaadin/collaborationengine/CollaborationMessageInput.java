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

import java.util.Objects;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.Focusable;
import com.vaadin.flow.component.HasEnabled;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageInputI18n;
import com.vaadin.flow.component.shared.HasTooltip;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.internal.UsageStatistics;
import com.vaadin.flow.shared.Registration;

/**
 * Extension of the {@link MessageInput} component which integrates with the
 * {@link CollaborationMessageList}. The user can type a message and submit it.
 * The messages will be displayed in any {@link CollaborationMessageList} that
 * is connected to the same topic as the list passed as the argument of this
 * component constructor. The text area and button will be disabled while the
 * connection to the topic is not active or the topic is set to
 * <code>null</code> (see {@link CollaborationMessageList#setTopic(String)}).
 *
 * @author Vaadin Ltd
 * @since 3.1
 */
public class CollaborationMessageInput extends Composite<MessageInput>
        implements Focusable<CollaborationMessageInput>, HasSize, HasStyle,
        HasEnabled, HasTooltip {

    static {
        UsageStatistics.markAsUsed(
                CollaborationEngine.COLLABORATION_ENGINE_NAME
                        + "/CollaborationMessageInput",
                CollaborationEngine.COLLABORATION_ENGINE_VERSION);
    }

    /**
     * Creates a new collaboration message input component which submits
     * messages to the provided {@link CollaborationMessageList}.
     *
     * @param list
     *            the list which will display the submitted messages, not null
     */
    public CollaborationMessageInput(CollaborationMessageList list) {
        Objects.requireNonNull(list,
                "A list instance to connect this component to is required");
        getContent().setEnabled(false);
        list.setSubmitter(activationContext -> {
            getContent().setEnabled(true);
            Registration registration = getContent().addSubmitListener(
                    event -> activationContext.appendMessage(event.getValue()));
            return () -> {
                registration.remove();
                getContent().setEnabled(false);
            };
        });
    }

    /**
     * Gets the internationalization object previously set for this component.
     * <p>
     * Note: updating the object content returned by this method will not update
     * the component if not set back using
     * {@link MessageInput#setI18n(MessageInputI18n)}.
     *
     * @return the i18n object, or {@code null} if one has not been set with
     *         {@link #setI18n(MessageInputI18n)}
     */
    public MessageInputI18n getI18n() {
        return getContent().getI18n();
    }

    /**
     * Sets the internationalization properties for this component. It enabled
     * you to customize and translate the language used in the message input.
     * <p>
     * Note: updating the object properties after setting the i18n will not
     * update the component. To make the changes effective, you need to set the
     * updated object again.
     *
     * @param i18n
     *            the i18n object, not {@code null}
     */
    public void setI18n(MessageInputI18n i18n) {
        getContent().setI18n(i18n);
    }

    /**
     * Sets a tooltip text for the component.
     *
     * @param text
     *            The tooltip text
     *
     * @return the tooltip handle
     */
    public Tooltip setTooltipText(String text) {
        return getContent().setTooltipText(text);
    }

    /**
     * Gets the tooltip handle of the component.
     *
     * @return the tooltip handle
     */
    public Tooltip getTooltip() {
        return getContent().getTooltip();
    }
}
