package de.deepamehta.plugins.mail;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.mail.internet.InternetAddress;

/**
 * Hash recipient lists by type.
 */
@SuppressWarnings("serial")
class RecipientsByType extends HashMap<RecipientType, List<InternetAddress>> {

    public void add(String typeUri, String address, String personal)
            throws UnsupportedEncodingException {
        getTypeList(RecipientType.fromUri(typeUri)).add(new InternetAddress(address, personal));
    }

    private List<InternetAddress> getTypeList(RecipientType type) {
        if (get(type) == null) {
            put(type, new ArrayList<InternetAddress>());
        }
        return get(type);
    }
}
