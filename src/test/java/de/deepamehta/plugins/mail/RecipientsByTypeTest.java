package de.deepamehta.plugins.mail;

import static org.junit.Assert.*;

import java.util.List;

import javax.mail.internet.InternetAddress;

import org.junit.Test;

public class RecipientsByTypeTest {

    @Test
    public void addAndGetRecipients() throws Exception {
        RecipientsByType recipients = new RecipientsByType();
        recipients.add(RecipientType.CC.getUri(), "cc@dm4-mail-check.de", "CC test");
        recipients.add(RecipientType.TO.getUri(), "to@dm4-mail-check.de", "TO test");

        assertEquals(2, recipients.size());

        List<InternetAddress> to = recipients.get(RecipientType.TO);
        assertEquals(1, to.size());
        assertEquals("TO test", to.get(0).getPersonal());
    }

}
