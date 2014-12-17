package de.deepamehta.plugins.mail;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.AddressException;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;

import org.apache.commons.mail.EmailAttachment;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.codehaus.jettison.json.JSONException;
import org.jsoup.nodes.Document;

import de.deepamehta.core.Association;
import de.deepamehta.core.ChildTopics;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.model.ChildTopicsModel;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.model.TopicRoleModel;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.PluginService;
import de.deepamehta.core.service.ResultList;
import de.deepamehta.core.service.event.PostCreateTopicListener;
import de.deepamehta.core.storage.spi.DeepaMehtaTransaction;
import de.deepamehta.plugins.accesscontrol.model.ACLEntry;
import de.deepamehta.plugins.accesscontrol.model.AccessControlList;
import de.deepamehta.plugins.accesscontrol.model.Operation;
import de.deepamehta.plugins.accesscontrol.model.UserRole;
import de.deepamehta.plugins.accesscontrol.service.AccessControlService;
import de.deepamehta.plugins.files.ResourceInfo;
import de.deepamehta.plugins.files.service.FilesService;
import de.deepamehta.plugins.mail.service.MailService;

@Path("/mail")
@Produces(MediaType.APPLICATION_JSON)
public class MailPlugin extends PluginActivator implements MailService, PostCreateTopicListener {

    private static Logger log = Logger.getLogger(MailPlugin.class.getName());

    // URI constants

    public static final String AGGREGATION = "dm4.core.aggregation";
    public static final String COMPOSITION = "dm4.core.composition";
    public static final String CHILD = "dm4.core.child";
    public static final String CHILD_TYPE = "dm4.core.child_type";
    public static final String TOPIC_TYPE = "dm4.core.topic_type";
    public static final String PARENT = "dm4.core.parent";
    public static final String PARENT_TYPE = "dm4.core.parent_type";
    public static final String FILE = "dm4.files.file";
    public static final String ATTACHMENTS = "attachments";
    public static final String BODY = "dm4.mail.body";
    public static final String EMAIL_ADDRESS = "dm4.contacts.email_address";
    public static final String DATE = "dm4.mail.date";
    public static final String FROM = "dm4.mail.from";
    public static final String MAIL = "dm4.mail";
    public static final String MESSAGE_ID = "dm4.mail.id";
    public static final String RECIPIENT = "dm4.mail.recipient";
    public static final String RECIPIENT_TYPE = "dm4.mail.recipient.type";
    public static final String SENDER = "dm4.mail.sender";
    public static final String SIGNATURE = "dm4.mail.signature";
    public static final String SUBJECT = "dm4.mail.subject";
    public static final String USER_ACCOUNT = "dm4.accesscontrol.user_account";

    // service references
    @Inject
    private AccessControlService acService;
    @Inject
    private FilesService fileService = null;

    // package internal helpers

    MailConfigurationCache config = null;
    ImageCidEmbedment cidEmbedment = null;
    Autocomplete autocomplete = null;

    boolean isInitialized;

    /**
     * @see #associateRecipient(long, long, RecipientType)
     */
    @POST
    @Path("{mail}/recipient/{address}")
    public Association associateRecipient(//
            @PathParam("mail") long mailId,//
            @PathParam("address") long addressId) {
        return associateRecipient(mailId, addressId, config.getDefaultRecipientType());
    }

    @Override
    public Association associateRecipient(long mailId, long addressId, RecipientType type) {
        log.info("associate " + mailId + " with recipient address " + addressId);
        // ### create value of the recipient association (#593 ref?)
        ChildTopicsModel value = new ChildTopicsModel()//
                .putRef(RECIPIENT_TYPE, type.getUri())//
                .putRef(EMAIL_ADDRESS, addressId);
        // get and update or create a new recipient association
        RelatedTopic recipient = getContactOfEmail(addressId);
        Association association = getRecipientAssociation(mailId, addressId, recipient.getId());
        if (association == null) { // create a recipient association
            return associateRecipient(mailId, recipient, value);
        } else { // update address and type references
            association.setChildTopics(value);
            return association;
        }
    }

