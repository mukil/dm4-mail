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

    // DM 4 Core URIs
    static final String AGGREGATION = "dm4.core.aggregation";
    static final String COMPOSITION = "dm4.core.composition";
    static final String CHILD = "dm4.core.child";
    static final String CHILD_TYPE = "dm4.core.child_type";
    static final String TOPIC_TYPE = "dm4.core.topic_type";
    static final String PARENT = "dm4.core.parent";
    static final String PARENT_TYPE = "dm4.core.parent_type";
    // ACL / User Account URIs
    static final String USER_ACCOUNT = "dm4.accesscontrol.user_account";
    // File URIs
    static final String FILE = "dm4.files.file";
    static final String ATTACHMENTS = "attachments";
    // Mail URIs
    static final String BODY = "dm4.mail.body";
    static final String EMAIL_ADDRESS = "dm4.contacts.email_address";
    static final String DATE = "dm4.mail.date";
    static final String FROM = "dm4.mail.from";
    static final String MAIL = "dm4.mail";
    static final String MESSAGE_ID = "dm4.mail.id";
    static final String RECIPIENT = "dm4.mail.recipient";
    static final String RECIPIENT_TYPE = "dm4.mail.recipient.type";
    static final String SENDER = "dm4.mail.sender";
    static final String SIGNATURE = "dm4.mail.signature";
    static final String SUBJECT = "dm4.mail.subject";

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
