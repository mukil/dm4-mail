package de.deepamehta.plugins.mail;

import static de.deepamehta.plugins.mail.MailPlugin.*;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import de.deepamehta.core.Association;
import de.deepamehta.core.ChildTopics;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.service.CoreService;
import de.deepamehta.core.storage.spi.DeepaMehtaTransaction;

/**
 * Model class that wraps the mail composite access.
 */
public class Mail {

    private final CoreService dms;

    private final Topic topic;

    public Mail(long topicId, CoreService dms) {
        this.dms = dms;
        this.topic = dms.getTopic(topicId).loadChildTopics();
    }

    public String getBody() throws Exception {
        String body = topic.getChildTopics().getTopic(BODY).getSimpleValue().toString();
        if (body.isEmpty()) {
            throw new IllegalArgumentException("Body of mail is empty");
        }

        if (topic.getChildTopics().getTopicsOrNull(SIGNATURE) == null) {
            throw new IllegalArgumentException("Signature of mail not found");
        } else {
            List<RelatedTopic> signature = topic.getChildTopics().getTopics(SIGNATURE);
            ChildTopics value = signature.get(0).getChildTopics();
            if (value.getTopicOrNull(BODY) == null) {
                throw new IllegalArgumentException("Signature of mail is empty");
            } else {
                String sigBody = value.getTopic(BODY).getSimpleValue().toString();
                if (sigBody.isEmpty()) {
                    throw new IllegalArgumentException("Signature of mail is empty");
                }
                return body + sigBody;
            }
        }
    }

    public RecipientsByType getRecipients() throws InvalidRecipients {
        Set<String> invalid = new HashSet<String>();
        RecipientsByType results = new RecipientsByType();

        for (RelatedTopic recipient : topic.getRelatedTopics(RECIPIENT, PARENT, CHILD, null)) {
            String personal = recipient.getSimpleValue().toString();

            for (Association association : dms.getAssociations(topic.getId(), recipient.getId())) {
                if (association.getTypeUri().equals(RECIPIENT) == false) {
                    continue; // sender or something else found
                }

                // get and validate recipient association
                ChildTopics value = dms.getAssociation(association.getId())
                    .loadChildTopics().getChildTopics(); // re-fetch with value
                if (value.getTopicOrNull(RECIPIENT_TYPE) == null) {
                    invalid.add("Recipient type of \"" + personal + "\" is not defined");
                    continue;
                }
                if (value.getTopicOrNull(EMAIL_ADDRESS) == null) {
                    invalid.add("Recipient \"" + personal + "\" has no email address");
                    continue;
                }

                Topic type = value.getTopic(RECIPIENT_TYPE);
                String email = value.getTopic(EMAIL_ADDRESS).getSimpleValue().toString();
                try {
                    results.add(type.getUri(), email, personal);
                } catch (Exception e) {
                    invalid.add("Email address \"" + email + "\" of recipient \"" + //
                            personal + "\" is invalid");
                }
            }
        }
        if (invalid.isEmpty() == false) {
            throw new InvalidRecipients(invalid);
        }
        return results;
    }

    public InternetAddress getSender() throws UnsupportedEncodingException, AddressException {
        RelatedTopic sender = topic.getRelatedTopic(SENDER, PARENT, CHILD, null);
        // sender had fetchRelatingComposite=true
        if (sender == null) {
            throw new IllegalArgumentException("Contact required");
        }
        String personal = sender.getSimpleValue().toString();
        String address;
        try { // throws runtime access
            address = sender.getRelatingAssociation().getChildTopics()//
                    .getTopic(EMAIL_ADDRESS).getSimpleValue().toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Contact has no email address");
        }
        InternetAddress internetAddress = new InternetAddress(address, personal);
        internetAddress.validate();
        return internetAddress;
    }

    public String getSubject() {
        return topic.getChildTopics().getTopic(SUBJECT).getSimpleValue().toString();
    }

    public Topic getTopic() {
        return topic;
    }

    public Topic setMessageId(String messageId) {
        DeepaMehtaTransaction tx = dms.beginTx();
        try {
            topic.getChildTopics().set(DATE, new Date().toString());
            topic.getChildTopics().set(MESSAGE_ID, messageId);
            tx.success();
        } finally {
            tx.finish();
        }
        return topic;
    }

    public Set<Long> getAttachmentIds() {
        Set<Long> attachments = new HashSet<Long>();
        for (RelatedTopic attachment : topic.getRelatedTopics(AGGREGATION, PARENT, CHILD, FILE)) {
            attachments.add(attachment.getId());
        }
        return attachments;
    }
}
