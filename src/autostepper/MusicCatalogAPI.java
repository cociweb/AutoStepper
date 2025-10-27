package autostepper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.net.URL;

public class MusicCatalogAPI {

    /**
     * Search for album art using iTunes Search API (free, no API key required)
     * @param songName The song name to search for
     * @param destinationFile Path to save the image
     */
    public static void findAlbumArt(String songName, String destinationFile) {
        try {
            // Clean up the song name for search
            String searchTerm = songName.replace("(", " ").replace(")", " ")
                                      .replace("[", " ").replace("]", " ")
                                      .replace("-", " ").replace("_", " ")
                                      .replace("&", " ").trim();

            // Use iTunes Search API (free, no API key needed)
            String apiUrl = "https://itunes.apple.com/search?term=" +
                           java.net.URLEncoder.encode(searchTerm, "UTF-8") +
                           "&entity=song&limit=5";

            Document doc = Jsoup.connect(apiUrl)
                              .userAgent("AutoStepper/1.0")
                              .timeout(10 * 1000)
                              .get();

            // Parse JSON response manually (simple approach)
            String jsonResponse = doc.text();

            // Look for artworkUrl100 in the JSON response
            String artworkUrl = extractArtworkUrl(jsonResponse);

            if (artworkUrl != null && !artworkUrl.isEmpty()) {
                // Get higher resolution artwork (replace 100x100 with 600x600)
                artworkUrl = artworkUrl.replace("100x100bb", "600x600bb");
                saveImage(artworkUrl, destinationFile);
                System.out.println("Downloaded album art from iTunes!");
            } else {
                System.out.println("No album art found in iTunes API response");
            }

        } catch (Exception e) {
            System.out.println("iTunes API failed with exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Simple JSON parsing to extract artwork URL
     */
    private static String extractArtworkUrl(String json) {
        try {
            // Look for "artworkUrl100":"http://..."
            int artworkIndex = json.indexOf("\"artworkUrl100\":\"");
            if (artworkIndex == -1) {
                artworkIndex = json.indexOf("\"artworkUrl60\":\"");
            }
            if (artworkIndex == -1) {
                return null;
            }

            int startQuote = json.indexOf("\"", artworkIndex + 15);
            int endQuote = json.indexOf("\"", startQuote + 1);

            if (startQuote != -1 && endQuote != -1) {
                return json.substring(startQuote + 1, endQuote);
            }
        } catch (Exception e) {
            // Parsing failed, return null
        }
        return null;
    }

    /**
     * Download and save image from URL
     */
    public static void saveImage(String imageUrl, String destinationFile) throws IOException {
        URL url = new URL(imageUrl);
        try (InputStream is = url.openStream();
             OutputStream os = new FileOutputStream(destinationFile)) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }
    }
}
