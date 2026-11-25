/*
 * Copyright 2000-2026 Vaadin Ltd.
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

import com.vaadin.flow.js.JsDefinition;
import com.vaadin.flow.js.JsExpression;
import com.vaadin.flow.js.JsInvokers;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The client-side JavaScript of {@link FieldHighlighter}, declared as a
 * {@link JsDefinition} so that it is compiled into the frontend bundle and runs
 * under a content security policy that does not allow <code>unsafe-eval</code>.
 * The field element is <code>this</code>.
 *
 * @author Vaadin Ltd
 */
@JsDefinition
interface FieldHighlighterJs extends JsInvokers {

    /**
     * Replaces the users highlighted on the field.
     *
     * @param users
     *            the users to show
     */
    @JsExpression("customElements.get('vaadin-field-highlighter').setUsers(this, $0)")
    void setUsers(ArrayNode users);

    /**
     * Adds a user highlighted on the field.
     *
     * @param user
     *            the user to add
     */
    @JsExpression("customElements.get('vaadin-field-highlighter').addUser(this, $0)")
    void addUser(ObjectNode user);

    /**
     * Removes a user highlighted on the field.
     *
     * @param user
     *            the user to remove
     */
    @JsExpression("customElements.get('vaadin-field-highlighter').removeUser(this, $0)")
    void removeUser(ObjectNode user);
}
