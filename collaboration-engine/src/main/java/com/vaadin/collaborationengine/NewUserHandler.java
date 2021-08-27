/*
 * Copyright (C) 2020 Vaadin Ltd
 *
 * This program is available under Commercial Vaadin Runtime License 1.0
 * (CVRLv1).
 *
 * For the full License, see http://vaadin.com/license/cvrl-1
 */
package com.vaadin.collaborationengine;

import java.io.Serializable;

import com.vaadin.flow.shared.Registration;

/**
 * Functional interface that defines how to handle a user when it becomes
 * present in a topic.
 *
 * @see PresenceManager#setNewUserHandler(NewUserHandler)
 * @author Vaadin Ltd
 */
@FunctionalInterface
public interface NewUserHandler extends Serializable {

    /**
     * Handles a user when it becomes present in a topic.
     *
     * @param user
     *            the user that becomes present
     * @return a registration that will be removed when the user stops being
     *         present
     */
    Registration handleNewUser(UserInfo user);
}