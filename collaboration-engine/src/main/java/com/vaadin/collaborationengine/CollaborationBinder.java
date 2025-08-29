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

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.vaadin.collaborationengine.HighlightHandler.HighlightContext;
import com.vaadin.collaborationengine.PropertyChangeHandler.PropertyChangeEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.BindingValidationStatusHandler;
import com.vaadin.flow.data.binder.PropertyDefinition;
import com.vaadin.flow.data.binder.Setter;
import com.vaadin.flow.data.converter.Converter;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.function.SerializableSupplier;
import com.vaadin.flow.function.ValueProvider;
import com.vaadin.flow.internal.UsageStatistics;
import com.vaadin.flow.shared.Registration;

/**
 * Extension of {@link Binder} for creating collaborative forms with
 * {@link CollaborationEngine}. In addition to Binder's data binding mechanism,
 * CollaborationBinder synchronizes the field values between clients which are
 * connected to the same topic via {@link TopicConnection}.
 *
 * @author Vaadin Ltd
 * @since 1.0
 *
 * @param <BEAN>
 *            the bean type
 */
public class CollaborationBinder<BEAN> extends Binder<BEAN>
        implements HasExpirationTimeout {

    private static final List<Class<?>> SUPPORTED_CLASS_TYPES = Arrays.asList(
            String.class, Boolean.class, Integer.class, Double.class,
            BigDecimal.class, LocalDate.class, LocalTime.class,
            LocalDateTime.class, Enum.class);
    private static final List<Class<?>> SUPPORTED_COLLECTION_TYPES = Arrays
            .asList(List.class, Set.class);

    static class JsonHandler<T> implements Serializable {
        private final SerializableFunction<T, JsonNode> serializer;
        private final SerializableFunction<JsonNode, T> deserializer;

        private JsonHandler(SerializableFunction<T, JsonNode> serializer,
                SerializableFunction<JsonNode, T> deserializer) {
            this.serializer = serializer;
            this.deserializer = deserializer;
        }

        private static <T> JsonHandler<T> forBasicType(Class<T> fieldType) {
            return new CollaborationBinder.JsonHandler<>(JsonUtil::toJsonNode,
                    jsonNode -> JsonUtil.toInstance(jsonNode, fieldType));
        }

        private void store(HasValue<?, ?> field) {
            if (field instanceof Component) {
                Component fieldAsComponent = (Component) field;
                ComponentUtil.setData(fieldAsComponent, JsonHandler.class,
                        this);
            } else {
                throw new IllegalArgumentException(
                        "CollaborationBinder can only be used with component fields. The provided field is of type "
                                + field.getClass().getName());
            }
        }

        private static JsonHandler<?> getAndClear(HasValue<?, ?> field) {
            if (field instanceof Component) {
                Component fieldAsComponent = (Component) field;
                JsonHandler<?> config = ComponentUtil.getData(fieldAsComponent,
                        JsonHandler.class);
                ComponentUtil.setData(fieldAsComponent, JsonHandler.class,
                        null);
                return config;
            }

            return null;
        }

        private JsonNode serialize(T value) {
            return serializer.apply(value);
        }

        private T deserialize(JsonNode jsonNode) {
            return deserializer.apply(jsonNode);
        }

    }

    protected static class CollaborationBindingBuilderImpl<BEAN, FIELDVALUE, TARGET>
            extends BindingBuilderImpl<BEAN, FIELDVALUE, TARGET> {
        private String propertyName = null;
        private boolean typeIsConverted = false;
        private JsonHandler<?> explicitJsonHandler;

        protected CollaborationBindingBuilderImpl(
                CollaborationBinder<BEAN> binder, HasValue<?, FIELDVALUE> field,
                Converter<FIELDVALUE, TARGET> converterValidatorChain,
                BindingValidationStatusHandler statusHandler) {
            super(binder, field, converterValidatorChain, statusHandler);

            explicitJsonHandler = JsonHandler.getAndClear(field);
        }

        @Override
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public Binding<BEAN, TARGET> bind(ValueProvider<BEAN, TARGET> getter,
                Setter<BEAN, TARGET> setter) {
            // Capture current propertyName
            final String propertyName = this.propertyName;

            if (propertyName == null) {
                throw new UnsupportedOperationException(
                        "A property name must always be provided when binding with the collaboration binder. "
                                + "Use bind(String propertyName) instead.");
            }

            Binding<BEAN, TARGET> binding = super.bind(getter, setter);

            HasValue field = binding.getField();

            CollaborationBinder<BEAN> binder = getBinder();
            ComponentConnectionContext connectionContext = binder.connectionContext;
            if (connectionContext != null) {
                connectionContext.addComponent((Component) field);
            }

            List<Registration> registrations = new ArrayList<>();

            registrations.add(field.addValueChangeListener(
                    event -> binder.setMapValueFromField(propertyName, field)));

            registrations.add(FieldHighlighter.setupForField(field,
                    propertyName, binder));

            binder.bindingRegistrations.put(binding,
                    () -> registrations.forEach(Registration::remove));

            binder.fieldToPropertyName.put(field, propertyName);

            return binding;
        }

        @Override
        public Binding<BEAN, TARGET> bind(String propertyName) {
            try {
                this.propertyName = propertyName;
                return super.bind(propertyName);
            } finally {
                this.propertyName = null;
            }
        }

        @Override
        protected CollaborationBinder<BEAN> getBinder() {
            return (CollaborationBinder<BEAN>) super.getBinder();
        }

        @Override
        protected <NEWTARGET> BindingBuilder<BEAN, NEWTARGET> withConverter(
                Converter<TARGET, NEWTARGET> converter,
                boolean resetNullRepresentation) {
            if (resetNullRepresentation) {
                // Flag implies that this is a "real" converter
                typeIsConverted = true;
            }
            return super.withConverter(converter, resetNullRepresentation);
        }

        @Override
        public BindingBuilder<BEAN, TARGET> withNullRepresentation(
                TARGET nullRepresentation) {
            /*
             * The null representation is internally implemented as a converter,
             * even though it doesn't change the type of the value. Because of
             * this, we reset the flag to its previous value after it becomes
             * set by the super call that sets a converter.
             */
            boolean typeWasConverted = typeIsConverted;
            try {
                return super.withNullRepresentation(nullRepresentation);
            } finally {
                typeIsConverted = typeWasConverted;
            }
        }

    }

    private final UserInfo localUser;
    private final SerializableSupplier<CollaborationEngine> ceSupplier;
    private final FieldHighlighter fieldHighlighter;

    private ComponentConnectionContext connectionContext;
    private FormManager formManager;
    private Duration expirationTimeout;

    private final Map<Binding<?, ?>, Registration> bindingRegistrations = new HashMap<>();
    private final Map<HasValue<?, ?>, String> fieldToPropertyName = new HashMap<>();
    private final Map<String, JsonHandler<?>> propertyJsonHandlers = new HashMap<>();
    private final Map<Class<?>, JsonHandler<?>> typeConfigurations = new HashMap<>();

    static {
        UsageStatistics.markAsUsed(
                CollaborationEngine.COLLABORATION_ENGINE_NAME
                        + "/CollaborationBinder",
                CollaborationEngine.COLLABORATION_ENGINE_VERSION);
    }

    /**
     * Creates a new collaboration binder. It uses reflection based on the
     * provided bean type to resolve bean properties.
     * <p>
     * The provided user information is used in the field editing indicators.
     * The name of the user will be displayed to other users when editing a
     * field, and the user's color index will be used to set the field's
     * highlight color.
     *
     * @param beanType
     *            the bean type to use, not <code>null</code>
     * @param localUser
     *            the information of the local user, not <code>null</code>
     * @since 1.0
     */
    public CollaborationBinder(Class<BEAN> beanType, UserInfo localUser) {
        this(beanType, localUser, CollaborationEngine::getInstance);
    }

    CollaborationBinder(Class<BEAN> beanType, UserInfo localUser,
            SerializableSupplier<CollaborationEngine> ceSupplier) {
        super(beanType);
        this.localUser = Objects.requireNonNull(localUser,
                "User cannot be null");
        this.ceSupplier = ceSupplier;
        this.fieldHighlighter = new FieldHighlighter(
                user -> getCollaborationEngine().getUserColorIndex(user));
    }

    private CollaborationEngine getCollaborationEngine() {
        return ceSupplier.get();
    }

    @Override
    protected BindingBuilder<BEAN, ?> configureBinding(
            BindingBuilder<BEAN, ?> baseBinding,
            PropertyDefinition<BEAN, ?> definition) {
        CollaborationBindingBuilderImpl<?, ?, ?> binding = (CollaborationBindingBuilderImpl<?, ?, ?>) baseBinding;

        JsonHandler<?> handler = findJsonHandler(binding, definition);

        propertyJsonHandlers.put(definition.getName(), handler);
        return super.configureBinding(baseBinding, definition);
    }

    private static boolean isSupportedType(Type type) {
        Objects.requireNonNull(type, "Type cannot be null");
        if (type instanceof Class<?>) {
            return isAssignableFromAny(SUPPORTED_CLASS_TYPES, (Class<?>) type);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;

            if (isAssignableFromAny(SUPPORTED_COLLECTION_TYPES,
                    (Class<?>) parameterizedType.getRawType())) {

                for (Type typeArgument : parameterizedType
                        .getActualTypeArguments()) {
                    if (!isSupportedType(typeArgument)) {
                        return false;
                    }
                }

                return true;
            }
        }

        return false;
    }

    private static String createTypeNotSupportedMessage(Type type) {
        return "The type " + type.getTypeName() + " is not supported. "
                + "You must use setSerializer to define conversion logic for custom value types. "
                + "Supported types are: "
                + Stream.concat(SUPPORTED_CLASS_TYPES.stream(),
                        SUPPORTED_COLLECTION_TYPES.stream()).map(Class::getName)
                        .collect(Collectors.joining(", "))
                + ". "
                + "For collections, the element type must be among the supported types.";
    }

    private static boolean isAssignableFromAny(List<Class<?>> types,
            Class<?> type) {
        return types.stream()
                .anyMatch(candidate -> candidate.isAssignableFrom(type));
    }

    private JsonHandler<?> findJsonHandler(
            CollaborationBindingBuilderImpl<?, ?, ?> builder,
            PropertyDefinition<?, ?> propertyDefinition) {
        if (builder.explicitJsonHandler != null) {
            // Use explicitly defined handler if available
            return builder.explicitJsonHandler;
        }

        if (!builder.typeIsConverted) {
            Class<?> propertyType = propertyDefinition.getType();

            if (isAssignableFromAny(SUPPORTED_COLLECTION_TYPES, propertyType)) {
                /*
                 * Property is a Collection but it did not have explicit
                 * collection and element types defined
                 */
                throw new IllegalStateException(
                        "Cannot configure JSON serializer for '"
                                + builder.propertyName + "' with type '"
                                + propertyType.getName() + "'. For collection "
                                + "types, you have to specify the type of the "
                                + "collection and the type of the elements in "
                                + "the collection. Use "
                                + "CollaborationBinder::forField(field, "
                                + "collectionType, elementType) to specify "
                                + "these. For example, if you are binding a "
                                + "List of String, you should call "
                                + "forField(field, List.class, String.class).");
            }
            /*
             * Can use the property type as long as there is no converter. A
             * converter would imply that the bean type is not the same as the
             * field type.
             */
            return getTypeConfiguration(propertyType)
                    .orElseThrow(() -> new IllegalStateException(
                            "Cannot configure JSON serializer for "
                                    + builder.propertyName + ". "
                                    + createTypeNotSupportedMessage(
                                            propertyType)));
        }

        throw new IllegalStateException(
                "Could not infer field type for property '"
                        + builder.propertyName
                        + "'. Configure the property using an overload of forField or forMemberField that allows explicitly defining the field type.");
    }

    private void handlePropertyChange(PropertyChangeEvent event) {
        getBinding(event.getPropertyName()).map(Binding::getField)
                .ifPresent(field -> {
                    String propertyName = event.getPropertyName();
                    JsonNode value = JsonUtil.toJsonNode(event.getValue());

                    setFieldValueFromFieldState(field, propertyName,
                            value == null ? NullNode.getInstance() : value);
                });
    }

    private Registration handleHighlight(HighlightContext context) {
        if (!context.getUser().equals(localUser)) {
            getBinding(context.getPropertyName()).map(Binding::getField)
                    .ifPresent(field -> fieldHighlighter.addEditor(field,
                            context.getUser(), context.getFieldIndex()));

            return () -> getBinding(context.getPropertyName())
                    .map(Binding::getField)
                    .ifPresent(field -> fieldHighlighter.removeEditor(field,
                            context.getUser(), context.getFieldIndex()));
        }
        return null;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void setFieldValueFromFieldState(HasValue field,
            String propertyName, JsonNode stateValue) {
        if (stateValue instanceof NullNode) {
            field.clear();
        } else {
            JsonHandler handler = propertyJsonHandlers.get(propertyName);
            field.setValue(handler.deserialize(stateValue));
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void setMapValueFromField(String propertyName, HasValue field) {
        if (formManager != null) {
            Object value;
            if (field.isEmpty()) {
                value = null;
            } else {
                JsonHandler handler = propertyJsonHandlers.get(propertyName);

                value = handler.serialize(field.getValue());
            }
            formManager.setValue(propertyName, value);
        }
    }

    void addEditor(String propertyName, int fieldIndex) {
        if (formManager != null) {
            formManager.highlight(propertyName, true, fieldIndex);
        }
    }

    void removeEditor(String propertyName) {
        if (formManager != null) {
            formManager.highlight(propertyName, false);
        }
    }

    @Override
    protected void removeBindingInternal(Binding<BEAN, ?> binding) {
        // Registration should be removed first, so it can e.g. remove editors
        // in map.
        // If the attached component is removed from the context first and the
        // connection is deactivated, registration removal can't update map
        // value.
        Registration registration = bindingRegistrations.remove(binding);
        if (registration != null) {
            registration.remove();
        }

        String propertyName = fieldToPropertyName.remove(binding.getField());
        propertyJsonHandlers.remove(propertyName);
        if (connectionContext != null) {
            connectionContext.removeComponent((Component) binding.getField());
        }
        super.removeBindingInternal(binding);
    }

    @Override
    protected <FIELDVALUE, TARGET> BindingBuilder<BEAN, TARGET> doCreateBinding(
            HasValue<?, FIELDVALUE> field,
            Converter<FIELDVALUE, TARGET> converter,
            BindingValidationStatusHandler handler) {
        return new CollaborationBindingBuilderImpl<>(this, field, converter,
                handler);
    }

    /**
     * Binds the given field to the property with the given name, as described
     * in {@link Binder#bind(HasValue, String)}.
     * <p>
     * In addition, synchronizes the values with other collaboration binder
     * instances which are connected to the same topic.
     *
     * @param <FIELDVALUE>
     *            the value type of the field to bind
     * @param field
     *            the field to bind, not <code>null</code>
     * @param propertyName
     *            the name of the property to bind, not <code>null</code>
     * @return the newly created binding
     * @throws IllegalArgumentException
     *             if the property name is invalid
     * @throws IllegalArgumentException
     *             if the property has no accessible getter
     */
    @Override
    public <FIELDVALUE> Binding<BEAN, FIELDVALUE> bind(
            HasValue<?, FIELDVALUE> field, String propertyName) {
        return super.bind(field, propertyName);
    }

    /**
     * Binds the member fields found in the given object, as described in
     * {@link Binder#bindInstanceFields(Object)}.
     * <p>
     * In addition, synchronizes the values with other collaboration binder
     * instances which are connected to the same topic.
     *
     * @param objectWithMemberFields
     *            the object that contains (Java) member fields to bind
     * @throws IllegalStateException
     *             if there are incompatible HasValue&lt;T&gt; and property
     *             types
     */
    @Override
    public void bindInstanceFields(Object objectWithMemberFields) {
        super.bindInstanceFields(objectWithMemberFields);
    }

    /**
     * @deprecated This operation is not supported by the collaboration binder.
     *             You can instead provide the bean for populating the fields
     *             using {@link #setTopic}, and write the values back to the
     *             bean using {@link #writeBean}.
     */
    @Override
    @Deprecated(forRemoval = false)
    public void setBean(BEAN bean) {
        throw new UnsupportedOperationException(
                "This operation is not supported by the collaboration binder. "
                        + "You can instead provide the bean for populating "
                        + "the fields with the setTopic method, and write the "
                        + "values back to the bean with the writeBean method.");
    }

    /**
     * @deprecated This operation, along with {@link #setBean(Object)}, is not
     *             supported by the collaboration binder. Instead of
     *             {@link #setBean(Object)}, you can provide the bean for
     *             populating the fields using {@link #setTopic}, and write the
     *             values back to the bean using {@link #writeBean}.
     */
    @Override
    @Deprecated(forRemoval = false)
    public BEAN getBean() {
        return super.getBean();
    }

    /**
     * @deprecated This operation is not supported by the collaboration binder.
     *             You can instead provide the bean for populating the fields
     *             using {@link #setTopic} to avoid overriding currently edited
     *             values. If you explicitly want to reset the field values for
     *             every user currently editing the fields, you can use
     *             {@link #reset}.
     */
    @Override
    @Deprecated(forRemoval = false)
    public void readBean(BEAN bean) {
        throw new UnsupportedOperationException(
                "This operation is not supported by the collaboration binder. "
                        + "You can instead provide the bean for populating the fields "
                        + "with the setTopic method to avoid overriding currently edited values. "
                        + "If you explicitly want to reset the field values for every user "
                        + "currently editing the fields, you can use the reset method.");
    }

    /**
     * Resets collaborative fields with values from the bound properties of the
     * given bean. The values will be propagated to all collaborating users.
     *
     * @param bean
     *            the bean whose property values to read or {@code null} to
     *            clear bound fields
     * @since 1.0
     */
    public void reset(BEAN bean) {
        super.readBean(bean);
    }

    UserInfo getLocalUser() {
        return localUser;
    }

    /**
     * Sets the topic to use with this binder and initializes the topic contents
     * if not already set. Setting a topic removes the connection to the
     * previous topic (if any) and resets all bindings based on values in the
     * new topic. The bean supplier is used to provide initial values for
     * bindings in case the topic doesn't yet contain any values.
     *
     *
     * @param topicId
     *            the topic id to use, or <code>null</code> to not use any topic
     * @param initialBeanSupplier
     *            a supplier that is invoked to get a bean from which to read
     *            initial values. Only invoked if there are no property values
     *            in the topic, or if the topic id is <code>null</code>.
     * @since 1.0
     */
    public void setTopic(String topicId,
            SerializableSupplier<BEAN> initialBeanSupplier) {
        if (formManager != null) {
            formManager.close();
            formManager = null;

            fieldToPropertyName.keySet()
                    .forEach(fieldHighlighter::removeEditors);
            connectionContext = null;
        }

        if (topicId == null) {
            super.readBean(initialBeanSupplier.get());
        } else {
            super.readBean(null);

            connectionContext = new ComponentConnectionContext();
            fieldToPropertyName.keySet().forEach(
                    field -> connectionContext.addComponent((Component) field));

            formManager = new FormManager(connectionContext, localUser, topicId,
                    ceSupplier);
            formManager.setActivationHandler(() -> {
                initializeBindingsWithoutFieldState(initialBeanSupplier);
                return null;
            });
            formManager.onConnectionFailed(
                    e -> super.readBean(initialBeanSupplier.get()));

            formManager.setPropertyChangeHandler(this::handlePropertyChange);
            formManager.setHighlightHandler(this::handleHighlight);
            if (expirationTimeout != null) {
                formManager.setExpirationTimeout(expirationTimeout);
            }
        }
    }

    private void initializeBindingsWithoutFieldState(
            SerializableSupplier<BEAN> initialBeanSupplier) {
        List<String> propertiesWithoutFieldState = fieldToPropertyName.values()
                .stream().filter(propertyName -> formManager
                        .getValue(propertyName, JsonNode.class) == null)
                .collect(Collectors.toList());

        if (propertiesWithoutFieldState.isEmpty()) {
            return;
        }

        BEAN initialBean = initialBeanSupplier.get();

        propertiesWithoutFieldState.stream().map(this::getBinding)
                .filter(Optional::isPresent).map(Optional::get)
                .forEach(binding -> {
                    if (initialBean == null) {
                        binding.getField().clear();
                    } else {
                        binding.read(initialBean);
                    }
                });
    }

    /**
     * {@inheritDoc}
     * <p>
     * The field value will be sent over the network to synchronize the value
     * with other users also editing the same field. The value type to use for
     * deserializing the value is automatically determined based on the bean
     * property type. The type must be defined separately using another overload
     * of this method in case a converter is used or if the property type is
     * parameterized.
     *
     * @see #forField(HasValue, Class)
     * @see #forField(HasValue, Class, Class)
     */
    @Override
    public <FIELDVALUE> BindingBuilder<BEAN, FIELDVALUE> forField(
            HasValue<?, FIELDVALUE> field) {
        // Overridden only to supplement documentation
        return super.forField(field);
    }

    /**
     * Creates a new binding for the given field and type. The returned builder
     * may be further configured before invoking
     * {@link BindingBuilder#bind(String)} which completes the binding. Until
     * {@code Binding.bind} is called, the binding has no effect.
     * <p>
     * The field value will be sent over the network to synchronize the value
     * with other users also editing the same field. This method allows
     * explicitly defining the type to use. This is necessary when a converter
     * is used since it's then not possible to derive the type from the bean
     * property.
     *
     * @see #forField(HasValue)
     * @see #forField(HasValue, Class, Class)
     * @see #setSerializer(Class, SerializableFunction, SerializableFunction)
     *
     * @param <FIELDVALUE>
     *            the value type of the field
     * @param field
     *            the field to be bound, not <code>null</code>
     * @param fieldType
     *            the type of the field value, not <code>null</code>
     * @return the new binding builder
     */
    public <FIELDVALUE> BindingBuilder<BEAN, FIELDVALUE> forField(
            HasValue<?, FIELDVALUE> field, Class<FIELDVALUE> fieldType) {
        getTypeConfigurationOrThrow(fieldType).store(field);
        return forField(field);
    }

    /**
     * Creates a new binding for the given (multi select) field whose value type
     * is a collection. The returned builder may be further configured before
     * invoking {@link BindingBuilder#bind(String)} which completes the binding.
     * Until {@code Binding.bind} is called, the binding has no effect.
     * <p>
     * The field value will be sent over the network to synchronize the value
     * with other users also editing the same field. This method allows
     * explicitly defining the collection type and element type to use.
     *
     * @see #forField(HasValue)
     * @see #forField(HasValue, Class)
     * @see #setSerializer(Class, SerializableFunction, SerializableFunction)
     *
     * @param <FIELDVALUE>
     *            the base type of the collection, e.g. {@code Set} for
     *            {@code CheckboxGroup<String>}
     * @param <ELEMENT>
     *            the type of the elements in the collection, e.g.
     *            {@code String} for {@code CheckboxGroup<String>}
     * @param field
     *            the field to be bound, not <code>null</code>
     * @param collectionType
     *            the base type of the collection, e.g. {@code Set.class} for
     *            {@code CheckboxGroup<String>}, not <code>null</code>
     * @param elementType
     *            the type of the elements in the collection, e.g.
     *            {@code String.class} for {@code CheckboxGroup<String>}, not
     *            <code>null</code>
     * @return the new binding builder
     */
    public <FIELDVALUE extends Collection<ELEMENT>, ELEMENT> BindingBuilder<BEAN, FIELDVALUE> forField(
            HasValue<?, FIELDVALUE> field,
            Class<? super FIELDVALUE> collectionType,
            Class<ELEMENT> elementType) {
        getTypeConfiguration(collectionType, elementType).store(field);
        return forField(field);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The field value will be sent over the network to synchronize the value
     * with other users also editing the same field. The value type to use for
     * deserializing the value is automatically determined based on the bean
     * property type. The type must be defined separately using another overload
     * of this method in case a converter is used or if the property type is
     * parameterized.
     *
     * @see #forMemberField(HasValue, Class)
     * @see #forMemberField(HasValue, Class, Class)
     */
    @Override
    public <FIELDVALUE> BindingBuilder<BEAN, FIELDVALUE> forMemberField(
            HasValue<?, FIELDVALUE> field) {
        // Overridden only to supplement documentation
        return super.forMemberField(field);
    }

    /**
     * Creates a new binding for the given field and type. The returned builder
     * may be further configured before invoking
     * {@link #bindInstanceFields(Object)}. Unlike with the
     * {@link #forField(HasValue)} method, no explicit call to
     * {@link BindingBuilder#bind(String)} is needed to complete this binding in
     * the case that the name of the field matches a field name found in the
     * bean.
     * <p>
     * The field value will be sent over the network to synchronize the value
     * with other users also editing the same field. This method allows
     * explicitly defining the type to use. This is necessary when a converter
     * is used since it's then not possible to derive the type from the bean
     * property.
     *
     * @see #forMemberField(HasValue)
     * @see #forMemberField(HasValue, Class, Class)
     * @see #setSerializer(Class, SerializableFunction, SerializableFunction)
     *
     * @param <FIELDVALUE>
     *            the value type of the field
     * @param field
     *            the field to be bound, not <code>null</code>
     * @param fieldType
     * @return the new binding builder
     *
     * @since 1.0
     */
    public <FIELDVALUE> BindingBuilder<BEAN, FIELDVALUE> forMemberField(
            HasValue<?, FIELDVALUE> field, Class<FIELDVALUE> fieldType) {
        getTypeConfigurationOrThrow(fieldType).store(field);
        return forMemberField(field);
    }

    /**
     * Creates a new binding for the given (multi select) field whose value type
     * is a collection. The returned builder may be further configured before
     * invoking {@link #bindInstanceFields(Object)}. Unlike with the
     *
     * {@link #forField(HasValue)} method, no explicit call to
     * {@link BindingBuilder#bind(String)} is needed to complete this binding in
     * the case that the name of the field matches a field name found in the
     * bean.
     * <p>
     * The field value will be sent over the network to synchronize the value
     * with other users also editing the same field. This method allows
     * explicitly defining the collection type and element type to use.
     *
     * @see #forMemberField(HasValue)
     * @see #forMemberField(HasValue, Class)
     * @see #setSerializer(Class, SerializableFunction, SerializableFunction)
     *
     * @param <FIELDVALUE>
     *            the base type of the collection, e.g. {@code Set} for
     *            {@code CheckboxGroup<String>}
     * @param <ELEMENT>
     *            the type of the elements in the collection, e.g.
     *            {@code String} for {@code CheckboxGroup<String>}
     * @param field
     *            the field to be bound, not <code>null</code>
     * @param collectionType
     *            the base type of the collection, e.g. {@code Set.class} for
     *            {@code CheckboxGroup<String>}, not <code>null</code>
     * @param elementType
     *            the type of the elements in the collection, e.g.
     *            {@code String.class} for {@code CheckboxGroup<String>}, not
     *            <code>null</code>
     * @return the new binding builder
     *
     * @since 1.0
     */
    public <FIELDVALUE extends Collection<ELEMENT>, ELEMENT> BindingBuilder<BEAN, FIELDVALUE> forMemberField(
            HasValue<?, FIELDVALUE> field,
            Class<? super FIELDVALUE> collectionType,
            Class<ELEMENT> elementType) {
        getTypeConfiguration(collectionType, elementType).store(field);
        return forMemberField(field);
    }

    /**
     * Sets a custom serializer and deserializer to use for a specific value
     * type. The serializer and deserializer will be used for all field bindings
     * that implicitly or explicitly use that type either as the field type or
     * as the collection element type in case of multi select fields. It is not
     * allowed to reconfigure the serializer and deserializer for a previously
     * configued type nor for any of the basic types that are supported without
     * custom logic.
     * <p>
     * Field values will be sent over the network to synchronize the value with
     * other users also editing the same field. This method allows defining
     * callbacks to convert between the field value and the value that is sent
     * over the network. This is necessary when using complex objects that are
     * not suitable to be sent as-is over the network.
     *
     * @param <T>
     *            the type handled by the serializer
     * @param type
     *            the type for which to set a serializer and deserializer, not
     *            <code>null</code>
     * @param serializer
     *            a callback that receives a non-empty field value and returns
     *            the value to send over the network (not <code>null</code>).
     *            The callback cannot be <code>null</code>.
     * @param deserializer
     *            a callback that receives a value produced by the serializer
     *            callback (not <code>null</code>) and returns the field value
     *            to use. The callback cannot be <code>null</code>.
     * @since 1.0
     */
    public <T> void setSerializer(Class<T> type,
            SerializableFunction<T, String> serializer,
            SerializableFunction<String, T> deserializer) {
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(serializer, "Serializer cannot be null");
        Objects.requireNonNull(deserializer, "Deserializer cannot be null");

        /*
         * Cannot allow changing an existing serializer on the fly because we
         * might then not be able to deserialize an existing value
         */
        if (isAssignableFromAny(SUPPORTED_CLASS_TYPES, type)
                || isAssignableFromAny(SUPPORTED_COLLECTION_TYPES, type)) {
            throw new IllegalArgumentException(
                    "Cannot set a custom serializer for a type that has built-in support.");
        }

        if (typeConfigurations.containsKey(type)) {
            throw new IllegalStateException(
                    "Serializer has already been set for the type "
                            + type.getName() + ".");
        }

        typeConfigurations.put(type,
                new JsonHandler<T>(
                        value -> new TextNode(serializer.apply(value)),
                        jsonNode -> deserializer.apply(jsonNode.asText())));
    }

    private <T> Optional<JsonHandler<T>> getTypeConfiguration(
            Class<T> fieldType) {
        @SuppressWarnings("unchecked")
        JsonHandler<T> configuration = (JsonHandler<T>) typeConfigurations
                .get(fieldType);
        if (configuration != null) {
            return Optional.of(configuration);
        } else if (isSupportedType(fieldType)) {
            return Optional.of(JsonHandler.forBasicType(fieldType));
        } else {
            return Optional.empty();
        }
    }

    private <ELEMENT> JsonHandler<ELEMENT> getTypeConfigurationOrThrow(
            Class<ELEMENT> fieldType) {
        return getTypeConfiguration(fieldType)
                .orElseThrow(() -> new IllegalStateException(
                        createTypeNotSupportedMessage(fieldType)));
    }

    private <ELEMENT, FIELDVALUE extends Collection<ELEMENT>> JsonHandler<FIELDVALUE> getTypeConfiguration(
            Class<? super FIELDVALUE> collectionType,
            Class<ELEMENT> elementType) {
        if (!isAssignableFromAny(SUPPORTED_COLLECTION_TYPES, collectionType)) {
            throw new IllegalArgumentException(collectionType
                    + " is not supported as a collection. Must use a type assignable to one of "
                    + SUPPORTED_COLLECTION_TYPES);
        }

        JsonHandler<ELEMENT> elementConfiguration = getTypeConfigurationOrThrow(
                elementType);

        return new JsonHandler<>(collection -> {
            ArrayNode arrayNode = JsonUtil.getObjectMapper().createArrayNode();

            collection.forEach(element -> arrayNode
                    .add(elementConfiguration.serialize(element)));

            return arrayNode;
        }, json -> {
            /*
             * Deserialize an empty array of the expected type to reuse
             * Jackson's logic for creating arbitrary collections.
             */
            @SuppressWarnings("unchecked")
            FIELDVALUE collection = (FIELDVALUE) JsonUtil.toInstance(
                    JsonUtil.getObjectMapper().createArrayNode(),
                    collectionType);

            /*
             * Then deserialize each element according to our json handler and
             * add it to the collection
             */
            ((ArrayNode) json).forEach(elementJson -> collection
                    .add(elementConfiguration.deserialize(elementJson)));

            return collection;
        });
    }

    /**
     * Gets the optional expiration timeout of this binder. An empty
     * {@link Optional} is returned if no timeout is set, which means the binder
     * is not cleared when there are no connected users to the related topic
     * (this is the default).
     *
     * @return the expiration timeout
     *
     * @since 3.1
     */
    @Override
    public Optional<Duration> getExpirationTimeout() {
        return Optional.ofNullable(expirationTimeout);
    }

    /**
     * Sets the expiration timeout of this binder. If set, this binder data is
     * cleared when {@code expirationTimeout} has passed after the last
     * connection to the related topic is closed. If set to {@code null}, the
     * timeout is cancelled.
     *
     * @param expirationTimeout
     *            the expiration timeout
     *
     * @since 3.1
     */
    @Override
    public void setExpirationTimeout(Duration expirationTimeout) {
        this.expirationTimeout = expirationTimeout;

        if (formManager != null) {
            formManager.setExpirationTimeout(expirationTimeout);
        }
    }
}
