package de.deepamehta.plugins.mail;

import static de.deepamehta.plugins.mail.MailPlugin.*;

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
        return topic.getCompositeValue().getTopic(BODY).getSimpleValue().toString();
    }

    public Map<RecipientType, List<InternetAddress>> getRecipients()
            throws UnsupportedEncodingException {
        RecipientsByType results = new RecipientsByType();
        for (TopicAssociation recipient : TopicUtils.getRelatedParts(dms, topic, RECIPIENT)) {
            String personal = recipient.getTopic().getSimpleValue().toString();
            CompositeValue assocComposite = recipient.getAssociation().getCompositeValue();
            String address = assocComposite.getTopic(EMAIL_ADDRESS).getSimpleValue().toString();
            String typeUri = assocComposite.getTopic(RECIPIENT_TYPE).getUri();
            results.add(typeUri, address, personal);
        }
        return results;
    }

    public InternetAddress getSender() throws UnsupportedEncodingException {
        TopicAssociation sender = TopicUtils.getRelatedPart(dms, topic, SENDER);
        String personal = sender.getTopic().getSimpleValue().toString();
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
