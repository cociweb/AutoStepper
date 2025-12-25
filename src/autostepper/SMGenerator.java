/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package autostepper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;



public class SMGenerator {
 
    private static final Logger logger = Logger.getLogger(SMGenerator.class.getName());

    private SMGenerator() {}
    private static final String HEADER = 
            "#TITLE:$TITLE;\n" +
            "#SUBTITLE:;\n" +
            "#ARTIST:$ARTIST;\n" +
            "#TITLETRANSLIT:;\n" +
            "#SUBTITLETRANSLIT:;\n" +
            "#ARTISTTRANSLIT:;\n" +
            "#GENRE:$GENRE;\n" +
            "#CREDIT:cociweb;\n" +
            "#BANNER:$BGIMAGE;\n" +
            "#BACKGROUND:$BGIMAGE;\n" +
            "#LYRICSPATH:;\n" +
            "#CDTITLE:;\n" +
            "#MUSIC:$MUSICFILE;\n" +
            "#OFFSET:$STARTTIME;\n" +
            "#SAMPLESTART:30.0;\n" +
            "#SAMPLELENGTH:30.0;\n" +
            "#SELECTABLE:YES;\n" +
            "#BPMS:0.000000=$BPM;\n" +
            "#STOPS:;\n" +
            "#KEYSOUNDS:;\n" +
            "#ATTACKS:;";
    
    public static String getChallenge() { return "Challenge:\n" + (AutoStepper.isHardMode() ? "     10:" : "     9:"); }

    public static String getHard() { return "Hard:\n" + (AutoStepper.isHardMode() ? "     8:" : "     7:"); }

    public static String getMedium() { return "Medium:\n" + (AutoStepper.isHardMode() ? "     6:" : "     5:"); }

    public static String getEasy() { return "Easy:\n" + (AutoStepper.isHardMode() ? "     4:" : "     3:"); }

    public static String getBeginner() { return "Beginner:\n" + (AutoStepper.isHardMode() ? "     2:" : "     1:"); }
    
    private static final String NOTE_FRAMEWORK =
            "//---------------dance-single - ----------------\n" +
            "#NOTES:\n" +
            "     dance-single:\n" +
            "     :\n" +
            "     $DIFFICULTY\n" +
            "     0.733800,0.772920,0.048611,0.850698,0.060764,634.000000,628.000000,6.000000,105.000000,8.000000,0.000000,0.733800,0.772920,0.048611,0.850698,0.060764,634.000000,628.000000,6.000000,105.000000,8.000000,0.000000:\n" +
            "$NOTES\n" +
            ";\n\n";

    public static String getHeader() { return HEADER; }
    
    public static String getNoteFramework() { return NOTE_FRAMEWORK; }

    private static void copyFileUsingStream(File source, File dest) throws IOException {
        try (InputStream is = new FileInputStream(source);
             OutputStream os = new FileOutputStream(dest)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        }
    }    
    
    public static void addNotes(BufferedWriter smfile, String difficulty, String notes) {
        try {
            smfile.write(getNoteFramework().replace("$DIFFICULTY", difficulty).replace("$NOTES", notes));
        } catch(Exception e) { 
            // Ignore exceptions during note writing
        }
    }
    
    public static void complete(BufferedWriter smfile) {
        try {
            smfile.close();
        } catch(Exception e) { 
            // Ignore exceptions during file closing
        }
    }

    public static File getSMFile(File songFile, String outputdir) {
        String filename = songFile.getName();
        File dir = new File(outputdir, filename + "_dir/");
        return new File(dir, filename + ".sm");
    }
    
