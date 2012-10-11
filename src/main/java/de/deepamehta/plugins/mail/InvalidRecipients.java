package de.deepamehta.plugins.mail;

import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("serial")
public class InvalidRecipients extends Exception {

    private Set<String> recipients = new HashSet<String>();

    public InvalidRecipients(Set<String> recipients) {
        this.recipients = recipients;
    }

    public Set<String> getRecipients() {
        return recipients;
    }

}
