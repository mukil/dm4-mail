package de.deepamehta.plugins.mail;

import de.deepamehta.core.Association;
import de.deepamehta.core.ChildTopics;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.*;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.Transactional;
import de.deepamehta.core.service.event.PostCreateTopicListener;
import de.deepamehta.core.storage.spi.DeepaMehtaTransaction;
import de.deepamehta.accesscontrol.AccessControlService;
import de.deepamehta.core.service.CoreService;
import de.deepamehta.files.FilesPlugin;
import de.deepamehta.files.FilesService;
import de.deepamehta.files.ItemKind;
import de.deepamehta.files.ResourceInfo;
import de.deepamehta.files.StoredFile;
import de.deepamehta.files.UploadedFile;
import de.deepamehta.plugins.mail.service.MailService;
import de.deepamehta.workspaces.WorkspacesService;
import java.io.File;
import org.apache.commons.mail.EmailAttachment;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.jsoup.nodes.Document;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/mail")
@Produces(MediaType.APPLICATION_JSON)
public class MailPlugin extends PluginActivator implements MailService, PostCreateTopicListener {

    private static Logger log = Logger.getLogger(MailPlugin.class.getName());

    // File Repository Constants
    public static final String FILEREPO_BASE_URI_NAME           = "filerepo";
    public static final String FILEREPO_ATTACHMENTS_SUBFOLDER   = "attachments";
    public static final String DM4_HOST_URL = System.getProperty("dm4.host.url");

    // service references
    @Inject
    private AccessControlService accesscontrol;
    @Inject
    private WorkspacesService workspaces; // used in Migration3
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
    public Association associateRecipient(@PathParam("mail") long mailId,
                                          @PathParam("address") long addressId) {
        return associateRecipient(mailId, addressId, config.getDefaultRecipientType());
    }

    @Override
    public Association associateRecipient(long mailId, long addressId, RecipientType type) {
        log.fine("Associate " + mailId + " with recipient address " + addressId);
        // ### create value of the recipient association (#593 ref?)
        ChildTopicsModel value = mf.newChildTopicsModel()//
                .putRef(RECIPIENT_TYPE, type.getUri())//
                .putRef(EMAIL_ADDRESS, addressId);
        // get and update or create a new recipient association
        RelatedTopic recipient = getParentTopicViaComposition(addressId);
        Association association = getRecipientAssociation(mailId, addressId, recipient.getId());
        if (association == null) { // create a recipient association
            return createRecipientAssociation(mailId, recipient, value);
        } else { // update address and type references
            association.setChildTopics(value);
            return association;
        }
    }

