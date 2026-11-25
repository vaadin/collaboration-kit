/*
 * Copyright 2000-2026 Vaadin Ltd.
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

import com.vaadin.flow.js.JsDefinition;
import com.vaadin.flow.js.JsExpression;
import com.vaadin.flow.js.JsInvokers;

/**
 * The client-side JavaScript of {@link BeaconHandler}, declared as a
 * {@link JsDefinition} so that it is compiled into the frontend bundle and runs
 * under a content security policy that does not allow <code>unsafe-eval</code>.
 *
 * @author Vaadin Ltd
 */
@JsDefinition
interface BeaconHandlerJs extends JsInvokers {

    /**
     * Registers a listener that notifies the server through the given beacon
     * URL when the page is hidden, i.e. when the browser tab is closed or
     * navigated away from.
     * <p>
     * Uses 'pagehide' since browsers are removing support for the 'unload'
     * event, e.g. recent Chrome versions no longer fire it.
     *
     * @param beaconUrl
     *            the URL to send the beacon to
     */
    @JsExpression("""
            window.addEventListener('pagehide', function() {
              if (navigator.sendBeacon) {
                navigator.sendBeacon($0);
              } else {
                var xhr = new XMLHttpRequest();
                xhr.open("POST", $0, false);
                xhr.send("");
              }
            });""")
    void installUnloadBeacon(String beaconUrl);
}
