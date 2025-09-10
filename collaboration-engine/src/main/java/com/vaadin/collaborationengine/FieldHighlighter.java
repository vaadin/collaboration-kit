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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.collaborationengine.FormManager.FocusedEditor;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.fieldhighlighter.FieldHighlighterInitializer;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.shared.Registration;

/**
 * @author Vaadin Ltd
 * @since 1.0
 */
class FieldHighlighter extends FieldHighlighterInitializer
        implements Serializable {

    static Registration setupForField(HasValue<?, ?> field, String propertyName,
            CollaborationBinder<?> binder) {

        List<Registration> registrations = new ArrayList<>();

        if (field instanceof HasElement) {
            Element element = ((HasElement) field).getElement();
            registrations.add(init(element));

            registrations.add(
                    element.addEventListener("vaadin-highlight-show", e -> {

                        JsonNode eventData = e.getEventData();
                        JsonNode eventDetail = eventData != null
                                ? eventData.at("/event/detail")
                                : null;
                        int fieldIndex = (eventDetail != null
                                && eventDetail.has("fieldIndex"))
                                        ? eventDetail.get("fieldIndex").asInt()
                                        : 0;

                        binder.addEditor(propertyName, fieldIndex);

                    }).addEventData("event.detail"));

            registrations.add(element.addEventListener("vaadin-highlight-hide",
                    e -> binder.removeEditor(propertyName)));

            registrations.add(() -> binder.removeEditor(propertyName));
        }
        return () -> registrations.forEach(Registration::remove);
    }

    private final SerializableFunction<UserInfo, Integer> colorIndexProvider;

    FieldHighlighter(
            SerializableFunction<UserInfo, Integer> colorIndexProvider) {
        this.colorIndexProvider = colorIndexProvider;
    }

    void setEditors(HasValue<?, ?> field, List<FocusedEditor> editors,
            UserInfo localUser) {
        if (field instanceof HasElement hasElement) {
            hasElement.getElement().executeJs(
                    "customElements.get('vaadin-field-highlighter').setUsers(this, $0)",
                    serialize(editors.stream()
                            .filter(editor -> !editor.user.equals(localUser))));
        }
    }

    void removeEditors(HasValue<?, ?> field) {
        setEditors(field, Collections.emptyList(), null);
    }

    void addEditor(HasValue<?, ?> field, UserInfo user, int fieldIndex) {
        if (field instanceof HasElement hasElement) {
            hasElement.getElement().executeJs(
                    "customElements.get('vaadin-field-highlighter')"
                            + ".addUser(this, $0)",
                    serialize(user, fieldIndex));
        }
    }

    void removeEditor(HasValue<?, ?> field, UserInfo user, int fieldIndex) {
        if (field instanceof HasElement hasElement) {
            hasElement.getElement().executeJs(
                    "customElements.get('vaadin-field-highlighter')"
                            + ".removeUser(this, $0)",
                    serialize(user, fieldIndex));
        }
    }

    private ArrayNode serialize(Stream<FocusedEditor> editors) {
        var array = JsonNodeFactory.instance.arrayNode();
        editors.map(this::serialize).forEach(array::add);
        return array;
    }

    private ObjectNode serialize(FocusedEditor focusedEditor) {
        var editorJson = JsonNodeFactory.instance.objectNode();
        editorJson.put("id", focusedEditor.user.getId());
        editorJson.put("name",
                Objects.toString(focusedEditor.user.getName(), ""));
        editorJson.put("colorIndex",
                colorIndexProvider.apply(focusedEditor.user));
        editorJson.put("fieldIndex", focusedEditor.fieldIndex);
        return editorJson;
    }

    private ObjectNode serialize(UserInfo user, int fieldIndex) {
        var editorJson = JsonNodeFactory.instance.objectNode();
        editorJson.put("id", user.getId());
        editorJson.put("name", Objects.toString(user.getName(), ""));
        editorJson.put("colorIndex", colorIndexProvider.apply(user));
        editorJson.put("fieldIndex", fieldIndex);
        return editorJson;
    }
}
