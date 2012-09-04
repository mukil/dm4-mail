package de.deepamehta.plugins.mail;

import java.util.HashMap;
import java.util.Map;

/**
 * Switchable recipient type enumeration mapping.
 */
public enum RecipientType {

    BCC("dm4.mail.recipient.bcc"), CC("dm4.mail.recipient.cc"), TO("dm4.mail.recipient.to");

    private static final Map<String, RecipientType> typesByUri = new HashMap<String, RecipientType>();

    static {
        for (RecipientType type : RecipientType.values()) {
            typesByUri.put(type.getUri(), type);
        }
    }

    private final String uri;

    private RecipientType(String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    public static RecipientType fromUri(String uri) {
        return typesByUri.get(uri);
    }
}
