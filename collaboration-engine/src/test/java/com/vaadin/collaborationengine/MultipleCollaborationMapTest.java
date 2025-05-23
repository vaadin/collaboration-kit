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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vaadin.collaborationengine.util.MockService;
import com.vaadin.collaborationengine.util.TestUtils;
import com.vaadin.flow.server.VaadinService;

public class MultipleCollaborationMapTest {

    private VaadinService service = new MockService();
    private CollaborationEngine ce;
    private TopicConnection connection;
    private CollaborationMap namedMapData;

    @Before
    public void init() {
        VaadinService.setCurrent(service);
        ce = TestUtil.createTestCollaborationEngine(service);
        TestUtils.openEagerConnection("form", topic -> {
            this.connection = topic;
            namedMapData = topic.getNamedMap("values");
        });
    }

    @After
    public void cleanUp() {
        VaadinService.setCurrent(null);
    }

    @Test
    public void namedMap_put_newElementAdded() {
        namedMapData.put("firstName", "foo");
        Assert.assertEquals("foo", connection.getNamedMap("values")
                .get("firstName", String.class));
    }

    @Test
    public void namedMap_subscribe_mapChangeEventFired() {
        AtomicReference<MapChangeEvent> mapChangeEvent = new AtomicReference<>();
        namedMapData.subscribe(mapChangeEvent::set);

        connection.getNamedMap("values").put("foo", "bar");
        Assert.assertEquals("foo", mapChangeEvent.get().getKey());
        Assert.assertEquals("bar", mapChangeEvent.get().getValue(String.class));
    }

    @Test
    public void namedMap_concurrentPut_newElementsAdded()
            throws InterruptedException {
        List<Integer> resultList = parallelPut(namedMapData, 100);
        Assert.assertEquals(1, resultList.stream().distinct().count());
        long wrongResultCount = resultList.stream().filter(num -> num != 1000)
                .count();
        Assert.assertEquals(wrongResultCount, 0);
    }

    private List<Integer> parallelPut(CollaborationMap map, int executionTimes)
            throws InterruptedException {
        int[] count = { 0 };
        map.subscribe(e -> count[0]++);
        List<Integer> resultList = new ArrayList<>();

        AtomicInteger tempVal = new AtomicInteger(0);
        for (int i = 0; i < executionTimes; i++) {
            ExecutorService executor = Executors.newFixedThreadPool(5);
            for (int j = 0; j < 10; j++) {
                executor.execute(() -> {
                    for (int k = 0; k < 100; k++) {
                        map.put("foo", tempVal.incrementAndGet());
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            tempVal.set(0);
            resultList.add(count[0]);
            count[0] = 0;
        }
        return resultList;
    }

}
