package de.deepamehta.plugins.mail;

import de.deepamehta.core.Topic;

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

}
