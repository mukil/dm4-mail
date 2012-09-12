package de.deepamehta.plugins.mail;

import de.deepamehta.core.Association;
import de.deepamehta.core.Topic;

/**
 * Helper class that holds an association with composite values and the blank
 * corresponding topic.
 * 
 * FIXME accessible getId() of association.getRole1() makes this class needless
 */
class TopicAssociation {

    private Association association;

    private Topic topic;

    public TopicAssociation(Association association, Topic topic) {
        this.association = association;
        this.topic = topic;
    }

    public Topic getTopic() {
        return topic;
    }

    public Association getAssociation() {
        return association;
    }
}
