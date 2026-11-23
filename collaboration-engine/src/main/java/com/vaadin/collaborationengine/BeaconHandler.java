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

import java.io.ObjectStreamException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.server.SynchronizedRequestHandler;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.shared.ApplicationConstants;
import com.vaadin.flow.shared.Registration;

/**
 * A {@link SynchronizedRequestHandler} which notifies its listeners when the
 * browser tab is closed.
 *
 * @author Vaadin Ltd
 * @since 1.0
 */
class BeaconHandler extends SynchronizedRequestHandler {
    private static final String ID_PARAMETER = "id";
    private static final String REQUEST_TYPE = "beacon";

    private final String id = UUID.randomUUID().toString();
    private final List<Command> listeners;
    private final String beaconPath;

    public BeaconHandler(String beaconPath) {
        listeners = new ArrayList<>();
        this.beaconPath = beaconPath;
    }

    /*
     * An instance of this class is stored in the Vaadin session, causing
     * session serialization to fail. In order to solve this issue, we replace
     * it with null during serialization and then replace the session value with
     * a new instance during deserialization. See
     * ComponentConnectionContext.attach(UI).
     */
    @Serial
    private Object writeReplace() throws ObjectStreamException {
        return null;
    }

    static BeaconHandler ensureInstalled(UI ui, String beaconPath) {
        BeaconHandler beaconHandler = ComponentUtil.getData(ui,
                BeaconHandler.class);
        if (beaconHandler != null) {
            // Already installed, return the existing handler
            return beaconHandler;
        }

        BeaconHandler newBeaconHandler = new BeaconHandler(beaconPath);

        ui.getElement().executeJs(getUnloadScript(),
                newBeaconHandler.createBeaconUrl());

        VaadinSession session = ui.getSession();
        session.removeRequestHandler(null);
        session.addRequestHandler(newBeaconHandler);
        ComponentUtil.setData(ui, BeaconHandler.class, newBeaconHandler);

        ui.addDetachListener(
                detachEvent -> session.removeRequestHandler(newBeaconHandler));
        return newBeaconHandler;
    }

    String createBeaconUrl() {
        String requestTypeParameter = formatParameter(
                ApplicationConstants.REQUEST_TYPE_PARAMETER, REQUEST_TYPE);
        String beaconIdParameter = formatParameter(ID_PARAMETER, id);
        return "." + beaconPath + "?" + requestTypeParameter + "&"
                + beaconIdParameter;

    }

    private static String formatParameter(String name, String value) {
        return name + "=" + value;
    }

    private static String getUnloadScript() {
        // Uses 'pagehide' since browsers are removing support for the
        // 'unload' event, e.g. recent Chrome versions no longer fire it
        //@formatter:off
        return "window.addEventListener('pagehide', function() {"
                + "  if (navigator.sendBeacon) {"
                + "    navigator.sendBeacon($0);"
                + "  } else {"
                + "    var xhr = new XMLHttpRequest();"
                + "    xhr.open(\"POST\", $0, false);"
                + "    xhr.send(\"\");"
                + "  }"
                + "})";
        //@formatter:on
    }

    @Override
    protected boolean canHandleRequest(VaadinRequest request) {
        if (!beaconPath.equals(request.getPathInfo())) {
            return false;
        }
        String requestType = request
                .getParameter(ApplicationConstants.REQUEST_TYPE_PARAMETER);
        String beaconId = request.getParameter(ID_PARAMETER);
        return REQUEST_TYPE.equals(requestType) && id.equals(beaconId);
    }

    @Override
    public boolean synchronizedHandleRequest(VaadinSession session,
            VaadinRequest request, VaadinResponse response) {
        new ArrayList<>(listeners).forEach(Command::execute);
        return true;
    }

    Registration addListener(Command listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    List<Command> getListeners() {
        return new ArrayList<>(listeners);
    }
}
