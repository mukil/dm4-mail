package de.deepamehta.plugins.mail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.logging.Logger;

import javax.mail.util.ByteArrayDataSource;

import org.apache.commons.io.IOUtils;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import de.deepamehta.core.service.DeepaMehtaService;
import de.deepamehta.core.util.DeepaMehtaUtils;
import de.deepamehta.plugins.files.service.FilesService;

public class ImageCidEmbedment {

    private static Logger log = Logger.getLogger(MailPlugin.class.getName());

    private final DeepaMehtaService dms;

    private final FilesService fileService;

    public ImageCidEmbedment(DeepaMehtaService dms, FilesService fileService) {
        this.dms = dms;
        this.fileService = fileService;
    }

    /**
     * Embed all images of body.
     * 
     * @return Document with CID replaced image source attributes.
     */
    public Document embedImages(HtmlEmail email, String body) throws EmailException, IOException {
        int count = 0;
        Document document = Jsoup.parse(body);
        for (Element image : document.getElementsByTag("img")) {
            URL url = new URL(image.attr("src"));
            String cid = embedImage(email, url, url.getPath() + ++count);
            image.attr("src", "cid:" + cid);
        }
        return document;
    }

    /**
     * Embed any image type (external URL, file repository and plugin resource).
     * 
     * @return CID of embedded image.
     */
    public String embedImage(HtmlEmail email, URL url, String name) throws EmailException,
            IOException {
        if (DeepaMehtaUtils.isDeepaMehtaURL(url)) {
            String path = fileService.getRepositoryPath(url);
            if (path != null) { // repository link
                log.fine("embed repository image " + path);
                return email.embed(fileService.getFile(path));
            } else { // plugin resource
                path = url.getPath();
                String pluginUri = path.substring(1, path.indexOf("/", 1));
                path = "/web" + path.substring(path.indexOf("/", 1));

                log.fine("embed image resource " + path + " of plugin " + pluginUri);
                String type = getMimeType(pluginUri, path);
                InputStream resource = dms.getPlugin(pluginUri).getResourceAsStream(path);
                return email.embed(new ByteArrayDataSource(resource, type), name);
            }
        } else { // external URL
            log.fine("embed external image " + url);
            return email.embed(url, name);
        }
    }

    // FIXME simplify MIME type determination
    private String getMimeType(String pluginUri, String path) throws IOException,
            FileNotFoundException {
        InputStream resource = dms.getPlugin(pluginUri).getResourceAsStream(path);
        if (resource == null) {
            throw new RuntimeException("resource " + path + " of plugin " + pluginUri
                    + " not accessible");
        }
        // copy resource to a temporary file
        File file = File.createTempFile("mail_image_resource", "bin");
        FileOutputStream writer = new FileOutputStream(file);
        IOUtils.copy(resource, writer);
        IOUtils.closeQuietly(writer);
        IOUtils.closeQuietly(resource);

        // open connection and get MIME type
        return file.toURI().toURL().openConnection().getContentType();
    }

}
