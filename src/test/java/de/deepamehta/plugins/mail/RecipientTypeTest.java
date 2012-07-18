package de.deepamehta.plugins.mail;

import static org.junit.Assert.*;

import org.junit.Test;

public class RecipientTypeTest {

    @Test
    public void staticMapResolve() throws Exception {
        assertEquals(RecipientType.TO, RecipientType.fromUri("dm4.mail.recipient.to"));
    }

    @Test
    public void enumSwitch() throws Exception {
        switch (RecipientType.fromUri("dm4.mail.recipient.to")) {
        case TO:
            assertTrue("matched", true);
            break;
        default:
            fail("recipient type switch");
        }
    }
}
