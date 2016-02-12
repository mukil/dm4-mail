package de.deepamehta.plugins.mail.service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.List;

import org.apache.commons.mail.EmailException;

import de.deepamehta.core.Association;
import de.deepamehta.core.Topic;
import de.deepamehta.plugins.mail.Mail;
import de.deepamehta.plugins.mail.RecipientType;
import de.deepamehta.plugins.mail.StatusReport;

public interface MailService {

    /**
     * Returns parent of each search type.
     * 
     * Parent types must include at least one email address.
     * 
     * @return parent search types.
     */
    Collection<Topic> getSearchParentTypes();

    /**
     * Update or create a mail recipient association.
     * 
     * @param mailId
     *            ID of a mail topic.
     * @param addressId
     *            Email address of recipient.
     * @param type
     *            Recipient type URI or null to choose the configured default.
     * @return Recipient association with email address and recipient type.
     */
    Association associateRecipient(long mailId, long addressId, RecipientType type);

    /**
     * Update or create a mail sender association.
     * 
     * @param topicId
     *            ID of a mail or configuration topic.
     * @param addressId
     *            Email address of sender.
     * @return Sender association with email address.
     */
    Association associateSender(long topicId, long addressId);

    /**
     * Associate all valid email addresses of each recipient.
     * 
     * @param mailId
     * @param recipients
     */
    void associateValidatedRecipients(long mailId, List<Topic> recipients);

    /**
     * Sends a HTML mail.
     * 
     * @param mail  Mail topic.
     * @return      Sent mail topic.
     */
    StatusReport send(Mail mail) throws UnsupportedEncodingException, EmailException, IOException;
}
