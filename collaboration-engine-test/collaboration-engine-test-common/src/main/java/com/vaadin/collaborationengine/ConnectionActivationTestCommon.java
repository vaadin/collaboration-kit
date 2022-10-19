package com.vaadin.collaborationengine;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.vaadin.collaborationengine.util.AbstractCollaborativeFormTestCommon;

public class ConnectionActivationTestCommon
        extends AbstractCollaborativeFormTestCommon {
    @Test
    @Ignore("https://github.com/vaadin/collaboration-engine-internal/issues/917")
    public void preserveOnRefresh_fieldValuesPreserved_fieldIsCollaborative() {
        client1.textField.setValue("foo");
        client1.emailField.setValue("bar");

        refresh();
        client1 = new ClientState(this); // use the new session
        Assert.assertEquals("foo", client1.textField.getValue());
        Assert.assertEquals("bar", client1.emailField.getValue());

        ClientState client2 = new ClientState(addClient());
        client2.textField.setValue("baz");
        Assert.assertEquals("CE should still work as usual.", "baz",
                client1.textField.getValue());
    }
}
