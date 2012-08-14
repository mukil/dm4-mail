package de.deepamehta.plugins.mail;

import java.util.ArrayList;
import java.util.List;

import de.deepamehta.core.Association;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.ResultSet;
import de.deepamehta.core.Topic;
import de.deepamehta.core.service.DeepaMehtaService;

/**
 * Static topic access abstractions.
 */
class TopicUtils {

    public static final String AGGREGATION = "dm4.core.aggregation";

    public static final String COMPOSITION = "dm4.core.composition";

    public static final String PART = "dm4.core.part";

    public static final String PART_TYPE = "dm4.core.part_type";

    public static final String TOPIC_TYPE = "dm4.core.topic_type";

    public static final String WHOLE = "dm4.core.whole";

    public static final String WHOLE_TYPE = "dm4.core.whole_type";

    public static Topic getParentTopic(Topic partTopic, String parentType) {
        return partTopic.getRelatedTopic(null,//
                PART, WHOLE, parentType, false, false, null);
    }

    public static Topic getParentType(Topic partType) {
        return partType.getRelatedTopic(null,//
                PART_TYPE, WHOLE_TYPE, TOPIC_TYPE, false, false, null);
    }

    // FIXME simplify query of association
    public static TopicAssociation getRelatedPart(DeepaMehtaService dms,
            String associationUri, Topic wholeTopic, Topic partTopic) {
        Association association = dms.getAssociation(associationUri,//
                wholeTopic.getId(), partTopic.getId(), WHOLE, PART, true, null);
        return new TopicAssociation(association, partTopic);
    }

    public static TopicAssociation getRelatedPart(DeepaMehtaService dms, Topic wholeTopic,
            String associationUri) {
        RelatedTopic partTopic = wholeTopic.getRelatedTopic(associationUri,//
                WHOLE, PART, null, false, false, null);
        if (partTopic == null) {
            return null;
        } else {
            return getRelatedPart(dms, associationUri, wholeTopic, partTopic);
        }
    }

    public static List<TopicAssociation> getRelatedParts(DeepaMehtaService dms, Topic wholeTopic,
            String associationUri) {
        ArrayList<TopicAssociation> parts = new ArrayList<TopicAssociation>();
        ResultSet<RelatedTopic> partTopics = wholeTopic.getRelatedTopics(associationUri,//
                WHOLE, PART, null, false, false, 0, null);
        for (RelatedTopic partTopic : partTopics) {
            parts.add(getRelatedPart(dms, associationUri, wholeTopic, partTopic));
        }
        return parts;
    }
}
