package de.deepamehta.plugins.mail;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

import de.deepamehta.core.JSONEnabled;
import de.deepamehta.core.Topic;
import java.util.Iterator;
import java.util.logging.Logger;

public class StatusReport implements JSONEnabled {

    private String message;
    private Topic topic;

        
    private static Logger log = Logger.getLogger(MailPlugin.class.getName());

    // error code > topic id > topic specific message
    private Map<MailError, Set<String>> errors = new HashMap<MailError, Set<String>>();

    public StatusReport(Topic topic) {
        this.topic = topic;
    }

    public void addError(MailError error, String message) {
        Set<String> messages = errors.get(error);
        if (messages == null) {
            messages = new HashSet<String>();
            errors.put(error, messages);
        }
        messages.add(message);
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setTopic(Topic topic) {
        this.topic = topic;
    }

    public boolean hasErrors() {
        return errors.isEmpty() ? false : true;
    }

    @Override
    public JSONObject toJSON() {
        try {
            JSONObject json = new JSONObject()//
                    .put("message", message)//
                    .put("success", hasErrors() ? false : true)//
                    .put("topic_id", topic.getId());
            if (hasErrors()) { // map error messages
                JSONArray jsonErrors = new JSONArray();
                for (MailError mailError : errors.keySet()) {
                    Set<String> mailErrorTopics = errors.get(mailError);
                    log.info("Mail has Errors:" + mailErrorTopics.toString());
                    Iterator iterator = mailErrorTopics.iterator();
                    while (iterator.hasNext()) {
                        Object topicError = iterator.next();
                        jsonErrors.put(new JSONObject()
                            .put("message", mailError.getMessage())
                            // ### find/implement a better replacement for DeepaMehtaUtils.stringToJSON
                            .put("topics", topicError));
                    }
                }
                json.put("errors", jsonErrors);
            }
            return json;
        } catch (Exception e) {
            throw new RuntimeException("Serialization failed (" + this + ")", e);
        }
    }
}
