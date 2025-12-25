package autostepper;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class GoogleImageSearch {
		
    private static final Logger logger = Logger.getLogger(GoogleImageSearch.class.getName());

    private GoogleImageSearch() {}
    public static void findAndSaveImage(String question, String destination) {
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:59.0) Gecko/20100101 Firefox/59.0";

        try {
            String googleUrl = "https://www.google.com/search?as_st=y&tbm=isch&as_q=" + question.replace(",", "+").replace(" ", "+") + "&as_epq=&as_oq=&as_eq=&cr=&as_sitesearch=&safe=images&tbs=isz:lt,islt:vga,iar:w";
            Document doc1 = Jsoup.connect(googleUrl).userAgent(ua).timeout(8 * 1000).get();
            Elements elems = doc1.select("[data-src]");
            if( elems.isEmpty() ) {
                if (AutoStepper.isStepDebug() && logger.isLoggable(Level.FINE)) logger.fine(String.format("Couldn't find any images for: %s", question));
                return;
            }
            Element media = elems.first();
            String finUrl = media.attr("abs:data-src"); 
            saveImage(finUrl.replace("&quot", ""), destination);
        } catch (Exception e) {
            if (AutoStepper.isStepDebug()) logger.fine(e.toString());
        }
    }

    public static void saveImage(String imageUrl, String destinationFile) throws IOException {
        URL url = URI.create(imageUrl).toURL();
        try (InputStream is = url.openStream();
             OutputStream os = new FileOutputStream(destinationFile)) {

            byte[] b = new byte[2048];
            int length;

            while ((length = is.read(b)) != -1) {
                os.write(b, 0, length);
            }
        }
    }
}