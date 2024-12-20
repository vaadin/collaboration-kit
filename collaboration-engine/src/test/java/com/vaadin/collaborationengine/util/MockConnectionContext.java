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
package com.vaadin.collaborationengine.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import com.vaadin.collaborationengine.ActionDispatcher;
import com.vaadin.collaborationengine.ActivationHandler;
import com.vaadin.collaborationengine.ConnectionContext;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.shared.Registration;

public class MockConnectionContext implements ConnectionContext {

    private transient ActivationHandler activationHandler;

    boolean closed = false;

    boolean throwOnClose = false;

    private boolean eager;

    private ActionDispatcher actionDispatcher = new MockActionDispatcher();

    private transient Executor executor;

    private boolean executorExplicitlySet;

    private AtomicInteger actionDispatchCount = new AtomicInteger();

    public ActivationHandler getActivationHandler() {
        return activationHandler;
    }

    public void setEager(boolean eager) {
        this.eager = eager;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
        executorExplicitlySet = true;
    }

    @Override
    public Registration init(ActivationHandler activationHandler,
            Executor executor) {

        this.activationHandler = activationHandler;
        if (!executorExplicitlySet) {
            this.executor = executor;
        }
        if (eager) {
            activate();
        }
        return () -> {
            deactivate();
            closed = true;
            if (throwOnClose) {
                throw new FailOnPurposeException();
            }
        };
    }

    public int getDispatchActionCount() {
        return actionDispatchCount.get();
    }

    public void resetActionDispatchCount() {
        actionDispatchCount.set(0);
    }

    public void activate() {
        activationHandler.accept(actionDispatcher);
    }

    public void deactivate() {
        activationHandler.accept(null);
    }

    public boolean isThrowOnClose() {
        return throwOnClose;
    }

    public void setThrowOnClose(boolean throwOnClose) {
        this.throwOnClose = throwOnClose;
    }

    public boolean isClosed() {
        return closed;
    }

    public ActionDispatcher getActionDispatcher() {
        return actionDispatcher;
    }

    public static class FailOnPurposeException extends RuntimeException {
    }

    public static MockConnectionContext createEager() {
        MockConnectionContext context = new MockConnectionContext();
        context.setEager(true);
        return context;
    }

    public class MockActionDispatcher implements ActionDispatcher {

        private final List<Command> actions = new ArrayList<>();

        private boolean hold;

        public void hold() {
            hold = true;
        }

        public void release() {
            hold = false;
            actions.forEach(this::execute);
            actions.clear();
        }

        @Override
        public <T> CompletableFuture<T> createCompletableFuture() {
            return new CompletableFuture<>();
        }

        @Override
        public void dispatchAction(Command action) {
            if (hold) {
                actions.add(action);
            } else {
                execute(action);
            }
        }

        private void execute(Command action) {
            actionDispatchCount.incrementAndGet();
            executor.execute(action::execute);
        }
    }
}