    @Override
    public void associateValidatedRecipients(long mailId, List<Topic> recipients) {
        for (Topic recipient : recipients) {
            Topic topic = dm4.getTopic(recipient.getId()).loadChildTopics();
            List<RelatedTopic> emailAddresses = topic.getChildTopics().getTopicsOrNull(EMAIL_ADDRESS);
            if (emailAddresses != null) {
                String personal = recipient.getSimpleValue().toString();
                for (Topic email : emailAddresses) {
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
    public Association associateMailSender(@PathParam("mail") long mailId, @PathParam("address") long addressId) {
        return associateSender(mailId, addressId);
    }

    @Override
    public Association associateSender(long mailId, long addressId) {
        log.fine("Associate Mail " + mailId + " with E-Mail Address Topic (identifying a Sender) " + addressId);

        // create value of sender association (#593 ref?)
        ChildTopicsModel value = mf.newChildTopicsModel().putRef(EMAIL_ADDRESS, addressId);

        // find existing sender association
        RelatedTopic sender = getParentTopicViaComposition(addressId);
        RelatedTopic oldSender = getRelatedSender(mailId, false);

        if (oldSender == null) { // create the first sender association
            return createSenderAssociation(mailId, sender, value);
        } else { // update or delete the old sender
            Association association = getSenderAssociation(mailId, oldSender.getId());
            DeepaMehtaTransaction tx = dm4.beginTx();
            try {
                if (sender.getId() != oldSender.getId()) { // delete the old one
                    dm4.deleteAssociation(association.getId());
                    association = createSenderAssociation(mailId, sender, value);
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
        log.fine("Mail Contact Autocomplete query=" + query);
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
    public Topic copyMail(@PathParam("mail") long mailId, @QueryParam("recipients") boolean includeRecipients) {
        log.fine("Copy mail " + mailId);
        DeepaMehtaTransaction tx = dm4.beginTx();
        try {
            // 1 clone mail ..
            Topic mail = dm4.getTopic(mailId).loadChildTopics();
            ChildTopicsModel oldMail = mail.getModel().getChildTopicsModel();
            String subject = oldMail.getString(SUBJECT);
            String mailBody = oldMail.getString(BODY);
            boolean fromDummy = oldMail.getBoolean(FROM);
            String sentDate = "", messageId = ""; // nullify date and ID
            // 1.1 re-use existing signatures (there is just one but see migration1 ###)
            List<? extends RelatedTopicModel> signatures = oldMail.getTopicsOrNull(SIGNATURE);
            long signatureId = -1;
            for (RelatedTopicModel signature : signatures) {
                signatureId = signature.getId();
            }
            ChildTopicsModel clonedMail = mf.newChildTopicsModel()
                .put(SUBJECT, subject).put(BODY, mailBody)
                .put(FROM, fromDummy).put(DATE, sentDate).put(MESSAGE_ID, messageId)
                .addRef(SIGNATURE, signatureId); // do not copy signatures..
            TopicModel model = mf.newTopicModel(MAIL, clonedMail);
            Topic clone = dm4.createTopic(model);

            // 2 clone sender association ..
            RelatedTopic sender = MailPlugin.this.getRelatedSender(mail, true);
            ChildTopics senderAssociation = sender.getRelatingAssociation().getChildTopics();
            long addressId = senderAssociation.getTopic(EMAIL_ADDRESS).getId();
            // 2.1 reference email address topic on new sender association
            ChildTopicsModel newModel = mf.newChildTopicsModel()
                .putRef(EMAIL_ADDRESS, addressId);
            createSenderAssociation(clone.getId(), sender, newModel);
            
            // 3 clone recipient associations  ..
            List<RelatedTopic> recipientList = mail.getRelatedTopics(RECIPIENT, PARENT, CHILD, null);
            // migration note: fetchRelatingComposite = true
            if (includeRecipients) {
                for (RelatedTopic recipient : recipientList) {
                    if (recipient.getTypeUri().equals(RECIPIENT) == false) {
                        continue; // sender or something else found
                    }
                    // 3.1 re-use existing recipient types
                    Association recipientAssociationModel = dm4.getAssociation(recipient.getId()).loadChildTopics();
                    ChildTopicsModel newValue = mf.newChildTopicsModel()
                        .putRef(RECIPIENT_TYPE, recipientAssociationModel.getTopic(RECIPIENT_TYPE).getUri())
                        .putRef(EMAIL_ADDRESS, recipientAssociationModel.getTopic(EMAIL_ADDRESS).getId());
                    createRecipientAssociation(clone.getId(), recipient, newValue);
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
        log.fine("Write a mail to recipient " + recipientId);
        DeepaMehtaTransaction tx = dm4.beginTx();
        try {
            Topic mail = dm4.createTopic(mf.newTopicModel(MAIL));
            Topic recipient = dm4.getTopic(recipientId).loadChildTopics();
            List<RelatedTopic> emailAddresses = recipient.getChildTopics().getTopicsOrNull(EMAIL_ADDRESS);
            if (emailAddresses != null) {
                for (RelatedTopic address : emailAddresses) {
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

    @POST
    @Path("/attachment/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Topic upload(UploadedFile attachment) {
        log.fine("Uploading Attachment " + attachment.getName());
        String folderPath = getAttachmentsDirectoryInFileRepo();
        StoredFile file = fileService.storeFile(attachment, folderPath);
        String filePath = folderPath + File.separator + file.getFileName();
        return fileService.getFileTopic(filePath);
    }

    private String prefix() {
        File repo = fileService.getFile("/");
        return ((FilesPlugin) fileService).repoPath(repo);
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
    public List<Topic> getRecipientTypes() {
        return config.getRecipientTypes();
    }

    /**
     * @see #getSearchParentTypes
     */
    @GET
    @Path("/search/parents")
    public List<RelatedTopic> listSearchParentTypes() {
        Collection<RelatedTopic> parents = getSearchParentTypes();
        return new ArrayList<RelatedTopic>(parents);
    }

    @Override
    public Collection<RelatedTopic> getSearchParentTypes() {
        return config.getSearchParentTypes();
    }

    /**
     * Load the configuration.
     *
     * Useful after type and configuration changes with the web-client.
     * ### this will fail/become useless if system configuration topic approach
     *     (see Migration3.3) is employed and this is not triggered by "admin"
     */
    @GET
    @Path("/config/load")
    public Topic loadConfiguration() {
        log.info("Load mail plugin configuration cache");
        config = new MailConfigurationCache(dm4);
        autocomplete = new Autocomplete(dm4, config);
        return config.getTopic();
    }

    /**
     * #### Sets the default sender and signature of a mail topic after creation.
     */
    @Override
    public void postCreateTopic(Topic topic) {
        if (topic.getTypeUri().equals(MAIL)) {
            if (topic.getChildTopics().getTopicOrNull(FROM) == null) { // new mail
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
    public StatusReport send(@PathParam("mail") long mailId) {
        log.info("Send mail " + mailId);
        return send(new Mail(mailId, dm4));
    }

    @Override
    public StatusReport send(Mail mail) {

        // Investigations due to "mx0.infokitchen.net[80.237.138.5] refused to talk to me: 451-HELO/EHLO must contain "
        // .. a FQDN or IP literal 451 Please see RFC 2821 section 4.1.1.1)
        // System.setProperty("mail.smtp.localhost", "mail.mikromedia.de");
        // log.info("SMTP Localhost (EHLO) Name: " + System.getProperty("mail.smtp.localhost"));

        // Hot Fix: Classloader issue we have in OSGi since using Pax web?
        // Latest issue was ..
        // "Caused by: javax.activation.UnsupportedDataTypeException: no object DCH for MIME type multipart/mixed;"
        Thread.currentThread().setContextClassLoader(MailPlugin.class.getClassLoader());
        log.fine("BeforeSend: Set ClassLoader to " + Thread.currentThread().getContextClassLoader().toString());

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
                log.info("Attach " + path);
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
            } finally {
                // Fix: Classloader issue we have in OSGi since using Pax web
                Thread.currentThread().setContextClassLoader(CoreService.class.getClassLoader());
                log.fine("AfterSend: Set ClassLoader back " + Thread.currentThread().getContextClassLoader().toString());
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
        if (isInitialized) {
            loadConfiguration();
            cidEmbedment = new ImageCidEmbedment(fileService);
        }
    }

    /**
     * Constructs path to the "attachments" folder, either in a workspace or in a global file repository.
     * @return String   Repository path for storing attachments.
     */
    private String getAttachmentsDirectoryInFileRepo() {
        String folderPath = FILEREPO_ATTACHMENTS_SUBFOLDER; // global filerepo
        if (!fileService.pathPrefix().equals("/")) { // add workspace specific path in front of image folder name
            folderPath = fileService.pathPrefix() + File.separator + FILEREPO_ATTACHMENTS_SUBFOLDER;
        }
        try {
            // check image file repository
            if (fileService.fileExists(folderPath)) {
                ResourceInfo resourceInfo = fileService.getResourceInfo(fileService.pathPrefix() + File.separator +
                    FILEREPO_ATTACHMENTS_SUBFOLDER);
                if (resourceInfo.getItemKind() != ItemKind.DIRECTORY) {
                    String message = "MailPlugin: \""+FILEREPO_ATTACHMENTS_SUBFOLDER+"\" directory in repo at " + folderPath + " can not be used";
                    throw new IllegalStateException(message);
                }
            } else { // images subdirectory does not exist yet in repo
                log.info("Creating the \""+FILEREPO_ATTACHMENTS_SUBFOLDER+"\" subfolder on the fly for new filerepo in " + folderPath+ "!");
                // A (potential) workspace folder gets created no the fly, too (since #965).
                fileService.createFolder(FILEREPO_ATTACHMENTS_SUBFOLDER, fileService.pathPrefix());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return folderPath;
    }

    /**
     * Double check:
     * - Was crashing if no "signature" was configured at all?
     * - What happens if no "Signature" matches the current E-Mail Address in use by the "Sender"
     * @param mail
     * 
     * @see MailPlugin#postCreateTopic(Topic)
     */
    private void associateDefaultSender(Topic mail) {
        Topic creator = null;
        RelatedTopic senderContact = null;

        // 1) get default sender for user account
        String username = accesscontrol.getCreator(mail.getId());
        Topic creatorName = accesscontrol.getUsernameTopic(username);
        if (creatorName != null) {
            creator = creatorName.getRelatedTopic(null, CHILD, PARENT, USER_ACCOUNT);
        }
        // get user account specific sender (checks for a "Sender" association)
        if (creator != null) {
            senderContact = MailPlugin.this.getRelatedSender(creator, true);
            log.info("Found sender configured for this User Account..");
        }

        // 2) If sender is empty, use the configured default sender instead
        if (senderContact == null) {
            senderContact = config.getDefaultSender();
            log.info("Default sender not setup for this username, accessing system wide default \"From\" as sender");
        }

        // 3) Setup new mail with senders signature and senders Email Adress value as "From" ???
        if (senderContact != null) {
            DeepaMehtaTransaction tx = dm4.beginTx();
            try {
                log.info("Contact identified as Sending Mail " + senderContact.getSimpleValue());
                Association association = senderContact.getRelatingAssociation().loadChildTopics();
                ChildTopics value = association.getChildTopics();
                // May be NULL, if no "Signature" was created for configured "Contact" AND possibly after
                // https://github.com/mukil/dm4-mail/issues/4 - This Lead to: No mail topic could be created anymore
                Topic eMailAddress = value.getTopic(EMAIL_ADDRESS);
                if (eMailAddress != null) {
                    // 3.1) Put Ref E-Mail Address into Sender Association
                    long addressId = eMailAddress.getId();
                    ChildTopicsModel newValue = mf.newChildTopicsModel()
                        .putRef(EMAIL_ADDRESS, addressId); // re-use existing e-mail address topics
                    // ..) Creates "Sender" Association between "Mail" and  the "Contact" ("Person" or "Institution")
                    createSenderAssociation(mail.getId(), senderContact, newValue);
                    // 3.2) Fetch the "Contact" signature for that "E Mail Address" value
                    RelatedTopic signature = getContactSignature(senderContact, addressId);
                    // 3.3) And add that Signature to that new "Mail" Tpic
                    if (signature != null) {
                        log.info("Found a corresponding Signature \"" + signature.getSimpleValue() + "\"");
                        mail.getChildTopics().getModel().add(SIGNATURE, signature.getModel()); // This crashes
                    } else {
                        log.info("No corresponding Signature related to Sender \""
                            + senderContact.getSimpleValue() + "\" and Email \"" + eMailAddress.getSimpleValue() + "\"");
                    }
                } else {
                    log.info("NO E-Mail Address Value present for Sender, subsequently NO corresponding Signature");
                }
                tx.success();
            } finally {
                tx.finish();
            }
        }
    }

    private Association createRecipientAssociation(long topicId, Topic recipient, ChildTopicsModel value) {
        DeepaMehtaTransaction tx = dm4.beginTx();
        try {
            Association assoc = dm4.createAssociation(mf.newAssociationModel(RECIPIENT,
                mf.newTopicRoleModel(recipient.getId(), CHILD),
                mf.newTopicRoleModel(topicId, PARENT), value));
            tx.success();
            return assoc;
        } finally {
            tx.finish();
        }
    }

    private Association createSenderAssociation(long topicId, Topic sender, ChildTopicsModel value) {
        DeepaMehtaTransaction tx = dm4.beginTx();
        try {
            Association assoc = dm4.createAssociation(mf.newAssociationModel(SENDER,
                mf.newTopicRoleModel(sender.getId(), CHILD),
                mf.newTopicRoleModel(topicId, PARENT), value));
            tx.success();
            return assoc;
        } finally {
            tx.finish();
        }
    }

    private RelatedTopic getParentTopicViaComposition(long addressId) {
        return dm4.getTopic(addressId).getRelatedTopic(COMPOSITION, CHILD, PARENT, null);
    }

    /** Comparing contact-signatures by "E-Mail Address"-Topic ID*/
    private RelatedTopic getContactSignature(Topic contact, long addressId) {
        // Fixme: The "Sender" relation is used again to model a "Signature" for a e.g. "Person" and
        // it holds an "E-Mail Address" value chosen, identifying which signature to fetch, e.g. if a "Person"
        // has many "E-Mail Address" entered
        for (RelatedTopic signature : contact.getRelatedTopics(SENDER, CHILD, PARENT, SIGNATURE)) {
            signature.loadChildTopics();
            ChildTopics value = signature.getRelatingAssociation().loadChildTopics().getChildTopics();
            log.info("Fetching Contact Signature E-Mail Address Value: " + value);
            // The following Topic may be NULL too
            if (addressId == value.getTopic(EMAIL_ADDRESS).getId()) {
                return signature;
            }
        }
        return null;
    }

    private Association getRecipientAssociation(long topicId, long addressId, long recipientId) {
        for (Association recipient : dm4.getAssociations(topicId, recipientId)) {
            if (recipient.getTypeUri().equals(RECIPIENT)) {
                Association association = dm4.getAssociation(recipient.getId());
                association.loadChildTopics();
                Topic address = association.getChildTopics().getTopic(EMAIL_ADDRESS);
                if (address.getId() == addressId) {
                    return association;
                }                
            }
        }
        return null;
    }

    private RelatedTopic getRelatedSender(long topicId, boolean fetchRelatingComposite) {
        return getRelatedSender(dm4.getTopic(topicId), fetchRelatingComposite);
    }

    private RelatedTopic getRelatedSender(Topic topic, boolean fetchRelatingComposite) {
        if (!fetchRelatingComposite) {
            return topic.getRelatedTopic(SENDER, PARENT, CHILD, null);   
        } else {
            // do fetch relating composite
            RelatedTopic sender = topic.getRelatedTopic(SENDER, PARENT, CHILD, null);
            if (sender != null) { // this may be the case when a new mail is instantiated 
                sender.getAssociation(SENDER, CHILD, PARENT, topic.getId());
                sender.loadChildTopics();
            }
            return sender;
        }
    }

    private Association getSenderAssociation(long topicId, long senderId) {
        return dm4.getAssociation(SENDER, topicId, senderId, PARENT, CHILD).loadChildTopics();
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

}
