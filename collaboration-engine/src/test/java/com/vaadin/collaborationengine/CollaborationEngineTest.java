package com.vaadin.collaborationengine;

import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.vaadin.collaborationengine.util.EagerConnectionContext;
import com.vaadin.collaborationengine.util.MockService;
import com.vaadin.collaborationengine.util.SpyConnectionContext;
import com.vaadin.collaborationengine.util.TestUtils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.shared.Registration;

import static com.vaadin.collaborationengine.CollaborationEngine.USER_COLOR_COUNT;

public class CollaborationEngineTest {

    private VaadinService service = new MockService();
    private CollaborationEngine collaborationEngine;
    private ConnectionContext context;
    private SerializableFunction<TopicConnection, Registration> connectionCallback;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void init() {
        VaadinService.setCurrent(service);
        collaborationEngine = TestUtil.createTestCollaborationEngine(service);

        context = new EagerConnectionContext();
        connectionCallback = topicConnection -> () -> {
            // no impl
        };
    }

    @After
    public void cleanUp() {
        VaadinService.setCurrent(null);
    }

    @Test
    public void correctVersionPassedToUsageStatistics() {
        String artifactVersion = System
                .getProperty("collaboration-engine.version");

        if (artifactVersion == null) {
            String javaCommand = System.getProperty("sun.java.command");
            Assume.assumeFalse(
                    "Maven version property is not set when run through Eclipse",
                    javaCommand != null && javaCommand.startsWith(
                            "org.eclipse.jdt.internal.junit.runner.RemoteTestRunner"));
        }

        Assert.assertThat(artifactVersion, CoreMatchers
                .startsWith(CollaborationEngine.COLLABORATION_ENGINE_VERSION));
    }

    @Test
    public void getInstance_notNull() {
        collaborationEngine.openTopicConnection(context, "foo",
                SystemUserInfo.getInstance(), topicConnection -> {
                    Assert.assertNotNull(topicConnection);
                    return null;
                });
    }

    @Test
    public void getInstance_returnsAlwaysSameInstance() {
        Assert.assertSame(CollaborationEngine.getInstance(service),
                CollaborationEngine.getInstance(service));
    }

    @Test(expected = NullPointerException.class)
    public void openTopicConnectionWithNullComponent_throws() {
        collaborationEngine.openTopicConnection((Component) null, "foo",
                SystemUserInfo.getInstance(), connectionCallback);
    }

    @Test(expected = NullPointerException.class)
    public void openTopicConnectionWithNullContext_throws() {
        collaborationEngine.openTopicConnection((ConnectionContext) null, "foo",
                SystemUserInfo.getInstance(), connectionCallback);
    }

    @Test(expected = NullPointerException.class)
    public void openTopicConnectionWithNullId_throws() {
        collaborationEngine.openTopicConnection(context, null,
                SystemUserInfo.getInstance(), connectionCallback);
    }

    @Test(expected = NullPointerException.class)
    public void openTopicConnection_nullUserInfo_throws() {
        collaborationEngine.openTopicConnection(context, "foo", null,
                connectionCallback);
    }

    @Test(expected = NullPointerException.class)
    public void openTopicConnectionWithNullCallback_throws() {
        collaborationEngine.openTopicConnection(context, "foo",
                SystemUserInfo.getInstance(), null);
    }

    @Test
    public void openTopicConnection_sameTopicId_hasSameTopic() {
        Topic[] topics = new Topic[2];
        collaborationEngine.openTopicConnection(context, "foo",
                SystemUserInfo.getInstance(), topicConnection -> {
                    topics[0] = topicConnection.getTopic();
                    return null;
                });

        ConnectionContext otherContext = new EagerConnectionContext();
        collaborationEngine.openTopicConnection(otherContext, "foo",
                SystemUserInfo.getInstance(), topicConnection -> {
                    topics[1] = topicConnection.getTopic();
                    return null;
                });
        Assert.assertSame(topics[0], topics[1]);
    }

    @Test
    public void openTopicConnections_distinctTopicIds_hasDistinctTopics() {
        Topic[] topics = new Topic[2];
        collaborationEngine.openTopicConnection(context, "foo",
                SystemUserInfo.getInstance(), topicConnection -> {
                    topics[0] = topicConnection.getTopic();
                    return null;
                });

        collaborationEngine.openTopicConnection(context, "baz",
                SystemUserInfo.getInstance(), topicConnection -> {
                    topics[1] = topicConnection.getTopic();
                    return null;
                });
        Assert.assertNotSame(topics[0], topics[1]);
    }

    @Test
    public void reopenTopicConnection_newTopicConnectionInstance() {
        TopicConnection[] connections = new TopicConnection[2];
        collaborationEngine.openTopicConnection(context, "foo",
                SystemUserInfo.getInstance(), topic -> {
                    connections[0] = topic;
                    return null;
                });
        connections[0].deactivateAndClose();
        collaborationEngine.openTopicConnection(context, "foo",
                SystemUserInfo.getInstance(), otherTopic -> {
                    connections[1] = otherTopic;
                    return null;
                });
        Assert.assertNotSame(
                "TopicConnection instance should not be reused after closing.",
                connections[0], connections[1]);
    }

