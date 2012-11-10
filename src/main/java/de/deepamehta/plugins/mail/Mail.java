package de.deepamehta.plugins.mail;

import static de.deepamehta.plugins.mail.MailPlugin.*;
import static de.deepamehta.plugins.mail.TopicUtils.*;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import de.deepamehta.core.DeepaMehtaTransaction;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.CompositeValue;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.service.ClientState;
import de.deepamehta.core.service.DeepaMehtaService;

/**
 * Model class that wraps the mail composite access.
 */
public class Mail {

    private final DeepaMehtaService dms;

    private final Topic topic;

    private final ClientState clientState;

    public Mail(long topicId, DeepaMehtaService dms, ClientState clientState) {
        this.dms = dms;
        this.clientState = clientState;
        this.topic = dms.getTopic(topicId, true, clientState);
    }

    public String getBody() throws Exception {
        String body = topic.getCompositeValue().getTopic(BODY).getSimpleValue().toString();
        if (body.isEmpty()) {
            throw new IllegalArgumentException("Body of mail is empty");
        }
        String signature = topic.getCompositeValue().getTopics(SIGNATURE).get(0) // first
                .getCompositeValue().getTopic(BODY).getSimpleValue().toString();
        if (signature.isEmpty()) {
            throw new IllegalArgumentException("Signature of mail is empty");
        }
        return body + signature;
    }

    public RecipientsByType getRecipients() throws InvalidRecipients {
        Set<String> invalid = new HashSet<String>();
        RecipientsByType results = new RecipientsByType();
        for (RelatedTopic recipient : topic.getRelatedTopics(RECIPIENT,//
                WHOLE, PART, null, false, true, 0, null)) {
            String personal = recipient.getSimpleValue().toString();
            CompositeValue assocComposite = recipient.getAssociation().getCompositeValue();
            TopicModel type;
            try { // throws runtime access
                type = assocComposite.getTopic(RECIPIENT_TYPE);
            } catch (Exception e) {
                invalid.add("Recipient type of \"" + personal + "\" is not defined");
                continue;
            }
            List<TopicModel> addresses;
            try { // throws runtime access
                addresses = assocComposite.getTopics(EMAIL_ADDRESS);
                if (addresses.isEmpty()) { // caught immediately
                    throw new RuntimeException("no address");
                }
            } catch (Exception e) {
                invalid.add("Recipient \"" + personal + "\" has no email address");
                continue;
            }
            String typeUri = type.getUri();
            for (TopicModel email : addresses) {
                String address = email.getSimpleValue().toString();
                try {
                    results.add(typeUri, address, personal);
                } catch (Exception e) {
                    invalid.add("Address \"" + address + "\" of recipient \"" + //
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
        RelatedTopic sender = topic.getRelatedTopic(SENDER,//
                WHOLE, PART, null, false, true, null);
        if (sender == null) {
            throw new IllegalArgumentException("Contact required");
        }
        String personal = sender.getSimpleValue().toString();
        String address;
        try { // throws runtime access

            address = sender.getAssociation().getCompositeValue()//
                    .getTopic(EMAIL_ADDRESS).getSimpleValue().toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Contact has no email address");
        }
        InternetAddress internetAddress = new InternetAddress(address, personal);
        internetAddress.validate();
        return internetAddress;
    }

    public String getSubject() {
        return topic.getCompositeValue().getTopic(SUBJECT).getSimpleValue().toString();
    }

    public Topic getTopic() {
        return topic;
    }

    public Topic setMessageId(String messageId) {
        DeepaMehtaTransaction tx = dms.beginTx();
        topic.setChildTopicValue(DATE, new SimpleValue(new Date().toString()));
        topic.setChildTopicValue(MESSAGE_ID, new SimpleValue(messageId));
        tx.success();
        tx.finish();
        return topic;
    }

    public Set<Long> getAttachmentIds() {
        Set<Long> attachments = new HashSet<Long>();
        for (RelatedTopic attachment : topic.getRelatedTopics(TopicUtils.AGGREGATION,
                TopicUtils.WHOLE, TopicUtils.PART, FILE, false, false, 0, clientState)) {
            attachments.add(attachment.getId());
        }
        return attachments;
    }
}
