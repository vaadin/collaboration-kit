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

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * Pins down which types are marked as deprecated now that Collaboration Kit is
 * superseded by Vaadin Signals.
 * <p>
 * Only the entry points an application uses to start using Collaboration Kit
 * are deprecated. The types that are merely passed around once one of those
 * entry points is in use are deliberately left alone, so that application code
 * gets one warning per feature it needs to migrate instead of one warning per
 * declaration.
 */
@SuppressWarnings("deprecation")
public class DeprecatedEntryPointsTest {

    /**
     * Types that application code has to name in order to start using a
     * Collaboration Kit feature.
     */
    private static final List<Class<?>> ENTRY_POINTS = List.of(
            CollaborationEngine.class, CollaborationEngineConfiguration.class,
            CollaborationAvatarGroup.class, CollaborationBinder.class,
            CollaborationBinderUtil.class, CollaborationMessageInput.class,
            CollaborationMessageList.class, FormManager.class,
            MessageManager.class, PresenceManager.class);

    /**
     * Types that are only reachable through an entry point, and which are
     * therefore not deprecated on their own.
     */
    private static final List<Class<?>> SUPPORTING_TYPES = List.of(
            UserInfo.class, SystemUserInfo.class, TopicConnection.class,
            TopicConnectionRegistration.class, CollaborationMap.class,
            CollaborationList.class, CollaborationMessage.class,
            ConnectionContext.class, ComponentConnectionContext.class,
            SystemConnectionContext.class, Backend.class, LocalBackend.class,
            EntryScope.class);

    @Test
    public void entryPoints_deprecatedForRemoval() {
        for (Class<?> entryPoint : ENTRY_POINTS) {
            Deprecated deprecated = entryPoint.getAnnotation(Deprecated.class);
            Assert.assertNotNull(
                    entryPoint.getSimpleName()
                            + " is an entry point and should be deprecated",
                    deprecated);
            Assert.assertTrue(
                    entryPoint.getSimpleName()
                            + " should be deprecated for removal",
                    deprecated.forRemoval());
            Assert.assertFalse(entryPoint.getSimpleName()
                    + " should tell since which version it is deprecated",
                    deprecated.since().isEmpty());
        }
    }

    @Test
    public void supportingTypes_notDeprecated() {
        for (Class<?> supportingType : SUPPORTING_TYPES) {
            Assert.assertNull(supportingType.getSimpleName()
                    + " is not an entry point and should not be deprecated,"
                    + " to avoid a deprecation warning on every declaration in"
                    + " application code",
                    supportingType.getAnnotation(Deprecated.class));
        }
    }

    @Test
    public void package_deprecatedForRemoval() {
        Deprecated deprecated = CollaborationEngine.class.getPackage()
                .getAnnotation(Deprecated.class);
        Assert.assertNotNull(
                "The collaborationengine package should be deprecated",
                deprecated);
        Assert.assertTrue(
                "The collaborationengine package should be deprecated for removal",
                deprecated.forRemoval());
    }
}
