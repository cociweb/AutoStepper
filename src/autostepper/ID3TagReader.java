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

    private static final Logger logger = Logger.getLogger(ID3TagReader.class.getName());

    private ID3TagReader() {}

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
            processTag(tag, metadata);
        } catch (Exception e) {
            if (AutoStepper.isStepDebug()) logger.fine("Error reading ID3 tags: " + e.getMessage());
        }

        return metadata;
    }

    private static void processTag(Tag tag, AudioMetadata metadata) {
        if (tag == null) {
            if (AutoStepper.isStepDebug()) logger.fine("No ID3 tags found in file");
            return;
        }

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

        if (AutoStepper.isStepDebug() && logger.isLoggable(java.util.logging.Level.FINE)) logger.fine(String.format("Read ID3 tags - Title: '%s', Artist: '%s', Genre: '%s'", metadata.getTitle(), metadata.getArtist(), metadata.getGenre()));
    }

    /**
     * Simple data class to hold audio metadata
     */
    public static class AudioMetadata {
        private String title = "";
        private String artist = "";
        private String genre = "";

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

        public String getTitle() { return title; }
        public String getArtist() { return artist; }
        public String getGenre() { return genre; }
    }
}
