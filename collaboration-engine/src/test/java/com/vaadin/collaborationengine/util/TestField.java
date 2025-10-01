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
package com.vaadin.collaborationengine.util;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.Assert;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.Focusable;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.internal.nodefeature.ElementListenerMap;

import tools.jackson.databind.node.JsonNodeFactory;

@Tag("test-field")
public class TestField extends AbstractField<TestField, String>
        implements Focusable<TestField>, HasValidation {

    private String errorMessage;

    private boolean invalid;

    public TestField() {
        super(null);
    }

    @Override
    protected void setPresentationValue(String newPresentationValue) {
        // NO-OP
    }

    @Override
    public String getEmptyValue() {
        return "empty value";
    }

    public void showHighlight() {
        fireEvent("vaadin-highlight-show");
    }

    public void hideHighlight() {
        fireEvent("vaadin-highlight-hide");
    }

    private void fireEvent(String eventName) {
        DomEvent event = new DomEvent(getElement(), eventName,
                JsonNodeFactory.instance.objectNode());
        getElement().getNode().getFeature(ElementListenerMap.class)
                .fireEvent(event);
    }

    @Override
    public boolean hasListener(Class<? extends ComponentEvent> eventType) {
        return super.hasListener(eventType);
    }

    public boolean hasFieldHighlightShowListener() {
        return hasElementListener("vaadin-highlight-show");
    }

    public boolean hasFieldHighlightHideListener() {
        return hasElementListener("vaadin-highlight-hide");
    }

    private boolean hasElementListener(String eventName) {
        ElementListenerMap elementListenerMap = getElement().getNode()
                .getFeature(ElementListenerMap.class);
        try {
            Field listenersField = elementListenerMap.getClass()
                    .getDeclaredField("listeners");
            listenersField.setAccessible(true);
            Map<String, ?> listeners = (Map<String, ?>) listenersField
                    .get(elementListenerMap);

            return listeners != null && listeners.containsKey(eventName);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            Assert.fail(
                    "Failed to get element's event listeners with reflection");
        }
        return false;
    }

    @Override
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public void setInvalid(boolean invalid) {
        this.invalid = invalid;
    }

    @Override
    public boolean isInvalid() {
        return invalid;
    }
}
