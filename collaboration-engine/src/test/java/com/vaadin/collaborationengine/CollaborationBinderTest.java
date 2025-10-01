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

import static com.vaadin.collaborationengine.util.TestUtils.assertNullNode;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

import com.vaadin.collaborationengine.util.GenericTestField;
import com.vaadin.collaborationengine.util.TestBean;
import com.vaadin.collaborationengine.util.TestField;
import com.vaadin.collaborationengine.util.TestUtils;
import com.vaadin.flow.component.AbstractField.ComponentValueChangeEvent;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.Result;
import com.vaadin.flow.data.binder.StatusChangeEvent;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.binder.Validator;
import com.vaadin.flow.data.binder.ValueContext;
import com.vaadin.flow.data.converter.Converter;
import com.vaadin.flow.data.validator.StringLengthValidator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.NullNode;

public class CollaborationBinderTest extends AbstractCollaborationBinderTest {

    @Test
    public void bind_setFieldValue_sharedValueNotUpdated() {
        client.bind();
        field.setValue("foo");
        assertNullNode(
                "Collaborative value shouldn't be updated in inactivated connection",
                getFieldValue("value"));
    }

    @Test
    public void bind_activate_setFieldValue_sharedValueUpdated() {
        client.bind();
        client.attach();
        field.setValue("foo");
        Assert.assertEquals("foo", getSharedValue("value", String.class));
    }

    @Test
    public void activate_bind_setFieldValue_sharedValueUpdated() {
        client.attach();
        client.bind();
        field.setValue("foo");
        Assert.assertEquals("foo", getSharedValue("value", String.class));
    }

    @Test
    public void bind_setSharedValue_fieldNotUpdated() {
        client.bind();
        setSharedValue("value", MockJson.FOO);
        Assert.assertNull(
                "Field shouldn't be updated in inactivated connection",
                field.getValue());
    }

    @Test
    public void bind_activate_setSharedValue_fieldUpdated() {
        client.bind();
        client.attach();
        setSharedValue("value", MockJson.FOO);
        Assert.assertEquals("foo", field.getValue());
    }

    @Test
    public void bind_activate_fieldHasEmptyValue() {
        client.bind();
        client.attach();
        Assert.assertEquals(field.getEmptyValue(), field.getValue());
    }

    @Test
    public void bind_activate_setSharedValueNull_fieldHasEmptyValue() {
        client.bind();
        client.attach();
        setSharedValue("value", MockJson.FOO);
        setSharedValue("value", NullNode.getInstance());
        Assert.assertEquals(field.getEmptyValue(), field.getValue());
    }

