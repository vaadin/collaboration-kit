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

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vaadin.collaborationengine.TestUtil.TestCollaborationEngine;
import com.vaadin.collaborationengine.util.SpyActivationHandler;
import com.vaadin.collaborationengine.util.TestUtils;
import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.shared.Registration;

public class SystemConnectionContextTest {
    private static SerializableExecutor rejectExecutor = runnable -> Assert
            .fail("Should not dispatch");

    private TestCollaborationEngine ce;
    private SystemConnectionContext context;
    private SpyActivationHandler spy;
    /*
     * Tests that create their own thread pool can store the reference here to
     * ensure shutdown in @After
     */
    private ExecutorService executorService;

    @Before
    public void setup() {
        ce = TestUtil.createTestCollaborationEngine();
        context = ce.getSystemContext();
        spy = new SpyActivationHandler();
    }

    @After
    public void cleanup() {
        CurrentInstance.clearAll();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Test
    public void ceConfigured_getInstance_usesCeInstance() {
        VaadinService.setCurrent(ce.getVaadinService());

        SystemConnectionContext instance = SystemConnectionContext
                .getInstance();

        Assert.assertSame(instance, context);
        Assert.assertSame(ce, instance.getCollaborationEngine());
    }

    @Test(expected = IllegalStateException.class)
    public void ceNotConfigured_getInstance_throws() {
        SystemConnectionContext.getInstance();
    }

    @Test
    public void init_activatedImmediately() {
        context.init(spy, command -> ce.getExecutorService().execute(command));

        spy.assertActive("Init should activate immediately");
    }

    @Test
    public void initedContext_dispatchAction_runOnExecutor() {
        Thread thread = Thread.currentThread();
        AtomicInteger runCount = new AtomicInteger();

        context.init(spy, Runnable::run);

        spy.getActionDispatcher().dispatchAction(() -> {
            Assert.assertSame(thread, Thread.currentThread());
            runCount.incrementAndGet();
        });

        Assert.assertEquals(1, runCount.get());
    }

    @Test
    public void initedContext_dispatchAction_currentCeDefined() {
        // Sanity check
        Assert.assertNull(VaadinService.getCurrent());

        context.init(spy, Runnable::run);

        spy.getActionDispatcher().dispatchAction(() -> {
            Assert.assertSame(ce.getVaadinService(),
                    VaadinService.getCurrent());
            Assert.assertSame(ce, CollaborationEngine.getInstance());
        });
    }

    @Test
    public void initedContext_dispatchAction_currentInstancesRestored() {
        CurrentInstance.set(Integer.class, Integer.valueOf(0));
        context.init(spy, Runnable::run);

        spy.getActionDispatcher().dispatchAction(() -> {
            CurrentInstance.set(Integer.class, Integer.valueOf(1));
        });

        Assert.assertEquals(0, CurrentInstance.get(Integer.class).intValue());
        Assert.assertNull(VaadinService.getCurrent());
    }

    @Test(expected = IllegalStateException.class)
    public void initedContext_initWithSameHandler_throws() {
        context.init(spy, rejectExecutor);

        context.init(spy, rejectExecutor);
    }

    @Test
    public void initedContext_initWithAnotherHandler_bothActive() {
        SpyActivationHandler anotherSpy = new SpyActivationHandler();
        context.init(spy, rejectExecutor);

        context.init(anotherSpy, rejectExecutor);

        spy.assertActive("Original spy should be active");
        spy.assertActive("New spy should be active");
    }

    @Test
    public void initedContext_removeRegistration_dispatcherCleared() {
        Registration registration = context.init(spy, rejectExecutor);
        spy.assertActive("Sanity check");

        registration.remove();

        spy.assertInactive("Should be inactive when registration is removed");
    }

    @Test
    public void initedContext_doubleRemove_removedOnlyOnce() {
        Registration registration = context.init(spy, rejectExecutor);
        spy.assertActive("Sanity check");

        registration.remove();

        // Spy would throw for unexpected change if it's invoked again
        registration.remove();
    }

    @Test
    public void contextWithTwoHandlers_removeRegistration_otherHandlerActive() {
        SpyActivationHandler anotherSpy = new SpyActivationHandler();
        Registration registration = context.init(spy, rejectExecutor);
        context.init(anotherSpy, rejectExecutor);

        spy.assertActive("Sanity check");
        anotherSpy.assertActive("Sanity check");

        registration.remove();

        anotherSpy
                .assertActive("Spy that was not removed should remain active");
    }

    @Test
    public void contextWithTwoHandlers_serviceDestroy_bothInactive() {
        SpyActivationHandler anotherSpy = new SpyActivationHandler();
        Registration registration = context.init(spy, rejectExecutor);
        context.init(anotherSpy, rejectExecutor);

        spy.assertActive("Sanity check");
        anotherSpy.assertActive("Sanity check");

        ce.getVaadinService().destroy();

        spy.assertInactive("Should be inactive when service is destroyed");
        anotherSpy
                .assertInactive("Should be inactive when service is destroyed");

        // Spy would throw for unexpected change if it's invoked again
        registration.remove();
    }

    @Test
    public void initAndRemoveHandler_clearReferences_garbageCollected()
            throws InterruptedException {
        Registration registration = context.init(spy, rejectExecutor);
        WeakReference<SystemConnectionContext> reference = new WeakReference<>(
                context);
        spy.assertActive("Sanity check");

        registration.remove();
        registration = null;
        context = null;
        ce = null;

        Assert.assertTrue(TestUtils.isGarbageCollected(reference));
    }

    @Test
    public void unusedContext_clearReference_notReferencedThroughCe()
            throws InterruptedException {
        SystemConnectionContext context = new SystemConnectionContext(
                () -> TestCollaborationEngine.getInstance());
        WeakReference<SystemConnectionContext> reference = new WeakReference<>(
                context);

        context = null;

        Assert.assertTrue(TestUtils.isGarbageCollected(reference));
    }

    @Test
    public void singleActivation_multipleTasks_runInSequence()
            throws InterruptedException {
        executorService = Executors.newCachedThreadPool();
        AtomicInteger executionCount = new AtomicInteger();
        AtomicBoolean executionActive = new AtomicBoolean();
        AtomicReference<String> error = new AtomicReference<>();

        context.init(spy, command -> executorService.execute(command));

        for (int i = 0; i < 100; i++) {
            final int currentExecution = i;
            spy.getActionDispatcher().dispatchAction(() -> {
                if (error.get() != null) {
                    return;
                }

                if (executionActive.getAndSet(true)) {
                    error.set("Another execution was active");
                    return;
                }

                int currentCount = executionCount.getAndIncrement();
                if (currentCount != currentExecution) {
                    error.set("Expected count " + currentExecution + " but got "
                            + currentCount);
                }

                executionActive.set(false);
            });
        }

        executorService.shutdown();
        Assert.assertTrue(
                executorService.awaitTermination(10, TimeUnit.MILLISECONDS));

        Assert.assertNull(error.get());
    }

    @Test
    public void multipleActivations_multipleTasks_runIndependently()
            throws InterruptedException {
        executorService = Executors.newCachedThreadPool();
        AtomicBoolean executionActive = new AtomicBoolean();
        AtomicInteger concurrentCount = new AtomicInteger();

        for (int i = 0; i < 5; i++) {
            SpyActivationHandler spy = new SpyActivationHandler();
            context.init(spy, command -> executorService.execute(command));

            spy.getActionDispatcher().dispatchAction(() -> {
                if (executionActive.getAndSet(true)) {
                    concurrentCount.incrementAndGet();
                }

                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                executionActive.set(false);
            });
        }

        executorService.shutdown();
        Assert.assertTrue(
                executorService.awaitTermination(10, TimeUnit.MILLISECONDS));

        Assert.assertTrue(concurrentCount.get() > 0);
    }

    @Test
    public void serializeContext() {
        SystemConnectionContext deserializedContext = TestUtils
                .serialize(context);
    }
}
