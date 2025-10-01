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

import java.util.UUID;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.shared.communication.PushMode;

import tools.jackson.databind.JsonNode;

/**
 * The main view contains a button and a collaborative div which shows the
 * number of clicks
 */
@Route("")
public class MainView extends VerticalLayout {

    private UserInfo user = new UserInfo(UUID.randomUUID().toString());
    private VerticalLayout editor = new VerticalLayout();
    private Button button = new Button("Increase");
    private Span span = new Span();

    private Registration connectionRegistration;

    public MainView() {
        addAttachListener(event -> event.getUI().getPushConfiguration()
                .setPushMode(PushMode.AUTOMATIC));
        openConnection();

        editor.add(button, span);

        HorizontalLayout activationButtons = new HorizontalLayout();
        Button detachButton = new Button("Detach editor", e -> remove(editor));
        Button reattachButton = new Button("Reattach editor",
                e -> addComponentAsFirst(editor));
        activationButtons.add(detachButton, reattachButton);

        HorizontalLayout connectionButtons = new HorizontalLayout();
        Button closeButton = new Button("Close connection",
                e -> connectionRegistration.remove());
        Button reopenButton = new Button("Reopen connection",
                e -> openConnection());
        connectionButtons.add(closeButton, reopenButton);

        add(editor, new Hr(), activationButtons, new Hr(), connectionButtons);
    }

    private void openConnection() {
        connectionRegistration = CollaborationEngine.getInstance()
                .openTopicConnection(editor, MainView.class.getName(), user,
                        topic -> {
                            CollaborationMap map = topic.getNamedMap("values");
                            if (map.get("value", JsonNode.class) == null) {
                                map.put("value", 0);
                            }

                            map.subscribe(event -> span
                                    .setText(event.getValue(String.class)));

                            return button.addClickListener(e -> {
                                Thread update = new Thread(() -> {
                                    Integer newState = map.get("value",
                                            Integer.class) + 1;
                                    map.put("value", newState);
                                });
                                update.start();
                            });
                        });
    }

}
