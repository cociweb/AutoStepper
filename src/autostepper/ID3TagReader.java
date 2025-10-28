package autostepper;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.File;

/**
 * Utility class for reading ID3 tags from audio files
 */
public class ID3TagReader {

    /**
     * Read metadata from audio file using ID3 tags
     * @param audioFile The audio file to read
     * @return AudioMetadata object with title, artist, and genre
     */
    public static AudioMetadata readMetadata(File audioFile) {
        // Disable Jaudiotagger INFO logging to reduce verbosity
        Logger.getLogger("org.jaudiotagger").setLevel(Level.WARNING);
        
        AudioMetadata metadata = new AudioMetadata();

        try {
            AudioFile f = AudioFileIO.read(audioFile);
            Tag tag = f.getTag();

            if (tag != null) {
                // Read title
                String title = tag.getFirst(FieldKey.TITLE);
                if (title != null && !title.trim().isEmpty()) {
                    metadata.title = title.trim();
                }

                // Read artist
                String artist = tag.getFirst(FieldKey.ARTIST);
                if (artist != null && !artist.trim().isEmpty()) {
                    metadata.artist = artist.trim();
                }

                // Read genre
                String genre = tag.getFirst(FieldKey.GENRE);
                if (genre != null && !genre.trim().isEmpty()) {
                    metadata.genre = genre.trim();
                }

                if (AutoStepper.STEP_DEBUG) System.out.println("Read ID3 tags - Title: '" + metadata.title + "', Artist: '" + metadata.artist + "', Genre: '" + metadata.genre + "'");
            } else {
                if (AutoStepper.STEP_DEBUG) System.out.println("No ID3 tags found in file");
            }

        } catch (Exception e) {
            if (AutoStepper.STEP_DEBUG) System.out.println("Error reading ID3 tags: " + e.getMessage());
        }

        return metadata;
    }

    /**
     * Simple data class to hold audio metadata
     */
    public static class AudioMetadata {
        public String title = "";
        public String artist = "";
        public String genre = "";

        /**
         * Check if any metadata was found
         */
        public boolean hasData() {
            return !title.isEmpty() || !artist.isEmpty() || !genre.isEmpty();
        }

        /**
         * Get title with fallback to artist if title is empty
         */
        public String getDisplayTitle() {
            if (!title.isEmpty()) {
                return title;
            } else if (!artist.isEmpty()) {
                return artist;
            } else {
                return "";
            }
        }
    }
}
