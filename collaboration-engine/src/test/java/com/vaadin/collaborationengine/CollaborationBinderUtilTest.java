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

import static com.vaadin.collaborationengine.MockJson.LIST_BIG_DECIMAL_TYPE_REF;
import static com.vaadin.collaborationengine.MockJson.LIST_BOOLEAN_TYPE_REF;
import static com.vaadin.collaborationengine.MockJson.LIST_DOUBLE_TYPE_REF;
import static com.vaadin.collaborationengine.MockJson.LIST_INTEGER_TYPE_REF;
import static com.vaadin.collaborationengine.MockJson.LIST_LOCAL_DATE_TIME_TYPE_REF;
import static com.vaadin.collaborationengine.MockJson.LIST_LOCAL_DATE_TYPE_REF;
import static com.vaadin.collaborationengine.MockJson.LIST_LOCAL_TIME_TYPE_REF;
import static com.vaadin.collaborationengine.MockJson.LIST_STRING_TYPE_REF;
import static com.vaadin.collaborationengine.MockJson.LIST_TEST_ENUM_TYPE_REF;
import static com.vaadin.collaborationengine.MockJson.SET_BIG_DECIMAL_TYPE_REF;
import static com.vaadin.collaborationengine.MockJson.SET_BOOLEAN_TYPE_REF;
import static com.vaadin.collaborationengine.MockJson.SET_DOUBLE_TYPE_REF;
import static com.vaadin.collaborationengine.MockJson.SET_INTEGER_TYPE_REF;
import static com.vaadin.collaborationengine.MockJson.SET_LOCAL_DATE_TIME_TYPE_REF;
import static com.vaadin.collaborationengine.MockJson.SET_LOCAL_DATE_TYPE_REF;
import static com.vaadin.collaborationengine.MockJson.SET_LOCAL_TIME_TYPE_REF;
import static com.vaadin.collaborationengine.MockJson.SET_STRING_TYPE_REF;
import static com.vaadin.collaborationengine.MockJson.SET_TEST_ENUM_TYPE_REF;
import static com.vaadin.collaborationengine.util.TestUtils.assertNullNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiConsumer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vaadin.collaborationengine.util.TestEnum;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