    @Test
    public void closeTopicConnection_garbageCollectedTheActivationHandler()
            throws InterruptedException {
        SpyConnectionContext spyContext = new SpyConnectionContext();
        Registration registration = collaborationEngine
                .openTopicConnection(spyContext, "foo",
                        SystemUserInfo.getInstance(), topic -> {
                            // no impl
                            return null;
                        });

        WeakReference weakRef = new WeakReference(
                spyContext.getActivationHandler());
        registration.remove();

        Assert.assertTrue(
                "Expect ActivationHandler to be garbage-collected when connection closed",
                TestUtils.isGarbageCollected(weakRef));

    }

    @Test
    public void userColors_sameColorForSameUserId() {
        UserInfo firstUser = new UserInfo("id1234");
        UserInfo secondUser = new UserInfo("id1234");
        Assert.assertEquals(collaborationEngine.getUserColorIndex(firstUser),
                collaborationEngine.getUserColorIndex(secondUser));
    }

    @Test
    public void userColors_calculateBaseOnMapSize() {
        UserInfo firstUser = new UserInfo("userId-first");
        int offset = collaborationEngine.getUserColorIndex(firstUser);

        for (int i = 0; i < 12; i++) {
            UserInfo user = new UserInfo("user-color-test-id-" + i);
            Assert.assertEquals(collaborationEngine.getUserColorIndex(user),
                    (offset + i + 1) % USER_COLOR_COUNT);
        }
    }

    @Test
    public void userColors_userInfoHasColorSet() {
        UserInfo firstUser = new UserInfo("id1234", 99999);
        Assert.assertEquals(99999,
                collaborationEngine.getUserColorIndex(firstUser));
    }

    @Test(expected = NullPointerException.class)
    public void nullUserId_throws() {
        new UserInfo(null);
    }

    @Test
    public void openConnection_callbackRunThroughDispatch() {
        AtomicBoolean callbackRun = new AtomicBoolean();
        AtomicReference<Command> pendingAction = new AtomicReference<>();

        collaborationEngine.openTopicConnection(new ConnectionContext() {
            @Override
            public Registration setActivationHandler(
                    ActivationHandler handler) {
                handler.setActive(true);
                return null;
            }

            @Override
            public void dispatchAction(Command action) {
                boolean updated = pendingAction.compareAndSet(null, action);
                Assert.assertTrue(
                        "There should not be a previous pending action",
                        updated);
            }

            @Override
            public <T> CompletableFuture<T> createCompletableFuture() {
                return new CompletableFuture<>();
            }
        }, "topic", SystemUserInfo.getInstance(), connection -> {
            boolean previouslyRun = callbackRun.getAndSet(true);
            Assert.assertFalse("Callback should have been run only once",
                    previouslyRun);
            return null;
        });

        Assert.assertFalse(callbackRun.get());

        pendingAction.get().execute();

        Assert.assertTrue(callbackRun.get());
    }

    @Test
    public void getInstance_productionMode_configurationNotProvided_throwsException() {
        exception.expect(IllegalStateException.class);
        exception.expectMessage(
                "Collaboration Engine is missing a required configuration object");
        MockService service = new MockService(true);
        CollaborationEngine.getInstance(service);
    }

    @Test
    public void configure_configurationAlreadySet_throwsException() {
        exception.expect(IllegalStateException.class);
        exception.expectMessage(
                "Collaboration Engine has been already configured");
        MockService service = new MockService();
        CollaborationEngine.configure(service,
                new CollaborationEngineConfiguration(e -> {
                }));
        CollaborationEngine.configure(service,
                new CollaborationEngineConfiguration(e -> {
                }));
    }

    @Test(expected = NullPointerException.class)
    public void configureLicenseEventHandlerNull_throwsException() {
        CollaborationEngine.configure(new MockService(),
                new CollaborationEngineConfiguration(null));
    }

    @Test
    public void dataDirPropertyIsSetCorrectly() {
        CollaborationEngineConfiguration configuration = new CollaborationEngineConfiguration(
                event -> {
                });
        configuration.setVaadinService(service);
        configuration.setDataDir("foo");
        String dataDir = configuration.getDataDirPath().toString();
        Assert.assertEquals("foo", dataDir);
    }

    @Test
    public void dataDirPropertyTakesPrecedenceOverConfiguration() {
        System.setProperty("vaadin.ce.dataDir", "foo");
        CollaborationEngineConfiguration configuration = new CollaborationEngineConfiguration(
                event -> {
                });
        configuration.setVaadinService(service);
        configuration.setDataDir("bar");
        String dataDir = configuration.getDataDirPath().toString();
        Assert.assertEquals("foo", dataDir);
    }
}
