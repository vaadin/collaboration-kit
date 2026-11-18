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

import static org.junit.Assert.assertTrue;

import java.io.Serializable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vaadin.collaborationengine.util.MockConnectionContext;
import com.vaadin.collaborationengine.util.MockService;
import com.vaadin.collaborationengine.util.MockUI;
import com.vaadin.collaborationengine.util.SpyActivationHandler;
import com.vaadin.collaborationengine.util.TestUtils;
import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.server.VaadinService;

public class ConnectionContextTest implements Serializable {

    private transient CollaborationEngine ce;
    private transient MockUI ui;
    private transient MockConnectionContext context;
    private transient SpyActivationHandler spy;
    private transient CollaborationMap map;

    @After
    public void cleanup() {
        CurrentInstance.clearAll();
    }

    @Before
    public void init() {
        VaadinService service = new MockService();
        VaadinService.setCurrent(service);
        ce = TestUtil.createTestCollaborationEngine();

        ui = new MockUI();
        context = MockConnectionContext.createEager();
        spy = new SpyActivationHandler();

        ce.openTopicConnection(context, "foo", SystemUserInfo.getInstance(),
                tc -> {
                    map = tc.getNamedMap("map");
                    map.subscribe(event -> {
                    });
                    return () -> {
                    };
                });
    }

    @Test
    public void subscribe_actionDispatchedThroughContext() {
        map.put("foo", "bar");
        assertTrue("Context should be passed through.",
                context.getDispatchActionCount() > 0);
    }

    @Test
    public void setTopicValue_actionDispatchedThroughContext() {
        context.resetActionDispatchCount();
        map.put("foo", "bar");
        assertTrue("Context should be passed through.",
                context.getDispatchActionCount() > 0);
    }

    @Test
    public void serializedContext() {
        ConnectionContext deserializedContext = TestUtils.serialize(context);
    }
}