public class CollaborationBinderUtilTest
        extends AbstractCollaborationBinderTest {

    @Before
    public void init2() {
        client.bind();
        client.attach();
        field.showHighlight();
    }

    @Test
    public void setFieldValue_fieldUpdated() {
        CollaborationBinderUtil.setFieldValue(topicConnection, "value",
                MockJson.FOO);

        Assert.assertEquals("foo", field.getValue());
    }

    @Test
    public void setFieldValue_nullValue_convertedToNullNode() {
        assertNullNode("", CollaborationBinderUtil
                .getFieldValue(topicConnection, "nonExistingProperty"));

        CollaborationBinderUtil.setFieldValue(topicConnection, "value", null);
        assertNullNode("", CollaborationBinderUtil
                .getFieldValue(topicConnection, "value"));
    }

    @Test
    public void addEditors_editorsUpdated() {
        CollaborationBinderUtil.addEditor(topicConnection, "value",
                new UserInfo("1"));
        CollaborationBinderUtil.addEditor(topicConnection, "value",
                new UserInfo("2"));

        Assert.assertEquals(Arrays.asList(client.user, new UserInfo("1"),
                new UserInfo("2")), getEditors("value"));
    }

    @Test
    public void addDuplicateEditor_noDuplicates() {
        String id = "987";

        CollaborationBinderUtil.addEditor(topicConnection, "value",
                new UserInfo(id));
        CollaborationBinderUtil.addEditor(topicConnection, "value",
                new UserInfo(id));

        Assert.assertEquals(Arrays.asList(client.user, new UserInfo(id)),
                getEditors("value"));
    }

    @Test
    public void removeEditor_editorRemoved() {
        String id = "46";

        CollaborationBinderUtil.addEditor(topicConnection, "value",
                new UserInfo(id));
        CollaborationBinderUtil.removeEditor(topicConnection, "value",
                new UserInfo(id));

        Assert.assertEquals(Arrays.asList(client.user), getEditors("value"));
    }

    @Test
    public void removeNonExistingEditor_doesNothing() {
        CollaborationBinderUtil.removeEditor(topicConnection, "value",
                new UserInfo("not an editor"));
        Assert.assertEquals(Arrays.asList(client.user), getEditors("value"));
    }

    @Test
    public void setFieldValue_worksWithSupportedValueTypes() {
        BiConsumer<Object, Class<?>> test = (value, type) -> {
            CollaborationBinderUtil.setFieldValue(topicConnection, "propName",
                    JsonUtil.toJsonNode(value));
            JsonNode fieldValue = CollaborationBinderUtil
                    .getFieldValue(topicConnection, "propName");
            Assert.assertEquals(value, JsonUtil.toInstance(fieldValue, type));
        };

        test.accept("foo", String.class);
        test.accept(true, Boolean.class);
        test.accept(1, Integer.class);
        test.accept(1.5, Double.class);
        test.accept(new BigDecimal(2.5), BigDecimal.class);
        test.accept(TestEnum.FOO, TestEnum.class);

        test.accept(LocalDate.of(2020, 2, 3), LocalDate.class);
        test.accept(LocalTime.of(1, 2, 3, 4), LocalTime.class);
        test.accept(LocalDateTime.of(2020, 2, 3, 4, 5, 6, 7),
                LocalDateTime.class);
    }

    @Test
    public void setFieldValue_worksWithCollectionOfSupportedValueTypes() {
        BiConsumer<Object, TypeReference<?>> test = (value, typeRef) -> {
            CollaborationBinderUtil.setFieldValue(topicConnection, "propName",
                    JsonUtil.toJsonNode(value));
            JsonNode fieldValue = CollaborationBinderUtil
                    .getFieldValue(topicConnection, "propName");
            Assert.assertEquals(value,
                    JsonUtil.toInstance(fieldValue, typeRef));
        };

        List<BigDecimal> bigDecimalList = Arrays.asList(new BigDecimal("2.5"),
                new BigDecimal("3.4"));
        List<LocalDate> localDateList = Arrays.asList(LocalDate.of(2020, 2, 3),
                LocalDate.of(2021, 12, 31));
        List<LocalTime> localTimeList = Arrays.asList(LocalTime.of(1, 2, 3, 4),
                LocalTime.of(2, 3, 4, 5));
        List<LocalDateTime> localDateTimeList = Arrays.asList(
                LocalDateTime.of(2020, 2, 3, 4, 5, 6, 7),
                LocalDateTime.of(2021, 3, 4, 5, 6, 7, 8));
        List<TestEnum> enumList = Arrays.asList(TestEnum.FOO, TestEnum.BAR);

        test.accept(Arrays.asList("foo", "bar"), LIST_STRING_TYPE_REF);
        test.accept(Arrays.asList(true, false), LIST_BOOLEAN_TYPE_REF);
        test.accept(Arrays.asList(2, 3), LIST_INTEGER_TYPE_REF);
        test.accept(Arrays.asList(1.2, 2.3), LIST_DOUBLE_TYPE_REF);

        test.accept(bigDecimalList, LIST_BIG_DECIMAL_TYPE_REF);
        test.accept(localDateList, LIST_LOCAL_DATE_TYPE_REF);
        test.accept(localTimeList, LIST_LOCAL_TIME_TYPE_REF);
        test.accept(localDateTimeList, LIST_LOCAL_DATE_TIME_TYPE_REF);
        test.accept(enumList, LIST_TEST_ENUM_TYPE_REF);

        test.accept(new HashSet(Arrays.asList("foo", "bar")),
                SET_STRING_TYPE_REF);
        test.accept(new HashSet(Arrays.asList(true, false)),
                SET_BOOLEAN_TYPE_REF);
        test.accept(new HashSet(Arrays.asList(2, 3)), SET_INTEGER_TYPE_REF);
        test.accept(new HashSet(Arrays.asList(1.2, 2.3)), SET_DOUBLE_TYPE_REF);

        test.accept(new HashSet(bigDecimalList), SET_BIG_DECIMAL_TYPE_REF);
        test.accept(new HashSet(localDateList), SET_LOCAL_DATE_TYPE_REF);
        test.accept(new HashSet(localTimeList), SET_LOCAL_TIME_TYPE_REF);
        test.accept(new HashSet(localDateTimeList),
                SET_LOCAL_DATE_TIME_TYPE_REF);
        test.accept(new HashSet(enumList), SET_TEST_ENUM_TYPE_REF);
    }

}