    @Override
    public void associateValidatedRecipients(long mailId, List<Topic> recipients) {
        for (Topic recipient : recipients) {
            Topic topic = dms.getTopic(recipient.getId()).loadChildTopics();
            if (topic.getChildTopics().has(EMAIL_ADDRESS)) {
                String personal = recipient.getSimpleValue().toString();
                for (Topic email : topic.getChildTopics().getTopics(EMAIL_ADDRESS)) {
                    String address = email.getSimpleValue().toString();
                    try {
                        new InternetAddress(address, personal).validate();
                    } catch (Exception e) {
                        log.log(Level.INFO, "email address '" + address + "' of contact '" + //
                                personal + "'" + " is invalid: " + e.getMessage());
                        continue; // check the next one
                    }
                    // associate validated email address as BCC recipient
                    associateRecipient(mailId, email.getId(), RecipientType.BCC);
                }
            }
        }
    }

    /**
     * @see #associateSender(long, long)
     */
    @POST
    @Path("{mail}/sender/{address}")
    public Association mailSender(//
            @PathParam("mail") long mailId,//
            @PathParam("address") long addressId) {
        return associateSender(mailId, addressId);
    }

    @Override
    public Association associateSender(long mailId, long addressId) {
        log.info("associate " + mailId + " with sender " + addressId);

        // create value of sender association (#593 ref?)
        ChildTopicsModel value = new ChildTopicsModel().putRef(EMAIL_ADDRESS, addressId);

        // find existing sender association
        RelatedTopic sender = getContactOfEmail(addressId);
        RelatedTopic oldSender = getSender(mailId, false);

        if (oldSender == null) { // create the first sender association
            return associateSender(mailId, sender, value);
        } else { // update or delete the old sender
            Association association = getSenderAssociation(mailId, oldSender.getId());
            DeepaMehtaTransaction tx = dms.beginTx();
            try {
                if (sender.getId() != oldSender.getId()) { // delete the old one
                    dms.deleteAssociation(association.getId());
                    association = associateSender(mailId, sender, value);
                } else { // update composite
                    association.setChildTopics(value);
                }
                tx.success();
            } finally {
                tx.finish();
            }
            return association;
        }
    }

    /**
     * Returns the parent of each search type substring match.
     *
     * @param term
     *            String to search.
     * @return Parent model of each result topic.
     */
    @GET
    @Path("/autocomplete/{term}")
    public List<TopicModel> search(@PathParam("term") String term) {
        String query = "*" + term + "*";
        log.info("autocomplete " + query);
        return autocomplete.search(query);
    }

