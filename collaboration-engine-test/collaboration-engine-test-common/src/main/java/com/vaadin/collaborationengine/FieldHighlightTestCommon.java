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

import static org.junit.Assume.assumeFalse;

import org.junit.Assert;
import org.junit.Test;

import com.vaadin.collaborationengine.util.AbstractCollaborativeFormTestCommon;
import com.vaadin.collaborationengine.util.FieldOutlineElement;
import com.vaadin.flow.component.radiobutton.testbench.RadioButtonElement;

public class FieldHighlightTestCommon
        extends AbstractCollaborativeFormTestCommon {

    @Test
    public void noInitialUserTags() throws Exception {
        assertNoUserTags(client1.textField, client1.checkbox);
    }

    @Test
    public void focusFields_userTagsForLocalUserNotDisplayed()
            throws Exception {
        client1.focusTextField();
        assertNoUserTags(client1.textField, client1.checkbox);

        client1.focusCheckbox();
        assertNoUserTags(client1.textField, client1.checkbox);
    }

    @Test
    public void focusField_addClient_newClientHasUserTag() throws Exception {
        client1.focusTextField();
        ClientState client2 = new ClientState(addClient());
        assertUserTags(client2.textField, "User 1");
        assertNoUserTags(client1.textField, client1.checkbox, client2.checkbox);
    }

    @Test
    public void addClient_focusAndBlurFields_userTagsUpdated()
            throws Exception {
        ClientState client2 = new ClientState(addClient());

        client1.focusTextField();
        client2.focusCheckbox();

        assertUserTags(client2.textField, "User 1");
        assertUserTags(client1.checkbox, "User 2");
        assertNoUserTags(client1.textField, client2.checkbox);

        client1.focusCheckbox();

        assertUserTags(client2.checkbox, "User 1");
        assertUserTags(client1.checkbox, "User 2");
        assertNoUserTags(client1.textField, client2.textField);

        client2.blur();

        assertUserTags(client2.checkbox, "User 1");
        assertNoUserTags(client1.textField, client1.checkbox,
                client2.textField);
    }

    @Test
    public void threeClients_focusSameField_twoUserTags() throws Exception {
        assumeFalse("Fails in Selenium Hub", isHub);

        ClientState client2 = new ClientState(addClient());
        ClientState client3 = new ClientState(addClient());

        client1.focusTextField();
        client2.focusTextField();
        client3.focusTextField();

        assertUserTags(client1.textField, "User 3", "User 2");
        assertUserTags(client2.textField, "User 3", "User 1");
        assertUserTags(client3.textField, "User 2", "User 1");

        client3.blur();

        assertUserTags(client1.textField, "User 2");
        assertUserTags(client2.textField, "User 1");
        assertUserTags(client3.textField, "User 2", "User 1");

    }

    @Test
    public void closeBrowser_tagRemoved() throws Exception {
        ClientState client2 = new ClientState(addClient());
        client2.focusTextField();
        close(client2.client);

        assertNoUserTags(client1.textField);
    }

    @Test
    public void focusRadioButtonsInsideGroup_individualButtonsHighlighted()
            throws Exception {
        ClientState client2 = new ClientState(addClient());

        // No highlight initially
        assertNoUserTags(client1.radioButtonGroup, client2.radioButtonGroup);
        assertRadioButtonHighlight(client1, null, null, null);
        assertRadioButtonHighlight(client2, null, null, null);

        // Client focuses radio button
        client2.focusRadioButton(1);

        assertUserTags(client1.radioButtonGroup, "User 2");
        assertNoUserTags(client2.radioButtonGroup);

        assertRadioButtonHighlight(client1, null, 2, null);
        assertRadioButtonHighlight(client2, null, null, null);

        // Two clients focus different radio buttons in a group
        ClientState client3 = new ClientState(addClient());
        client3.focusRadioButton(2);

        assertUserTags(client1.radioButtonGroup, "User 3", "User 2");
        assertUserTags(client2.radioButtonGroup, "User 3");
        assertUserTags(client3.radioButtonGroup, "User 2");

        assertRadioButtonHighlight(client1, null, 2, 3);
        assertRadioButtonHighlight(client2, null, null, 3);
        assertRadioButtonHighlight(client3, null, 2, null);

        // Client blurs radio button
        client3.blur();

        assertUserTags(client1.radioButtonGroup, "User 2");
        assertNoUserTags(client2.radioButtonGroup);
        assertUserTags(client3.radioButtonGroup, "User 2");

        assertRadioButtonHighlight(client1, null, 2, null);
        assertRadioButtonHighlight(client2, null, null, null);
        assertRadioButtonHighlight(client3, null, 2, null);

        // Two clients focus the same radio button
        client3.focusRadioButton(1);

        assertUserTags(client1.radioButtonGroup, "User 3", "User 2");
        assertUserTags(client2.radioButtonGroup, "User 3");
        assertUserTags(client3.radioButtonGroup, "User 2");

        assertRadioButtonHighlight(client1, null, 3, null);
        assertRadioButtonHighlight(client2, null, 3, null);
        assertRadioButtonHighlight(client3, null, 2, null);
    }

    @Test
    public void clientClearItsBinder_itsFieldsHaveNoHighlighters()
            throws Exception {
        ClientState client2 = new ClientState(addClient());
        client2.focusTextField();
        assertUserTags(client1.textField, "User 2");

        client1.clearBinder();
        assertNoUserTags(client1.textField);

        client1.rebind();
        assertUserTags(client1.textField, "User 2");

        client2.focusCheckbox();
        assertNoUserTags(client1.textField);
        assertUserTags(client1.checkbox, "User 2");
    }

    private void assertRadioButtonHighlight(ClientState client,
            Integer... expectedColorIndices) {
        int index = 0;
        for (RadioButtonElement radioButton : client.radioButtons) {
            FieldOutlineElement outline = radioButton
                    .$(FieldOutlineElement.class).first();
            Integer colorIndex = outline.getColorIndex();
            Assert.assertEquals("Radio button had unexpected color index",
                    expectedColorIndices[index++], colorIndex);
        }
    }
}
