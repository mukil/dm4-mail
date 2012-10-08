package de.deepamehta.plugins.mail;

import static de.deepamehta.plugins.mail.MailPlugin.*;
import static de.deepamehta.plugins.mail.TopicUtils.*;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public String getBody() {
        return topic.getCompositeValue().getTopic(BODY).getSimpleValue().toString()
                + topic.getCompositeValue().getTopics(SIGNATURE).get(0).getCompositeValue()
                        .getTopic(BODY).getSimpleValue().toString();
    }

    public Map<RecipientType, List<InternetAddress>> getRecipients()
            throws UnsupportedEncodingException {
        RecipientsByType results = new RecipientsByType();
        for (RelatedTopic recipient : topic.getRelatedTopics(RECIPIENT,//
                WHOLE, PART, null, false, true, 0, null)) {
            String personal = recipient.getSimpleValue().toString();
            CompositeValue assocComposite = recipient.getAssociation().getCompositeValue();
            String typeUri = assocComposite.getTopic(RECIPIENT_TYPE).getUri();
            for (TopicModel email : assocComposite.getTopics(EMAIL_ADDRESS)) {
                results.add(typeUri, email.getSimpleValue().toString(), personal);
            }
        }
        return results;
    }

    public InternetAddress getSender() throws UnsupportedEncodingException {
        RelatedTopic sender = topic.getRelatedTopic(SENDER,//
                WHOLE, PART, null, false, true, null);
        if (sender == null) {
            throw new IllegalArgumentException("sender address required");
        }
        String personal = sender.getSimpleValue().toString();
        String address = sender.getAssociation().getCompositeValue()//
                .getTopic(EMAIL_ADDRESS).getSimpleValue().toString();
        return new InternetAddress(address, personal);
    }

    public String getSubject() {
        return topic.getCompositeValue().getTopic(SUBJECT).getSimpleValue().toString();
    }

    public Topic getTopic() {
        return topic;
    }

    public Topic setDate(Date date) {
        DeepaMehtaTransaction tx = dms.beginTx();
        topic.setChildTopicValue(DATE, new SimpleValue(date.toString()));
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