    /**
     * Creates a copy of the mail.
     *
     * @param mailId
     *            ID of the mail topic to clone.
     * @param includeRecipients
     *            Copy recipients of the origin?
     * @return Cloned mail topic with associated sender and recipients.
     */
    @POST
    @Path("/{mail}/copy")
    public Topic copyMail(//
            @PathParam("mail") long mailId,//
            @QueryParam("recipients") boolean includeRecipients) {
        log.info("copy mail " + mailId);
        DeepaMehtaTransaction tx = dms.beginTx();
        try {
            // 1 clone mail ..
            Topic mail = dms.getTopic(mailId).loadChildTopics();
            ChildTopicsModel oldMail = mail.getModel().getChildTopicsModel();
            String subject = oldMail.getString(SUBJECT);
            String mailBody = oldMail.getString(BODY);
            boolean fromDummy = oldMail.getBoolean(FROM);
            String sentDate = "", messageId = ""; // nullify date and ID
            // 1.1 re-use existing signatures (there is just one but see migration1 ###)
            List<TopicModel> signatures = oldMail.getTopics(SIGNATURE);
            long signatureId = -1;
            for (TopicModel signature : signatures) {
                signatureId = signature.getId();
            }
            ChildTopicsModel clonedMail = new ChildTopicsModel()
                .put(SUBJECT, subject).put(BODY, mailBody)
                .put(FROM, fromDummy).put(DATE, sentDate).put(MESSAGE_ID, messageId)
                .addRef(SIGNATURE, signatureId); // do not copy signatures..
            TopicModel model = new TopicModel(MAIL, clonedMail);
            Topic clone = dms.createTopic(model);
            
            // 2 clone sender association ..
            RelatedTopic sender = getSender(mail, true);
            ChildTopics senderAssociation = sender.getRelatingAssociation().getChildTopics();
            long addressId = senderAssociation.getTopic(EMAIL_ADDRESS).getId();
            // 2.1 reference email address topic on new sender association
            ChildTopicsModel newModel = new ChildTopicsModel()
                .putRef(EMAIL_ADDRESS, addressId);
            associateSender(clone.getId(), sender, newModel);
            
            // 3 clone recipient associations  ..
            ResultList<RelatedTopic> recipientList = mail.getRelatedTopics(RECIPIENT, PARENT, CHILD, null, 0);
            // migration note: fetchRelatingComposite = true
            if (includeRecipients) {
                for (RelatedTopic recipient : recipientList) {
                    if (recipient.getTypeUri().equals(RECIPIENT) == false) {
                        continue; // sender or something else found
                    }
                    // 3.1 re-use existing recipient types
                    Association recipientAssociationModel = dms.getAssociation(recipient.getId()).loadChildTopics();
                    ChildTopicsModel newValue = new ChildTopicsModel()
                        .putRef(RECIPIENT_TYPE, recipientAssociationModel.getTopic(RECIPIENT_TYPE).getUri())
                        .putRef(EMAIL_ADDRESS, recipientAssociationModel.getTopic(EMAIL_ADDRESS).getId());
                    associateRecipient(clone.getId(), recipient, newValue);
                }
            }
            tx.success();
            return clone;
        } finally {
            tx.finish();
        }
    }

    /**
     * Creates a new mail to all email addresses of the contact topic.
     *
     * @param recipientId
     *            ID of a recipient contact topic.
     * @return Mail topic with associated recipient.
     */
    @POST
    @Path("/write/{recipient}")
    public Topic writeTo(@PathParam("recipient") long recipientId) {
        log.info("write a mail to recipient " + recipientId);
        DeepaMehtaTransaction tx = dms.beginTx();
        try {
            Topic mail = dms.createTopic(new TopicModel(MAIL));
            Topic recipient = dms.getTopic(recipientId).loadChildTopics();
            if (recipient.getChildTopics().has(EMAIL_ADDRESS)) {
                for (Topic address : recipient.getChildTopics().getTopics(EMAIL_ADDRESS)) {
                    associateRecipient(mail.getId(), address.getId(),//
                            config.getDefaultRecipientType());
                }
            }
            tx.success();
            return mail;
        } finally {
            tx.finish();
        }
    }

    /**
     * @return Default recipient type.
     */
    @GET
    @Path("/recipient/default")
    public String getDefaultRecipientType() {
        return config.getDefaultRecipientType().getUri();
    }

    /**
     * @return Recipient types.
     */
    @GET
    @Path("/recipient/types")
    public ResultList<RelatedTopic> getRecipientTypes() {
        return config.getRecipientTypes();
    }

    /**
     * @see #getSearchParentTypes
     */
    @GET
    @Path("/search/parents")
    public ResultList<Topic> listSearchParentTypes() {
        Collection<Topic> parents = getSearchParentTypes();
        return new ResultList<Topic>(parents.size(), new ArrayList<Topic>(parents));
    }

    @Override
    public Collection<Topic> getSearchParentTypes() {
        return config.getSearchParentTypes();
    }

    /**
     * Load the configuration.
     *
     * Useful after type and configuration changes with the web-client.
     */
    @GET
    @Path("/config/load")
    public Topic loadConfiguration() {
        log.info("load mail configuration");
        config = new MailConfigurationCache(dms);
        autocomplete = new Autocomplete(dms, config);
        return config.getTopic();
    }

