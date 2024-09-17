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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.vaadin.collaborationengine.CollaborationEngine;
import com.vaadin.collaborationengine.CollaborationEngineConfiguration;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServiceInitListener;

public class BackendConfigurer implements VaadinServiceInitListener {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(BackendConfigurer.class);

    @Override
    public void serviceInit(ServiceInitEvent serviceEvent) {
        VaadinService service = serviceEvent.getSource();

        CollaborationEngineConfiguration configuration = new CollaborationEngineConfiguration();

        if ("hazelcast".equals(System.getProperty("ce.clustering"))) {
            HazelcastInstance hz = Hazelcast.newHazelcastInstance();

            service.addServiceDestroyListener(event -> hz.shutdown());

            configuration.setBackend(new HazelcastBackend(hz));
        }

        CollaborationEngine.configure(service, configuration);
    }

}
