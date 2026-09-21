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
import org.openqa.selenium.By;

import com.vaadin.collaborationengine.util.AbstractCollaborativeViewTest;
import com.vaadin.testbench.TestBenchElement;

public class CollaborationAvatarInDialogTestCommon
        extends AbstractCollaborativeViewTest {
    @Override
    public String getRoute() {
        return "dialog";
    }

    @Test
    public void whenAvatarGroupIsDetachedThenUserIsRemovedFromTopic() {
        $("vaadin-button").id("open").click();
        TestBenchElement users = $("div").id("users");
        waitUntil(driver -> !users.findElements(By.tagName("div")).isEmpty(),
                3);

        // The dialog is closed through its own close button, as setting the
        // opened property on the client is no longer synchronized to the
        // server.
        $("vaadin-button").id("close").click();
        waitUntil(driver -> users.findElements(By.tagName("div")).isEmpty(), 3);
    }

}