    /**
     * Sets the default sender and signature of a mail topic after creation.
     */
    @Override
    public void postCreateTopic(Topic topic) {
        if (topic.getTypeUri().equals(MAIL)) {
            if (topic.getChildTopics().has(FROM) == false) { // new mail
                associateDefaultSender(topic);
            } else { // copied mail
                Topic from = topic.getChildTopics().getTopic(FROM);
                if (from.getSimpleValue().booleanValue() == false) { // sender?
                    associateDefaultSender(topic);
                }
            }
        }
    }

    /**
     * @see #send(Mail)
     */
    @POST
    @Path("/{mail}/send")
    public StatusReport send(//
            @PathParam("mail") long mailId) {
        log.info("send mail " + mailId);
        return send(new Mail(mailId, dms));
    }

    @Override
    public StatusReport send(Mail mail) {
        
        log.info("DEBUG: Current classloader of MailPlugin is: " 
            + Thread.currentThread().getContextClassLoader().toString());
        // Thread.currentThread().setContextClassLoader(MailPlugin.class.getClassLoader());
        // log.info("DEBUG: Quickfixed classloader of MailPlugin is: " 
            // + Thread.currentThread().getContextClassLoader().toString());
        StatusReport statusReport = new StatusReport(mail.getTopic());

        HtmlEmail email = new HtmlEmail();
        email.setDebug(true); // => System.out.println(SMTP communication);
        email.setHostName(config.getSmtpHost());

        try {
            InternetAddress sender = mail.getSender();
            // ..) Set Senders of Mail
            email.setFrom(sender.getAddress(), sender.getPersonal());
        } catch (UnsupportedEncodingException e) {
            reportException(statusReport, Level.INFO, MailError.SENDER, e);
        } catch (AddressException e) {
            reportException(statusReport, Level.INFO, MailError.SENDER, e);
        } catch (EmailException e) {
            reportException(statusReport, Level.INFO, MailError.SENDER, e);
        }

        try {
            String subject = mail.getSubject();
            if (subject.isEmpty()) { // caught immediately
                throw new IllegalArgumentException("Subject of mail is empty");
            }
            // ..) Set Subject of Mail
            email.setSubject(subject);
        } catch (Exception e) {
            reportException(statusReport, Level.INFO, MailError.CONTENT, e);
        }

        try {
            Document body = cidEmbedment.embedImages(email, mail.getBody());
            String text = body.text();
            if (text.isEmpty()) { // caught immediately
                throw new IllegalArgumentException("Text body of mail is empty");
            }
            // ..) Set Message Body
            email.setTextMsg(text);
            email.setHtmlMsg(body.html());
        } catch (Exception e) {
            reportException(statusReport, Level.INFO, MailError.CONTENT, e);
        }

        for (Long fileId : mail.getAttachmentIds()) {
            try {
                String path = fileService.getFile(fileId).getAbsolutePath();
                EmailAttachment attachment = new EmailAttachment();
                attachment.setPath(path);
                log.fine("attach " + path);
                // ..) Attach Attachmentss
                email.attach(attachment);
            } catch (Exception e) {
                reportException(statusReport, Level.INFO, MailError.ATTACHMENTS, e);
            }
        }

        RecipientsByType recipients = new RecipientsByType();
        try {
            recipients = mail.getRecipients();
            try {
                // ..) Mapping Recipients
                mapRecipients(email, recipients);
            } catch (Exception e) {
                reportException(statusReport, Level.SEVERE, MailError.RECIPIENT_TYPE, e);
            }
        } catch (InvalidRecipients e) {
            for (String recipient : e.getRecipients()) {
                log.log(Level.INFO, MailError.RECIPIENTS.getMessage() + ": " + recipient);
                statusReport.addError(MailError.RECIPIENTS, recipient);
            }
        }

        if (statusReport.hasErrors()) {
            statusReport.setMessage("Mail can NOT be sent");
        } else { // send, update message ID and return status with attached mail
            try {
                String messageId = email.send();
                statusReport.setMessage("Mail was SUCCESSFULLY sent to " + //
                        recipients.getCount() + " mail addresses");
                mail.setMessageId(messageId);
            } catch (EmailException e) {
                statusReport.setMessage("Sending mail FAILED");
                reportException(statusReport, Level.SEVERE, MailError.SEND, e);
            } catch (Exception e) { // error after send
                reportException(statusReport, Level.SEVERE, MailError.UPDATE, e);
            }
        }
        return statusReport;
    }

