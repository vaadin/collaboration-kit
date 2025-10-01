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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import com.vaadin.collaborationengine.util.TestEnum;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

public class MockJson {
    public static final JsonNode FOO = JsonUtil.toJsonNode("foo");
    public static final JsonNode BAZ = JsonUtil.toJsonNode("baz");
    public static final JsonNode QUX = JsonUtil.toJsonNode("qux");

    public static final TypeReference<List<String>> LIST_STRING_TYPE_REF = new TypeReference<List<String>>() {
    };
    public static final TypeReference<List<Boolean>> LIST_BOOLEAN_TYPE_REF = new TypeReference<List<Boolean>>() {
    };
    public static final TypeReference<List<Integer>> LIST_INTEGER_TYPE_REF = new TypeReference<List<Integer>>() {
    };
    public static final TypeReference<List<Double>> LIST_DOUBLE_TYPE_REF = new TypeReference<List<Double>>() {
    };

    public static final TypeReference<List<BigDecimal>> LIST_BIG_DECIMAL_TYPE_REF = new TypeReference<List<BigDecimal>>() {
    };
    public static final TypeReference<List<LocalDate>> LIST_LOCAL_DATE_TYPE_REF = new TypeReference<List<LocalDate>>() {
    };
    public static final TypeReference<List<LocalTime>> LIST_LOCAL_TIME_TYPE_REF = new TypeReference<List<LocalTime>>() {
    };
    public static final TypeReference<List<LocalDateTime>> LIST_LOCAL_DATE_TIME_TYPE_REF = new TypeReference<List<LocalDateTime>>() {
    };
    public static final TypeReference<List<TestEnum>> LIST_TEST_ENUM_TYPE_REF = new TypeReference<List<TestEnum>>() {
    };

    public static final TypeReference<Set<String>> SET_STRING_TYPE_REF = new TypeReference<Set<String>>() {
    };
    public static final TypeReference<Set<Boolean>> SET_BOOLEAN_TYPE_REF = new TypeReference<Set<Boolean>>() {
    };
    public static final TypeReference<Set<Integer>> SET_INTEGER_TYPE_REF = new TypeReference<Set<Integer>>() {
    };
    public static final TypeReference<Set<Double>> SET_DOUBLE_TYPE_REF = new TypeReference<Set<Double>>() {
    };

    public static final TypeReference<Set<BigDecimal>> SET_BIG_DECIMAL_TYPE_REF = new TypeReference<Set<BigDecimal>>() {
    };
    public static final TypeReference<Set<LocalDate>> SET_LOCAL_DATE_TYPE_REF = new TypeReference<Set<LocalDate>>() {
    };
    public static final TypeReference<Set<LocalTime>> SET_LOCAL_TIME_TYPE_REF = new TypeReference<Set<LocalTime>>() {
    };
    public static final TypeReference<Set<LocalDateTime>> SET_LOCAL_DATE_TIME_TYPE_REF = new TypeReference<Set<LocalDateTime>>() {
    };
    public static final TypeReference<Set<TestEnum>> SET_TEST_ENUM_TYPE_REF = new TypeReference<Set<TestEnum>>() {
    };
}
