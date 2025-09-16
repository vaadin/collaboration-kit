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

import static com.vaadin.collaborationengine.MockJson.LIST_STRING_TYPE_REF;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vaadin.collaborationengine.util.MockConnectionContext;
import com.vaadin.collaborationengine.util.MockService;
import com.vaadin.collaborationengine.util.TestUtils;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.function.SerializableSupplier;
import com.vaadin.flow.server.VaadinService;

public class FormManagerTest {

    private static final String TOPIC_ID = "form";

    private UserInfo user = new UserInfo("foo");

    private SerializableSupplier<CollaborationEngine> ceSupplier;

    @Before
    public void init() {
        VaadinService service = new MockService();
        VaadinService.setCurrent(service);
        CollaborationEngine ce = TestUtil
                .createTestCollaborationEngine(service);
        ceSupplier = () -> ce;
    }

    @After
    public void cleanUp() {
        UI.setCurrent(null);
        VaadinService.setCurrent(null);
    }

    @Test
    public void setValue_getValueIsCorrect() {
        FormManager manager = createActiveManager();
        manager.setValue("foo", "bar");

        Assert.assertEquals("bar", manager.getValue("foo", String.class));
    }

    @Test
    public void setHandler_setValue_handlerInvoked() {
        Map<String, Object> entries = new HashMap<>();
        FormManager manager = createActiveManager();

        manager.setPropertyChangeHandler(event -> entries
                .put(event.getPropertyName(), event.getValue()));
        manager.setValue("foo", "bar");

        Assert.assertTrue(entries.containsKey("foo"));
        Assert.assertEquals("bar", entries.get("foo"));
    }

    @Test
    public void handler_getValue_typeIsCorrect() {
        Map<String, Object> entries = new HashMap<>();
        FormManager manager = createActiveManager();

        manager.setPropertyChangeHandler(event -> entries
                .put(event.getPropertyName(), event.getValue(UserInfo.class)));
        manager.setValue("foo", user);

        Assert.assertTrue(entries.containsKey("foo"));
        Assert.assertEquals(UserInfo.class, entries.get("foo").getClass());
        Assert.assertEquals(user, entries.get("foo"));
    }

    @Test
    public void handler_getValue_typeRefIsCorrect() {
        Map<String, Object> entries = new HashMap<>();
        FormManager manager = createActiveManager();

        manager.setPropertyChangeHandler(event -> entries.put(
                event.getPropertyName(), event.getValue(LIST_STRING_TYPE_REF)));
        List<String> list = new ArrayList<>();
        list.add("foo");
        list.add("bar");
        manager.setValue("foo", list);

        Assert.assertTrue(entries.containsKey("foo"));
        Assert.assertEquals(List.class,
                entries.get("foo").getClass().getInterfaces()[0]);
        Assert.assertEquals(list, entries.get("foo"));
    }

    @Test
    public void setValue_setHandler_handlerInvoked() {
        Map<String, Object> entries = new HashMap<>();
        FormManager manager = createActiveManager();

        manager.setValue("foo", "bar");
        manager.setPropertyChangeHandler(event -> entries
                .put(event.getPropertyName(), event.getValue()));

        Assert.assertTrue(entries.containsKey("foo"));
        Assert.assertEquals("bar", entries.get("foo"));
    }

    @Test
    public void highlight_propertyIsHighlighted() {
        FormManager manager = createActiveManager();
        manager.highlight("foo", true);

        Assert.assertTrue(manager.isHighlight("foo"));
    }

    @Test
    public void highlight_removeHighlight_propertyIsNotHighlighted() {
        FormManager manager = createActiveManager();
        manager.highlight("foo", true);
        manager.highlight("foo", false);

        Assert.assertFalse(manager.isHighlight("foo"));
    }

    @Test
    public void setHandler_highlight_handlerInvoked() {
        Set<String> highlights = new HashSet<>();
        FormManager manager = createActiveManager();

        manager.setHighlightHandler(event -> {
            highlights.add(event.getPropertyName());
            return () -> highlights.remove(event.getPropertyName());
        });
        manager.highlight("foo", true);

        Assert.assertTrue(highlights.contains("foo"));
    }

    @Test
    public void highlight_setHandler_handlerInvoked() {
        Set<String> highlights = new HashSet<>();
        FormManager manager = createActiveManager();

        manager.highlight("foo", true);
        manager.setHighlightHandler(event -> {
            highlights.add(event.getPropertyName());
            return () -> highlights.remove(event.getPropertyName());
        });

        Assert.assertTrue(highlights.contains("foo"));
    }

    @Test
    public void setHandler_removeHighlight_removeRegistrationInvoked() {
        Set<String> highlights = new HashSet<>();
        FormManager manager = createActiveManager();

        manager.setHighlightHandler(event -> {
            highlights.add(event.getPropertyName());
            return () -> highlights.remove(event.getPropertyName());
        });
        manager.highlight("foo", true);
        manager.highlight("foo", false);

        Assert.assertFalse(highlights.contains("foo"));
    }

    @Test
    public void setHighlight_deactivateContext_highlightCleared() {
        MockConnectionContext context = new MockConnectionContext();
        FormManager manager = createManager(context);

        context.activate();
        manager.highlight("foo", true);
        context.deactivate();

        Assert.assertFalse(manager.isHighlight("foo"));
    }

    @Test
    public void setHandler_deactivateContext_registrationRemoved() {
        MockConnectionContext context = new MockConnectionContext();
        FormManager manager = createManager(context);

        Set<String> highlights = new HashSet<>();

        context.activate();
        manager.setHighlightHandler(event -> {
            highlights.add(event.getPropertyName());
            return () -> highlights.remove(event.getPropertyName());
        });

        manager.highlight("foo", true);
        Assert.assertTrue(highlights.contains("foo"));

        context.deactivate();

        Assert.assertFalse(highlights.contains("foo"));
    }

    @Test
    public void setValue_deactivateContext_valueCleared() {
        MockConnectionContext context = new MockConnectionContext();
        FormManager manager = createManager(context);

        context.activate();
        manager.setValue("foo", "bar");
        context.deactivate();

        Assert.assertNull(manager.getValue("foo", String.class));
    }

    @Test
    public void openTopicConnection_setExpirationTimeout_timeoutIsCorrect() {
        MockConnectionContext context = new MockConnectionContext();
        FormManager manager = createManager(context);

        manager.setExpirationTimeout(Duration.ofMinutes(15));

        long timeout = manager.getExpirationTimeout().get().toMinutes();
        Assert.assertEquals(15, timeout);
    }

    @Test
    public void setExpirationTimeout_openTopicConnection_timeoutIsCorrect() {
        FormManager manager = createActiveManager();
        manager.setExpirationTimeout(Duration.ofMinutes(15));

        AtomicLong timeout = new AtomicLong();
        ceSupplier.get().openTopicConnection(
                MockConnectionContext.createEager(), TOPIC_ID, user,
                connection -> {
                    timeout.set(
                            connection.getNamedMap(FormManager.COLLECTION_NAME)
                                    .getExpirationTimeout().get().toMinutes());
                    return null;
                });
        Assert.assertEquals(15, timeout.longValue());
    }

    @Test
    public void serializeFormManager() {
        FormManager manager = createActiveManager();

        FormManager deserializedManager = TestUtils.serialize(manager);
    }

    private FormManager createActiveManager() {
        return createManager(MockConnectionContext.createEager());
    }

    private FormManager createManager(ConnectionContext context) {
        return new FormManager(context, user, TOPIC_ID, ceSupplier);
    }
}