    @Test
    public void bind_activate_resetBean_sharedValueAndFieldUpdated() {
        client.bind();
        client.attach();
        client.binder.reset(new TestBean("foo"));
        Assert.assertEquals("foo", getSharedValue("value", String.class));
        Assert.assertEquals("foo", field.getValue());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void bindWithoutPropertyName_throws() {
        client.binder.bind(field, TestBean::getValue, TestBean::setValue);
    }

    @Test
    public void bind_unbind_listenersRemoved() {
        Binder.Binding<TestBean, String> binding = client.bind();
        Assert.assertTrue(
                "Expected field to have listeners. The test is invalid.",
                field.hasListener(ComponentValueChangeEvent.class)
                        && field.hasFieldHighlightShowListener()
                        && field.hasFieldHighlightHideListener());

        binding.unbind();

        Assert.assertFalse("All ValueChangeListeners should have been removed",
                field.hasListener(ComponentValueChangeEvent.class));
        Assert.assertFalse(
                "All listeners for showing highlight should have been removed",
                field.hasFieldHighlightShowListener());
        Assert.assertFalse(
                "All listeners for hiding highlight should have been removed",
                field.hasFieldHighlightHideListener());
    }

    @Test
    public void bind_unbind_changesNotPropagated() {
        Binder.Binding<TestBean, String> binding = client.bind();
        binding.unbind();

        field.setValue("foo");
        assertNullNode(
                "Map shouldn't have changed after setting the field value",
                getFieldValue("value"));

        setSharedValue("value", MockJson.BAZ);
        Assert.assertEquals(
                "Field value shouldn't have changed after updating the map",
                "foo", field.getValue());
    }

    @Test
    public void bind_unbind_editorRemoved() {
        Binder.Binding<TestBean, String> bind = client.bind();
        client.attach();
        field.showHighlight();

        bind.unbind();

        Assert.assertEquals(Collections.emptyList(), getEditors("value"));
    }

    @Test
    public void bind_deactivate_setFieldValue_sharedValueUpdated() {
        client.bind();
        client.attach();
        client.detach();

        field.setValue("bar");
        assertNullNode(
                "Binder which has a deactivated connection should not update the map",
                getFieldValue("value"));
    }

    @Test
    public void bind_activate_setValue_secondClientBinds_fieldGetsCurrentValue() {
        client.bind();
        client.attach();
        field.setValue("foo");

        client2.binder.forField(client2.field).bind("value");

        Assert.assertNull(
                "The second client should not receive value when in-activated",
                client2.field.getValue());

        client2.attach();
        Assert.assertEquals(
                "The second client should have the field value set by the first client",
                "foo", client2.field.getValue());
    }

    @Test
    public void twoActivatedClientsWithBinders_setFieldValueOnOne_otherUpdated() {
        client.bind();
        client.attach();

        client2.bind();
        client2.attach();

        client2.field.setValue("bar");
        Assert.assertEquals("bar", field.getValue());
    }

    @Test
    public void twoActivatedClientsWithBinders_setSharedValue_twoClientFieldsUpdated() {
        client.bind();
        client.attach();

        client2.bind();
        client2.attach();

        setSharedValue("value", "baz");

        Assert.assertEquals("baz", field.getValue());
        Assert.assertEquals("baz", client2.field.getValue());
    }

    @Test
    public void bind_active_topicHasNoEditors() {
        client.bind();
        client.attach();
        Assert.assertEquals(Collections.emptyList(), getEditors("value"));
    }

    @Test
    public void bind_activate_focusField_topicHasEditor() {
        client.bind();
        client.attach();
        field.showHighlight();
        Assert.assertEquals(Arrays.asList(client.binder.getLocalUser()),
                getEditors("value"));
    }

    @Test
    public void bind_activate_focusField_blurField_topicHasNoEditors() {
        client.bind();
        client.attach();
        field.showHighlight();
        field.hideHighlight();
        Assert.assertEquals(Collections.emptyList(), getEditors("value"));
    }

    @Test
    public void bind_focusTwice_topicHasOneEditor() {
        client.bind();
        client.attach();
        field.showHighlight();
        field.showHighlight();
        Assert.assertEquals(Arrays.asList(client.binder.getLocalUser()),
                getEditors("value"));
    }

    @Test
    public void twoActivatedClientsFocus_topicContainsBothEditors() {
        client.bind();
        client.attach();

        client2.bind();
        client2.attach();

        field.showHighlight();
        client2.field.showHighlight();

        Assert.assertEquals(Arrays.asList(client.binder.getLocalUser(),
                client2.binder.getLocalUser()), getEditors("value"));
    }

    @Test
    public void focus_deactivate_editorRemoved() {
        client.bind();
        client.attach();

        client2.bind();
        client2.attach();

        field.showHighlight();
        client2.field.showHighlight();

        client.detach();

        Assert.assertEquals(Arrays.asList(client2.binder.getLocalUser()),
                getEditors("value"));

        client2.detach();

        Assert.assertEquals(Collections.emptyList(), getEditors("value"));
    }

    @Test
    public void setTopicWhenAttached_topicEmpty_topicInitialized() {
        // Prevent attach from populating the regular topic
        client.binder.setTopic("another", () -> null);

        client.bind();
        client.attach();

        client.binder.setTopic("topic", () -> new TestBean("a"));

        Assert.assertEquals("a", getSharedValue("value", String.class));
    }

    @Test
    public void setTopicWhenDetached_topicEmpty_topicInitialized() {
        client.binder.setTopic("topic", () -> new TestBean("a"));

        client.bind();
        client.attach();

        Assert.assertEquals("a", getSharedValue("value", String.class));
    }

    @Test
    public void setTopicWhenAttached_initializedTopic_topicValueRetained() {
        // Prevent attach from using the regular topic
        client.binder.setTopic("another", () -> null);

        setSharedValue("value", MockJson.FOO);

        client.bind();
        client.attach();

        client.binder.setTopic("topic", () -> new TestBean("b"));

        Assert.assertEquals("foo", getSharedValue("value", String.class));
    }

    @Test
    public void setTopicWhenDetached_initializedTopic_topicValueRetained() {
        setSharedValue("value", MockJson.FOO);

        client.binder.setTopic("topic", () -> new TestBean("b"));

        client.bind();
        client.attach();

        Assert.assertEquals("foo", getSharedValue("value", String.class));
    }

    @Test
    public void setTopic_initializedTopic_supplierNotCalled() {
        setSharedValue("value", MockJson.FOO);

        client.binder.setTopic("topic", () -> {
            throw new AssertionError();
        });

        client.bind();
        client.attach();
    }

    @Test
    public void setTopic_initializedTopic_fieldValuesSet() {
        setSharedValue("value", MockJson.FOO);

        client.binder.setTopic("topic", () -> null);

        client.bind();
        client.attach();

        Assert.assertEquals("foo", field.getValue());
    }

    @Test
    public void changeTopic_ignoreOldTopic() {
        client.bind();
        client.attach();

        client.binder.setTopic("another", () -> null);

        setSharedValue("value", MockJson.FOO);
        Assert.assertTrue(client.field.isEmpty());

        client.field.setValue("b");
        Assert.assertEquals("foo", getSharedValue("value", String.class));
    }

    @Test
    public void clearTopic_ignoreOldTopic() {
        client.bind();
        client.attach();

        client.binder.setTopic(null, () -> null);

        setSharedValue("value", MockJson.FOO);
        Assert.assertTrue(client.field.isEmpty());

        client.field.setValue("b");
        Assert.assertEquals("foo", getSharedValue("value", String.class));
    }

    @Test
    public void clearTopic_clientsNotConnected() {
        client.bind();
        client.attach();
        client.binder.setTopic(null, () -> null);

        client2.bind();
        client2.attach();
        client2.binder.setTopic(null, () -> null);

        client2.field.setValue("b");

        Assert.assertTrue(client.field.isEmpty());
        Assert.assertFalse(client.field.hasListener(AttachEvent.class));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void readBean_operationNotSupported() {
        client.binder.readBean(new TestBean());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void setBean_operationNotSupported() {
        client.binder.setBean(new TestBean());
    }

    @Test
    public void collaborationMapValue_set_get() {
        client.bind();
        client.attach();
        client.field.setValue("foo");

        JsonNode value = CollaborationBinderUtil
                .getFieldValue(map.getConnection(), "value");

        String mapValue = JsonUtil.toInstance(value, String.class);
        Assert.assertEquals("foo", mapValue);
    }

    @Test
    public void simpleBindingWithNullRepresentation_typeInferred() {
        client.binder.forField(client.field).withNullRepresentation("foo")
                .bind("value");
        client.attach();

        setSharedValue("value", "foo");
        Assert.assertEquals("foo", client.field.getValue());
    }

    @Test
    public void bindLocalDate_oneClientSets_otherClientReceives() {
        client.bindLocalDate();
        client.attach(client.localDateField);

        client2.bindLocalDate();
        client2.attach(client2.localDateField);

        LocalDate localDate = LocalDate.of(2020, 10, 16);
        client.localDateField.setValue(localDate);
        Assert.assertEquals(localDate, client2.localDateField.getValue());
    }

    @Test(expected = IllegalStateException.class)
    public void bindingGenericFieldWithConverter_explicitTypeDefinitionRequired() {
        client.binder.forField(new GenericTestField<>())
                .withConverter(String::valueOf, Integer::valueOf).bind("value");
    }

    @Test
    public void bindingGenericFieldWithConverter_explicitTypeProvided_propertyTypeAsDefined() {
        GenericTestField<Integer> field = new GenericTestField<>();
        client.binder.forField(field, Integer.class)
                .withConverter(String::valueOf, Integer::valueOf).bind("value");
        client.attach(field);

        setSharedValue("value", Integer.valueOf(42));
        Assert.assertEquals(42, field.getValue().intValue());
    }

    @Test
    public void complexTypeWithExplicitDefinition_valueSerializedAndDeserializedProperly() {
        GenericTestField<List<Double>> field = new GenericTestField<>();

        client.binder.forField(field, List.class, Double.class)
                .withNullRepresentation(Collections.emptyList())
                .withConverter(presentationValue -> presentationValue.stream()
                        .map(String::valueOf).collect(Collectors.joining(",")),
                        modelValue -> Stream.of(modelValue.split(","))
                                .map(Double::valueOf)
                                .collect(Collectors.toList()))
                .bind("value");

        client.attach(field);

        field.setValue(Arrays.asList(1d, 0.1d));
        JsonNode value = CollaborationBinderUtil.getFieldValue(topicConnection,
                "value");

        Assert.assertEquals(Arrays.asList(1d, 0.1d),
                JsonUtil.toInstance(value, MockJson.LIST_DOUBLE_TYPE_REF));

        CollaborationBinderUtil.setFieldValue(topicConnection, "value",
                JsonUtil.toJsonNode(Arrays.asList(0.1d, 1d)));
        Assert.assertEquals(Arrays.asList(0.1d, 1d), field.getValue());
    }

    @Test
    public void collectionTypeWithElementSerializer_valueSerializedAndDeserializedProperly() {
        GenericTestField<List<TestBean>> field = new GenericTestField<>();

        client.binder.setSerializer(TestBean.class, TestBean::getValue,
                TestBean::new);

        client.binder.forField(field, List.class, TestBean.class)
                .withNullRepresentation(Collections.emptyList())
                .bind("testBeans");

        client.attach(field);

        field.setValue(Arrays.asList(new TestBean("one"), new TestBean("two")));
        JsonNode value = CollaborationBinderUtil.getFieldValue(topicConnection,
                "testBeans");

        Assert.assertEquals(Arrays.asList("one", "two"),
                JsonUtil.toInstance(value, MockJson.LIST_STRING_TYPE_REF));

        CollaborationBinderUtil.setFieldValue(topicConnection, "testBeans",
                JsonUtil.toJsonNode(Arrays.asList("three", "four")));

        List<TestBean> fieldValue = field.getValue();
        Assert.assertEquals(2, fieldValue.size());
        Assert.assertEquals("three", fieldValue.get(0).getValue());
        Assert.assertEquals("four", fieldValue.get(1).getValue());
    }

    @Test(expected = IllegalStateException.class)
    public void listTypeWithoutCollectionAndElementType_rejected() {
        GenericTestField<List<TestBean>> field = new GenericTestField<>();
        client.binder.forField(field).bind("testBeans");
    }

    @Test
    public void collectionTypeWithConverterAndElementSerializer_valueSerializedAndDeserializedProperly() {
        GenericTestField<List<TestBean>> field = new GenericTestField<>();

        client.binder.setSerializer(TestBean.class, TestBean::getValue,
                TestBean::new);

        client.binder.forField(field, List.class, TestBean.class)
                .withNullRepresentation(Collections.emptyList())
                .withConverter(
                        presentationValue -> presentationValue.stream()
                                .map(TestBean::getValue)
                                .collect(Collectors.joining(",")),
                        modelValue -> Stream.of(modelValue.split(","))
                                .map(TestBean::new)
                                .collect(Collectors.toList()))
                .bind("value");

        client.attach(field);

        field.setValue(Arrays.asList(new TestBean("one"), new TestBean("two")));
        JsonNode value = CollaborationBinderUtil.getFieldValue(topicConnection,
                "value");

        Assert.assertEquals(Arrays.asList("one", "two"),
                JsonUtil.toInstance(value, MockJson.LIST_STRING_TYPE_REF));

        CollaborationBinderUtil.setFieldValue(topicConnection, "value",
                JsonUtil.toJsonNode(Arrays.asList("three", "four")));

        List<TestBean> fieldValue = field.getValue();
        Assert.assertEquals(2, fieldValue.size());
        Assert.assertEquals("three", fieldValue.get(0).getValue());
        Assert.assertEquals("four", fieldValue.get(1).getValue());
    }

    @Test(expected = IllegalStateException.class)
    public void collectionTypeWithComplexTypeWithoutSerializer_rejected() {
        GenericTestField<List<TestBean>> field = new GenericTestField<>();
        client.binder.forField(field, List.class, TestBean.class).bind("value");
    }

    @Test
    public void complexTypeWithSerializer_valueSerializedAndDeserializedProperly() {
        GenericTestField<TestBean> field = new GenericTestField<>();

        client.binder.setSerializer(TestBean.class, TestBean::getValue,
                TestBean::new);

        client.binder.forField(field).bind("testBean");
        client.attach(field);

        field.setValue(new TestBean("Lorem"));
        JsonNode value = CollaborationBinderUtil.getFieldValue(topicConnection,
                "testBean");
        Assert.assertTrue("Expected text node", value.isTextual());
        Assert.assertEquals("Lorem", JsonUtil.toInstance(value, String.class));

        CollaborationBinderUtil.setFieldValue(topicConnection, "testBean",
                JsonUtil.toJsonNode("Ipsum"));
        Assert.assertEquals("Ipsum", field.getValue().getValue());
    }

    @Test
    public void complexTypeWithConverterAndSerializer_valueSerializedAndDeserializedProperly() {
        GenericTestField<TestBean> field = new GenericTestField<>();

        client.binder.setSerializer(TestBean.class, TestBean::getValue,
                TestBean::new);

        client.binder.forField(field, TestBean.class)
                .withConverter(TestBean::getValue, TestBean::new).bind("value");
        client.attach(field);

        field.setValue(new TestBean("Lorem"));
        JsonNode value = CollaborationBinderUtil.getFieldValue(topicConnection,
                "value");
        Assert.assertTrue("Expected text node", value.isTextual());
        Assert.assertEquals("Lorem", JsonUtil.toInstance(value, String.class));

        CollaborationBinderUtil.setFieldValue(topicConnection, "value",
                JsonUtil.toJsonNode("Ipsum"));
        Assert.assertEquals("Ipsum", field.getValue().getValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setSerializer_builtInType_throws() {
        client.binder.setSerializer(Integer.class, String::valueOf,
                Integer::valueOf);
    }

    @Test
    public void emptyField_serializerNotCalled() {
        GenericTestField<TestBean> field = new GenericTestField<>();

        ArrayList<TestBean> serializedValues = new ArrayList<>();

        client.binder.setSerializer(TestBean.class, value -> {
            serializedValues.add(value);
            return value.getValue();
        }, TestBean::new);

        client.binder.bind(field, "testBean");
        client.attach(field);

        // Just to verify that the serializer is actually set up correctly
        field.setValue(new TestBean("foo"));
        Assert.assertEquals(Arrays.asList(new TestBean("foo")),
                serializedValues);

        field.setValue(null);
        Assert.assertEquals("Serializer should not have been run again",
                Arrays.asList(new TestBean("foo")), serializedValues);
        Assert.assertNull("Shared value should be cleared",
                getSharedValue("testBean", String.class));
    }

    @Test
    public void mapValueCleared_deserializerNotCalled() {
        GenericTestField<TestBean> field = new GenericTestField<>();

        ArrayList<String> deserializedValues = new ArrayList<>();

        client.binder.setSerializer(TestBean.class, TestBean::getValue,
                value -> {
                    deserializedValues.add(value);
                    return new TestBean(value);
                });

        client.binder.bind(field, "testBean");
        client.attach(field);

        // Just to verify that the deserializer is actually set up correctly
        setSharedValue("testBean", "foo");
        Assert.assertEquals(Arrays.asList("foo"), deserializedValues);

        setSharedValue("testBean", null);

        Assert.assertEquals("Serializer should not have been run again",
                Arrays.asList("foo"), deserializedValues);
        Assert.assertNull("Field should be cleared", field.getValue());
    }

    @Test
    public void setSerializer_replaceExisting_throws() {
        client.binder.setSerializer(TestBean.class, TestBean::getValue,
                TestBean::new);

        try {
            client.binder.setSerializer(TestBean.class, TestBean::getValue,
                    TestBean::new);
            Assert.fail("Redefining existing serializer should not be allowed");
        } catch (IllegalStateException expected) {
            // All is fine
        }
    }

    @Test
    public void bindInstanceFields_simpleCase_jsonHandlerInferred() {
        class Target {
            private TestField value = new TestField();
        }

        Target target = new Target();
        client.binder.bindInstanceFields(target);
        client.attach(target.value);

        setSharedValue("value", "foo");

        Assert.assertEquals("foo", target.value.getValue());
    }

    @Test(expected = IllegalStateException.class)
    public void bindInstanceFields_converterNotExplicitType_throws() {
        class Target {
            private GenericTestField<Integer> value = new GenericTestField<>();
        }

        Target target = new Target();
        client.binder.forMemberField(target.value)
                .withConverter(String::valueOf, Integer::valueOf);
        client.binder.bindInstanceFields(target);
    }

    @Test
    public void bindInstanceFields_converterWithExplicitType_jsonHandlerInferred() {
        class Target {
            private GenericTestField<Integer> value = new GenericTestField<>();
        }

        Target target = new Target();
        client.binder.forMemberField(target.value, Integer.class)
                .withConverter(String::valueOf, Integer::valueOf);
        client.binder.bindInstanceFields(target);

        client.attach(target.value);

        setSharedValue("value", Integer.valueOf(42));

        Assert.assertEquals(Integer.valueOf(42), target.value.getValue());
    }

    @Test(expected = IllegalStateException.class)
    public void unsupportedExplicitType_throwsFromForField() {
        client.binder.forField(new GenericTestField<>(), TestBean.class);
    }

    @Test(expected = IllegalStateException.class)
    public void unsupportedPropertyType_throwsWhenBinding() {
        class BeanWithUnsupportedPropertyType {
            public void setValue(TestBean value) {

            }

            public TestBean getValue() {
                return null;
            }
        }

        CollaborationBinder<BeanWithUnsupportedPropertyType> binder = new CollaborationBinder<>(
                BeanWithUnsupportedPropertyType.class, client.user);
        binder.forField(new GenericTestField<TestBean>()).bind("value");
    }

    @Test(expected = IllegalStateException.class)
    public void unsupportedTypeParameter_rejected() {
        client.binder.forField(new GenericTestField<List<TestBean>>(),
                List.class, TestBean.class);
    }

    @Test(expected = IllegalStateException.class)
    @SuppressWarnings("rawtypes")
    public void rawFieldType_rejected() {
        client.binder.forField(new GenericTestField<List>(), List.class);
    }

    @Test(expected = NullPointerException.class)
    public void nullFieldType_rejected() {
        client.binder.forField(client.field, null);
    }

    @Test
    public void expirationTimeout_timeoutIsSetOnBinderMap_beforeTopicIsSet() {
        client.binder.setExpirationTimeout(Duration.ofMinutes(15));
        client.bind();
        client.attach();
        long timeout = map.getExpirationTimeout().get().toMinutes();
        Assert.assertEquals(15, timeout);
    }

    @Test
    public void expirationTimeout_timeoutIsSetOnBinderMap_afterTopicIsSet() {
        client.bind();
        client.attach();
        client.binder.setExpirationTimeout(Duration.ofMinutes(15));
        long timeout = map.getExpirationTimeout().get().toMinutes();
        Assert.assertEquals(15, timeout);
    }

    @Test
    public void expirationTimeout_timeoutIsUnset() {
        client.binder.setExpirationTimeout(Duration.ofMinutes(15));
        client.bind();
        client.attach();
        client.binder.setExpirationTimeout(null);
        Assert.assertFalse(map.getExpirationTimeout().isPresent());
    }

    @Test(expected = ValidationException.class)
    public void withValidator_writeBeanThrowsWithInvalidValue()
            throws ValidationException {
        Validator<String> validator = new StringLengthValidator("ERROR", 4, 8);
        client.binder.forField(client.field, String.class)
                .withValidator(validator).bind("value");
        client.attach();
        client.field.setValue("foo");
        TestBean bean = new TestBean();
        client.binder.writeBean(bean);
    }

    @Test
    public void withValidator_fieldAppearsInvalidOnAllClients()
            throws ValidationException {
        Validator<String> validator = new StringLengthValidator("ERROR", 4, 8);
        client.binder.forField(client.field, String.class)
                .withValidator(validator).bind("value");
        client.attach();
        client2.binder.forField(client2.field, String.class)
                .withValidator(validator).bind("value");
        client2.attach();
        client.field.setValue("foo");

        Assert.assertTrue(client.field.isInvalid());
        Assert.assertTrue(client2.field.isInvalid());
    }

    @Test
    public void withConverter_valueIsConvertedProperly() {
        Converter<String, LocalDate> converter = new Converter<String, LocalDate>() {

            @Override
            public Result<LocalDate> convertToModel(String value,
                    ValueContext context) {
                try {
                    return Result.ok(LocalDate.parse(value));
                } catch (DateTimeParseException e) {
                    return Result.ok(null);
                }
            }

            @Override
            public String convertToPresentation(LocalDate value,
                    ValueContext context) {
                return value.toString();
            }
        };
        client.binder.forField(client.field, String.class)
                .withConverter(converter).bind("localDate");
        client.attach();
        client.field.setValue("2021-02-11");
        TestBean bean = new TestBean();
        client.binder.writeBeanIfValid(bean);

        Assert.assertEquals(LocalDate.parse("2021-02-11"), bean.getLocalDate());
    }

    @Test
    public void addStatusChangeListener_listenerIsInvokedOnStatusChange() {
        AtomicReference<StatusChangeEvent> event = new AtomicReference<>();
        client.binder.forField(client.field, String.class).bind("value");
        client.attach();
        client.binder.addStatusChangeListener(event::set);
        client.field.setValue("foo");

        Assert.assertNotNull(event.get());
    }

    @Test
    public void serializeBinder() {
        CollaborationBinder<TestBean> binder = client.binder;

        CollaborationBinder<TestBean> deserializedBinder = TestUtils
                .serialize(binder);
    }
}