    /**
     * Initialize.
     */
    @Override
    public void init() {
        isInitialized = true;
        configureIfReady();
    }

    private void configureIfReady() {
        if (isInitialized && acService != null && fileService != null) {
            createAttachmentDirectory();
            checkACLsOfMigration();
            loadConfiguration();
        }
    }

    private void checkACLsOfMigration() {
        Topic config = dms.getTopic("uri", new SimpleValue("dm4.mail.config"));
        if (acService.getCreator(config) == null) {
            DeepaMehtaTransaction tx = dms.beginTx();
            log.info("initial ACL update of configuration");
            try {
                Topic admin = acService.getUsername("admin");
                String adminName = admin.getSimpleValue().toString();
                acService.setCreator(config, adminName);
                acService.setOwner(config, adminName);
                acService.setACL(config, new AccessControlList(new ACLEntry(Operation.WRITE, UserRole.OWNER)));
                tx.success();
            } catch (Exception e) {
                log.warning("could not update ACLs of migration due to a " 
                    +  e.getClass().toString());
            } finally {
                tx.finish();
            }
            
        }
    }

    private void createAttachmentDirectory() {
        // TODO move the initialization to migration "0"
        try {
            ResourceInfo resourceInfo = fileService.getResourceInfo(ATTACHMENTS);
            String kind = resourceInfo.toJSON().getString("kind");
            if (kind.equals("directory") == false) {
                String repoPath = System.getProperty("dm4.filerepo.path");
                String message = "attachment storage directory " + repoPath + File.separator + ATTACHMENTS
                        + " can not be used";
                throw new IllegalStateException(message);
            }
        } catch (WebApplicationException e) { // !exists
            // catch fileService info request error => create directory
            if (e.getResponse().getStatus() != 404) {
                throw e;
            } else {
                log.info("create attachment directory");
                fileService.createFolder(ATTACHMENTS, "/");
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param mail
     * @param clientState
     * 
     * @see MailPlugin#postCreateTopic(Topic)
     */
    private void associateDefaultSender(Topic mail) {
        Topic creator = null;
        RelatedTopic sender = null;

        Topic creatorName = acService.getUsername(acService.getCreator(mail));
        if (creatorName != null) {
            creator = creatorName.getRelatedTopic(null, CHILD, PARENT, USER_ACCOUNT);
        }

        // get user account specific sender
        if (creator != null) {
            sender = getSender(creator, true);
        }

        // get the configured default sender instead
        if (sender == null) {
            sender = config.getDefaultSender();
        }

        if (sender != null) {
            DeepaMehtaTransaction tx = dms.beginTx();
            try {
                ChildTopics value = sender.getRelatingAssociation().getChildTopics();
                long addressId = value.getTopic(EMAIL_ADDRESS).getId();
                ChildTopicsModel newValue = new ChildTopicsModel()
                    .putRef(EMAIL_ADDRESS, addressId); // re-use existing e-mail address topics
                associateSender(mail.getId(), sender, newValue);
                RelatedTopic signature = getContactSignature(sender, addressId);
                if (signature != null) {
                    mail.getChildTopics().getModel().add(SIGNATURE, signature.getModel());
                }
                tx.success();
            } finally {
                tx.finish();
            }
        }
    }

    private Association associateRecipient(long topicId, Topic recipient, ChildTopicsModel value) {
        DeepaMehtaTransaction tx = dms.beginTx();
        try {
            Association assoc = dms.createAssociation(new AssociationModel(RECIPIENT,//
                new TopicRoleModel(recipient.getId(), CHILD),//
                new TopicRoleModel(topicId, PARENT), value));
            tx.success();
            return assoc;
        } finally {
            tx.finish();
        }
    }

    private Association associateSender(long topicId, Topic sender, ChildTopicsModel value) {
        DeepaMehtaTransaction tx = dms.beginTx();
        try {
            Association assoc = dms.createAssociation(new AssociationModel(SENDER,//
                new TopicRoleModel(sender.getId(), CHILD),//
                new TopicRoleModel(topicId, PARENT), value));
            tx.success();
            return assoc;
        } finally {
            tx.finish();
        }
    }

    private RelatedTopic getContactOfEmail(long addressId) {
        return dms.getTopic(addressId).getRelatedTopic(COMPOSITION, CHILD, PARENT, null);
    }

    /** Comparing contact-signatures by "E-Mail Address"-Topic ID*/
    private RelatedTopic getContactSignature(Topic topic, long addressId) {
        for (RelatedTopic signature : topic.getRelatedTopics(SENDER, CHILD, PARENT, SIGNATURE, 0)) {
            // signature had fetchComposite = true
            signature.loadChildTopics();
            // and fetchRelatingComposite = true
            ChildTopics value = signature.getRelatingAssociation().loadChildTopics().getChildTopics();
            if (addressId == value.getTopic(EMAIL_ADDRESS).getId()) {
                return signature;
            }
        }
        return null;
    }

    private Association getRecipientAssociation(long topicId, long addressId, long recipientId) {
        for (Association recipient : dms.getAssociations(topicId, recipientId)) {
            if (recipient.getTypeUri().equals(RECIPIENT)) {
                Association association = dms.getAssociation(recipient.getId());
                association.loadChildTopics();
                Topic address = association.getChildTopics().getTopic(EMAIL_ADDRESS);
                if (address.getId() == addressId) {
                    return association;
                }                
            }
        }
        return null;
    }

    private RelatedTopic getSender(long topicId, boolean fetchRelatingComposite) {
        return getSender(dms.getTopic(topicId), fetchRelatingComposite);
    }

    private RelatedTopic getSender(Topic topic, boolean fetchRelatingComposite) {
        if (!fetchRelatingComposite) {
            return topic.getRelatedTopic(SENDER, PARENT, CHILD, null);   
        } else {
            // do fetch relating composite
            RelatedTopic sender = topic.getRelatedTopic(SENDER, PARENT, CHILD, null);
            if (sender != null) { // this may be the case when a new mail is instantiated 
                sender.getAssociation(SENDER, CHILD, PARENT, topic.getId()).loadChildTopics();
            }
            return sender;
        }
    }

    private Association getSenderAssociation(long topicId, long senderId) {
        return dms.getAssociation(SENDER, topicId, senderId, PARENT, CHILD).loadChildTopics();
    }

    private void mapRecipients(HtmlEmail email, Map<RecipientType, List<InternetAddress>> recipients)
            throws EmailException {
        for (RecipientType type : recipients.keySet()) {
            switch (type) {
            case BCC:
                email.setBcc(recipients.get(type));
                break;
            case CC:
                email.setCc(recipients.get(type));
                break;
            case TO:
                email.setTo(recipients.get(type));
                break;
            default:
                throw new IllegalArgumentException(type.toString());
            }
        }
    }

    private void reportException(StatusReport report, Level level, MailError error, Exception e) {
        String message = e.getMessage();
        Throwable cause = e.getCause();
        if (cause != null) {
            message += ": " + cause.getMessage();
        }
        String logMessage = error.getMessage() + ": " + message;
        if (level == Level.WARNING || level == Level.SEVERE) {
            log.log(level, logMessage, e); // log the exception trace
        } else {
            log.log(level, logMessage); // log only the message
        }
        report.addError(error, message);
    }
    
    @Override
    public void serviceArrived(PluginService service) {
        if (service instanceof FilesService) {
            cidEmbedment = new ImageCidEmbedment(fileService);
        }
    }
    
    @Override
    public void serviceGone(PluginService service) {
        if (service instanceof FilesService) {
            cidEmbedment = null;
        }
    }

}
