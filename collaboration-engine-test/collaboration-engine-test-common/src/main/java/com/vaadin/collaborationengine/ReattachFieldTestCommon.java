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

import org.junit.Test;

import com.vaadin.collaborationengine.util.AbstractCollaborativeFormTestCommon;

public class ReattachFieldTestCommon
        extends AbstractCollaborativeFormTestCommon {

    @Test
    public void detachTextFields_attachTextFields_collaborationWorks()
            throws Exception {
        ClientState client2 = new ClientState(addClient());

        client1.detachTextField();
        client2.detachTextField();

        client1.attachTextField();
        client2.attachTextField();

        client1.focusTextField();

        // Fails in Team-City Linux but not in other linux like gitpod with
        // google-chrome
        if (!System.getProperty("os.name").toLowerCase().matches(".*linux.*")) {
            assertUserTags(client2.textField, "User 1");
        }

        client1.textField.setValue("foo");
        // Value should be propagated to the other client
        waitUntil(driver -> "foo".equals(client2.textField.getValue()), 3);

        client1.blur();
        assertNoUserTags(client2.textField);
    }

}
