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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;

import com.vaadin.collaborationengine.util.MockService;
import com.vaadin.collaborationengine.util.MockUI;
import com.vaadin.collaborationengine.util.TestBean;
import com.vaadin.collaborationengine.util.TestField;
import com.vaadin.collaborationengine.util.TestLocalDateField;
import com.vaadin.collaborationengine.util.TestUtils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.function.SerializableSupplier;
import com.vaadin.flow.server.VaadinService;

import tools.jackson.databind.JsonNode;

public class AbstractCollaborationBinderTest {

    public static class BinderTestClient {
        UserInfo user = new UserInfo(UUID.randomUUID().toString());
        TestField field = new TestField();
        TestLocalDateField localDateField = new TestLocalDateField();

        CollaborationBinder<TestBean> binder;
        private final UI ui;

        public BinderTestClient(
                SerializableSupplier<CollaborationEngine> ceSupplier) {
            this.ui = new MockUI();
            binder = new CollaborationBinder<>(TestBean.class, user,
                    ceSupplier);
            binder.setTopic("topic", () -> null);
        }

        public Binder.Binding<TestBean, String> bind() {
            return binder.forField(field).bind("value");
        }

        public Binder.Binding<TestBean, LocalDate> bindLocalDate() {
            return binder.forField(localDateField).bind("localDate");
        }

        public void attach() {
            attach(field);
        }

        public void attach(Component child) {
            ui.add(child);
        }

        public void detach() {
            ui.remove(field);
            ui.remove(localDateField);
        }
    }

    protected VaadinService service;

    protected BinderTestClient client;
    protected BinderTestClient client2;
    protected TestField field;
    protected TopicConnection topicConnection;
    protected CollaborationMap map;

    @Before
    public void init() {
        service = new MockService();
        VaadinService.setCurrent(service);
        CollaborationEngine ce = TestUtil
                .createTestCollaborationEngine(service);
        SerializableSupplier<CollaborationEngine> ceSupplier = () -> ce;

        client = new BinderTestClient(ceSupplier);
        field = client.field;

        client2 = new BinderTestClient(ceSupplier);

        TestUtils.openEagerConnection(ce, "topic", topicConnection -> {
            this.topicConnection = topicConnection;
            map = CollaborationBinderUtil.getMap(topicConnection);
        });
    }

    @After
    public void cleanUp() {
        VaadinService.setCurrent(null);
    }

    protected void setSharedValue(String key, Object value) {
        CollaborationBinderUtil.setFieldValue(topicConnection, key, value);
    }

    protected <T> T getSharedValue(String key, Class<T> type) {
        return JsonUtil.toInstance(getFieldValue(key), type);
    }

    protected JsonNode getFieldValue(String key) {
        return CollaborationBinderUtil.getFieldValue(topicConnection, key);
    }

    protected List<UserInfo> getEditors(String key) {
        return CollaborationBinderUtil.getList(topicConnection)
                .getItems(FormManager.FocusedEditor.class).stream()
                .filter(f -> f.propertyName.equals(key))
                .map(focusedEditor -> focusedEditor.user).distinct()
                .collect(Collectors.toList());
    }

}
