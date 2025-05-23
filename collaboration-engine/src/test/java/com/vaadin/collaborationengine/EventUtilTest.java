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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class EventUtilTest {
    private static class SpyRunnable implements Runnable {
        private boolean invoked = false;

        @Override
        public void run() {
            if (invoked) {
                throw new RuntimeException("Already invoked");
            }
            invoked = true;
        }

        public boolean isInvoked() {
            return invoked;
        }

    }

    @Test
    public void basicListeners_fireEvents_listenersAreInvoked() {
        List<SpyRunnable> listeners = Collections.unmodifiableList(
                Arrays.asList(new SpyRunnable(), new SpyRunnable()));

        EventUtil.fireEvents(listeners, Runnable::run, false);

        for (SpyRunnable spyRunnable : listeners) {
            Assert.assertTrue(spyRunnable.isInvoked());
        }
    }

    @Test
    public void modifyListFromListener_fireEvents_noExceptionAndNewListenerNotRun() {
        ArrayList<Runnable> listeners = new ArrayList<>();
        listeners.add(() -> listeners
                .add(() -> Assert.fail("Should not be invoked")));

        EventUtil.fireEvents(listeners, Runnable::run, false);

        Assert.assertEquals(2, listeners.size());
    }

    @Test
    public void failingListener_fireEvents_otherListenersAreRun() {
        List<SpyRunnable> listeners = Arrays.asList(new SpyRunnable(),
                new SpyRunnable());
        // Set up to fail on next invocation
        listeners.get(0).run();

        try {
            EventUtil.fireEvents(listeners, Runnable::run, false);
            Assert.fail("Expection expected");
        } catch (RuntimeException e) {
            // Expected
        }

        for (SpyRunnable spyRunnable : listeners) {
            Assert.assertTrue(spyRunnable.isInvoked());
        }
    }

    @Test
    public void multipleFailingListeners_fireEvents_allExceptionsCollected() {
        List<Runnable> listeners = Arrays.asList(() -> {
            throw new RuntimeException("1");
        }, () -> {
            throw new RuntimeException("2");
        });

        try {
            EventUtil.fireEvents(listeners, Runnable::run, false);

            Assert.fail("Expection expected");
        } catch (RuntimeException e) {
            Assert.assertEquals("1", e.getMessage());

            Throwable[] suppressed = e.getSuppressed();
            Assert.assertEquals(1, suppressed.length);
            Assert.assertEquals("2", suppressed[0].getMessage());
        }
    }

    @Test
    public void failingListener_fireEventsToRemoveFailing_listenerIsRemoved() {
        ArrayList<SpyRunnable> listeners = new ArrayList<>(
                Arrays.asList(new SpyRunnable(), new SpyRunnable()));
        listeners.get(0).run();

        try {
            EventUtil.fireEvents(listeners, Runnable::run, true);
            Assert.fail("Expection expected");
        } catch (RuntimeException e) {
            // Ignore
        }

        Assert.assertEquals(1, listeners.size());
        Assert.assertTrue(listeners.get(0).isInvoked());
    }
}
