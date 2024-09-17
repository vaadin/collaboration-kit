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

import org.junit.Assert;
import org.junit.Test;

import com.vaadin.collaborationengine.util.AbstractCollaborativeFormTestCommon;

public class ConnectionActivationTestCommon
        extends AbstractCollaborativeFormTestCommon {
    @Test
    public void preserveOnRefresh_fieldValuesPreserved_fieldIsCollaborative()
            throws Exception {
        client1.textField.setValue("foo");
        client1.emailField.setValue("bar");

        refresh();
        client1 = new ClientState(this); // use the new session
        Assert.assertEquals("foo", client1.textField.getValue());
        Assert.assertEquals("bar", client1.emailField.getValue());

        ClientState client2 = new ClientState(addClient());
        client2.textField.setValue("baz");

        refresh();
        client1 = new ClientState(this);
        Assert.assertEquals("CE should still work as usual.", "baz",
                client1.textField.getValue());
    }
}