    public static BufferedWriter generateSm(float bpm, float startTime, File songfile, String outputdir) {
        String filename = songfile.getName();
        String songname = filename.replace(".mp3", " ").replace(".wav", " ").replace(".com", " ").replace(".org", " ").replace(".info", " ");

        // Read ID3 metadata first
        ID3TagReader.AudioMetadata metadata = ID3TagReader.readMetadata(songfile);

        // Use ID3 data primarily, fallback to filename parsing
        String title;
        String artist;
        String genre;

        if (!metadata.getTitle().isEmpty()) {
            title = metadata.getTitle();
        } else {
            // Clean up song name for title - remove YouTube URLs and other noise
            songname = songname.replaceAll("\\([^)]*\\)", "") // Remove all parentheses and content
                               .replaceAll("\\[[^\\]]*+\\]", "") // Remove brackets and content
                               .replaceAll("\\b\\d{4}K\\b", "") // Remove resolution like 4K
                               .replaceAll("\\bOfficial\\b", "") // Remove "Official"
                               .replaceAll("\\bVideo\\b", "") // Remove "Video"
                               .replaceAll("\\bMusic\\b", "") // Remove "Music"
                               .replaceAll("\\bAudio\\b", "") // Remove "Audio"
                               .replaceAll("\\bRemastered\\b", "") // Remove "Remastered"
                               .replaceAll("\\s+", " ") // Normalize spaces
                               .trim();
            title = songname.length() > 30 ? songname.substring(0, 30) : songname;
        }

        if (!metadata.getArtist().isEmpty()) {
            artist = metadata.getArtist();
        } else {
            // Try to extract artist from filename (format: "Artist - Title")
            String[] parts = songname.split("\\s*-\\s*", 2);
            if (parts.length >= 2) {
                artist = parts[0].trim();
            } else {
                artist = "Unknown Artist";
            }
        }

        genre = metadata.getGenre().isEmpty() ? "" : metadata.getGenre();

        if (AutoStepper.isStepDebug() && logger.isLoggable(java.util.logging.Level.FINE)) logger.fine(String.format("Using metadata - Title: '%s', Artist: '%s', Genre: '%s'", title, artist, genre));

        String shortName = title.length() > 30 ? title.substring(0, 30) : title;
        File dir = new File(outputdir, filename + "_dir/");
        dir.mkdirs();
        File smfile = new File(dir, filename + ".sm");
        // get image for sm
        File imgFile = new File(dir, filename + "_img.png");
        String imgFileName = "";
        if (!imgFile.exists() && AutoStepper.DOWNLOADIMAGES) {
            if (AutoStepper.isStepDebug()) logger.fine("Attempting to get image for background & banner...");
            
            // Create better search terms using ID3 metadata when available
            String searchTerm = "";
            if (!title.isEmpty() && !artist.isEmpty()) {
                searchTerm = artist + " " + title;  // Best: "Artist Title"
            } else if (!title.isEmpty()) {
                searchTerm = title;  // Good: Just title
            } else if (!artist.isEmpty()) {
                searchTerm = artist;  // OK: Just artist
            } else {
                searchTerm = songname;  // Fallback: cleaned filename
            }
            
            if (AutoStepper.isStepDebug() && logger.isLoggable(java.util.logging.Level.FINE)) logger.fine(String.format("Searching for album art with: '%s'", searchTerm));
            
            try {
                // Try iTunes API first (better results)
                MusicCatalogAPI.findAlbumArt(searchTerm, imgFile.getAbsolutePath());
            } catch (Exception e) {
                // Ignore exceptions during file writing
            }        // Ignore exceptions during file operations
                if (AutoStepper.isStepDebug()) logger.fine("iTunes API failed: " + e.getMessage());
                try {
                    // Fallback to Google Images
                    GoogleImageSearch.findAndSaveImage(searchTerm, imgFile.getAbsolutePath());
                } catch (Exception e2) {
                    if (AutoStepper.isStepDebug()) logger.fine("All image search methods failed: " + e2.getMessage());
                }
            }
        } else if (!AutoStepper.DOWNLOADIMAGES && AutoStepper.isStepDebug()) {
            logger.fine("Image downloading disabled (use downloadimages=false to disable)");
        }
        if( imgFile.exists() ) {
            if (AutoStepper.isStepDebug()) logger.fine("Got an image file!");
            imgFileName = imgFile.getName();
        } else if (AutoStepper.isStepDebug()) logger.fine("No image file to use :(");
        try {
            boolean deleted = smfile.delete();
            if (AutoStepper.isStepDebug() && !deleted) {
                logger.fine("Failed to delete existing SM file");
            }
            copyFileUsingStream(songfile, new File(dir, filename));
            BufferedWriter writer = new BufferedWriter(new FileWriter(smfile));
            writer.write(Header.replace("$TITLE", shortName)
                             .replace("$ARTIST", artist)
                             .replace("$GENRE", genre)
                             .replace("$BGIMAGE", imgFileName)
                             .replace("$MUSICFILE", filename)
                             .replace("$STARTTIME", Float.toString(startTime + AutoStepper.STARTSYNC))
                             .replace("$BPM", Float.toString(BPM)));
            return writer;
        } catch(Exception e) {
            // Ignore exceptions during file writing
        }
        return null;
    }
}
