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
package com.vaadin;

import java.util.UUID;
import java.util.function.Function;

import com.vaadin.collaborationengine.CollaborationAvatarGroup;
import com.vaadin.collaborationengine.CollaborationBinder;
import com.vaadin.collaborationengine.CollaborationEngine;
import com.vaadin.collaborationengine.CollaborationMap;
import com.vaadin.collaborationengine.TopicConnection;
import com.vaadin.collaborationengine.UserInfo;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.shared.communication.PushMode;

@Route("")
@CssImport("@vaadin/vaadin-lumo-styles/lumo.css")
@CssImport("./styles/shared-styles.css")
public class DemoView extends VerticalLayout {

    private static final String TOPIC_ID = "form";
    private static final String ACTIVITY_LOG_MAP_NAME = "activityLog";

    private static final String FIRST_NAME = "firstName";
    private static final String LAST_NAME = "lastName";

    public static class Person {
        private String firstName = "";
        private String lastName = "";

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        @Override
        public String toString() {
            return "Person [firstName=" + firstName + ", lastName=" + lastName
                    + "]";
        }
    }

    private Registration closeConnection;
    private CollaborationBinder<Person> binder;

    private final Div log = new Div();

    public DemoView() {
        addAttachListener(event -> event.getUI().getPushConfiguration()
                .setPushMode(PushMode.AUTOMATIC));
        addClassName("centered-content");
        log.setClassName("log");
        showLogin();
    }

    private void showLogin() {
        TextField usernameField = new TextField("Username");
        Button startButton = new Button("Start editing", event -> {
            String value = usernameField.getValue();
            if (!value.isEmpty()) {
                showPersonEditor(value);
            } else {
                Notification.show("Must enter a username to collaborate");
                usernameField.focus();
            }
        });
        startButton.addClickShortcut(Key.ENTER);

        removeAll();
        add(usernameField, startButton);
    }

    private void showPersonEditor(String username) {
        TextField firstName = new TextField("First name");
        TextField lastName = new TextField("Last name");

        removeAll();
        Button submitButton = new Button("Submit", event -> {
            Person person = new Person();
            if (binder.writeBeanIfValid(person)) {
                closeConnection.remove();
                Notification.show("Submit: " + person);
                showLogin();
            }
        });
        UserInfo user = new UserInfo(UUID.randomUUID().toString(), username);
        CollaborationAvatarGroup collaboratorsAvatars = new CollaborationAvatarGroup(
                user, TOPIC_ID);
        collaboratorsAvatars.setOwnAvatarVisible(false);

        Avatar ownAvatar = new Avatar();
        ownAvatar.addClassName("own-avatar");
        ownAvatar.setName(username);

        HorizontalLayout avatarLayout = new HorizontalLayout(
                collaboratorsAvatars, ownAvatar);
        avatarLayout.setWidthFull();
        avatarLayout.setSpacing(false);
        avatarLayout.setJustifyContentMode(JustifyContentMode.BETWEEN);

        add(avatarLayout, firstName, lastName, submitButton, log);

        binder = new CollaborationBinder<>(Person.class, user);
        binder.setTopic(TOPIC_ID, Person::new);
        binder.forField(firstName).bind(FIRST_NAME);
        binder.forField(lastName).bind(LAST_NAME);

        /*
         * Tie connection to submit button so that it's deactivated when
         * detaching the form
         */
        closeConnection = CollaborationEngine.getInstance().openTopicConnection(
                submitButton, TOPIC_ID, user,
                topic -> configureTopicConnection(topic, username, firstName,
                        lastName));
    }

    private Registration configureTopicConnection(TopicConnection topic,
            String username, TextField firstName, TextField lastName) {
        Registration firstNameFocusRegistration = firstName.addFocusListener(
                event -> logEditorFocused(topic, FIRST_NAME, username));
        Registration lastNameFocusRegistration = lastName.addFocusListener(
                event -> logEditorFocused(topic, LAST_NAME, username));

        Registration firstNameBlurRegistration = firstName.addBlurListener(
                event -> logEditorBlurred(topic, FIRST_NAME, username));
        Registration lastNameBlurRegistration = lastName.addBlurListener(
                event -> logEditorBlurred(topic, LAST_NAME, username));

        Registration valueChangeRegistration = binder
                .addValueChangeListener(e -> {
                    if (e.isFromClient()) {
                        log(topic,
                                username + " changed "
                                        + ((TextField) e.getHasValue())
                                                .getLabel()
                                        + " to " + e.getValue());
                    }
                });

        topic.getNamedMap(ACTIVITY_LOG_MAP_NAME)
                .subscribe(event -> log.setText(event.getValue(String.class)));

        log(topic, username + " joined");

        Registration editorRegistration = () -> log(topic, username + " left");

        return Registration.combine(firstNameFocusRegistration,
                lastNameFocusRegistration, firstNameBlurRegistration,
                lastNameBlurRegistration, valueChangeRegistration,
                editorRegistration);
    }

    private static void log(TopicConnection topic, String message) {
        DemoView.<String> updateMaps(topic.getNamedMap(ACTIVITY_LOG_MAP_NAME),
                "log", String.class, "", oldLog -> message + "\n" + oldLog);
    }

    private static <T> void updateMaps(CollaborationMap map, String key,
            Class<T> type, T nullValue, Function<T, T> updater) {
        T oldValue = map.get(key, type);
        T newValue = updater.apply(oldValue == null ? nullValue : oldValue);
        map.replace(key, oldValue, newValue).thenAccept(success -> {
            if (!success) {
                updateMaps(map, key, type, nullValue, updater);
            }
        });
    }

    private static void logEditorFocused(TopicConnection topicConnection,
            String fieldName, String username) {
        String message = username + " started editing " + fieldName;
        log(topicConnection, message);
    }

    private static void logEditorBlurred(TopicConnection topicConnection,
            String fieldName, String username) {
        String message = username + " stopped editing " + fieldName;
        log(topicConnection, message);
    }

}
