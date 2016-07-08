package de.deepamehta.plugins.mail;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Logger;

import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import de.deepamehta.files.FilesService;

class ImageCidEmbedment {

    private static Logger log = Logger.getLogger(MailPlugin.class.getName());

    private final FilesService fileService;

    public ImageCidEmbedment(FilesService fileService) {
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
            image.attr("src", "cid:" + embedImage(email, url, ++count + url.getPath()));
        }
        return document;
    }

    /**
     * Embed any image type (external URL, file repository and plugin resource).
     * 
     * @return CID of embedded image.
     * @throws EmailException
     */
    public String embedImage(HtmlEmail email, URL url, String name) throws EmailException {
        String path = fileService.getRepositoryPath(url);
        if (path != null) { // repository link
            log.fine("embed repository image " + path);
            return email.embed(fileService.getFile(path));
        } else { // external URL
            log.fine("embed external image " + url);
            return email.embed(url, name);
        }
    }

}
