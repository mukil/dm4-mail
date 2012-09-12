package de.deepamehta.plugins.mail.service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collection;

import org.apache.commons.mail.EmailException;

import de.deepamehta.core.Association;
import de.deepamehta.core.Topic;
import de.deepamehta.core.service.ClientState;
import de.deepamehta.core.service.PluginService;
import de.deepamehta.plugins.mail.Mail;
import de.deepamehta.plugins.mail.RecipientType;

public interface MailService extends PluginService {

    /**
     * Returns parent of each search type.
     * 
     * Parent types must include at least one email address.
     * 
     * @return parent search types.
     */
    Collection<Topic> getSearchParentTypes();

    /**
     * Associate mail and recipient.
     * 
     * @param mailId
     *            ID of a mail topic.
     * @param recipient
     *            Recipient topic with at least one email address.
     * @param type
     *            Recipient type URI or null to choose the configured default.
     * @param clientState
     *            Optional cookie or null.
     * @return Recipient association with email address and recipient type.
     */
    Association associateRecipient(long mailId, Topic recipient, RecipientType type,
            ClientState clientState);

    /**
     * Update mail sender association.
     * 
     * @param topicId
     *            ID of a mail or configuration topic.
     * @param senderId
     *            Sender topic with at least one email address.
     * @param clientState
     *            Optional cookie or null.
     * @return Sender association with email address.
     */
    Association associateSender(long topicId, Topic sender, ClientState clientState);

    /**
     * Sends a HTML mail.
     * 
     * @param mailId
     *            ID of a mail topic.
     * @return Sent mail topic.
     */
    Topic send(Mail mail) throws UnsupportedEncodingException,
            EmailException, IOException;
}
