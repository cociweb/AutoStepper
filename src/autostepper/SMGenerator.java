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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.logging.Logger;



public class SMGenerator {
 
    private static final Logger logger = Logger.getLogger(SMGenerator.class.getName());
    private static final String DIR_SUFFIX = "_dir/";

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
            "#SAMPLESTART:$SAMPLESTART;\n" +
            "#SAMPLELENGTH:$SAMPLELENGTH;\n" +
            "#SELECTABLE:YES;\n" +
            "#BPMS:$BPM;\n" +
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
        File dir = new File(outputdir, filename + DIR_SUFFIX);
        return new File(dir, filename + ".sm");
    }
    
    public static BufferedWriter generateSmFromPath(float bpm, float startTime, File songfile, String outputdir) {
        return generateSmFromPath(bpm, startTime, null, songfile, outputdir, 0f);
    }
    
    /**
     * Generates SM file with support for multiple BPM changes.
     * @param bpm Primary BPM value
     * @param startTime Start time offset
     * @param bpmChanges List of BPM changes (timestamp, bpm pairs), or null for constant tempo
     * @param songfile Source audio file
     * @param outputdir Output directory
     * @param songDuration Actual song duration in seconds (0 = use default 30s)
     */
    public static BufferedWriter generateSmFromPath(float bpm, float startTime, ArrayList<AutoStepper.BPMChange> bpmChanges, File songfile, String outputdir, float songDuration) {
        String filename = songfile.getName();
        
        // Extract and process song metadata
        SongMetadata metadata = extractSongMetadata(songfile, filename);
        
        // Try to find or download image
        String imgFileName = findImageFile(metadata.shortName, metadata.artist, new File(outputdir, filename + DIR_SUFFIX), filename);
        
        // Set up output files
        File smfile = setupOutputFile(outputdir, filename);
        
        // Write SM file content
        return writeSMFile(smfile, songfile, metadata, imgFileName, bpm, startTime, bpmChanges, filename, songDuration);
    }
    
    private static class SongMetadata {
        String title;
        String artist;
        String genre;
        String shortName;
        
        SongMetadata(String title, String artist, String genre, String shortName) {
            this.title = title;
            this.artist = artist;
            this.genre = genre;
            this.shortName = shortName;
        }
    }
    
    private static SongMetadata extractSongMetadata(File songfile, String filename) {
        String songname = filename.replace(".mp3", " ").replace(".wav", " ").replace(".com", " ").replace(".org", " ").replace(".info", " ");

        // Read ID3 metadata first
        ID3TagReader.AudioMetadata metadata = ID3TagReader.readMetadata(songfile);

        // Use ID3 data primarily, fallback to filename parsing
        String title = extractTitle(metadata, songname);
        String artist = extractArtist(metadata, songname);
        String genre = metadata.getGenre().isEmpty() ? "" : metadata.getGenre();

        if (AutoStepper.isStepDebug() && logger.isLoggable(java.util.logging.Level.FINE)) {
            logger.fine(String.format("Using metadata - Title: '%s', Artist: '%s', Genre: '%s'", title, artist, genre));
        }

        String shortName = title.length() > 30 ? title.substring(0, 30) : title;
        return new SongMetadata(title, artist, genre, shortName);
    }
    
    private static String extractTitle(ID3TagReader.AudioMetadata metadata, String songname) {
        if (!metadata.getTitle().isEmpty()) {
            return metadata.getTitle();
        } else {
            // Clean up song name for title - remove YouTube URLs and other noise
            String cleaned = songname.replaceAll("\\([^)]*\\)", "") // Remove all parentheses and content
                                   .replaceAll("\\[[^\\]]*+\\]", "") // Remove brackets and content
                                   .replaceAll("\\b\\d{4}K\\b", "") // Remove resolution like 4K
                                   .replaceAll("\\bOfficial\\b", "") // Remove "Official"
                                   .replaceAll("\\bVideo\\b", "") // Remove "Video"
                                   .replaceAll("\\bMusic\\b", "") // Remove "Music"
                                   .replaceAll("\\bAudio\\b", "") // Remove "Audio"
                                   .replaceAll("\\bRemastered\\b", "") // Remove "Remastered"
                                   .replaceAll("\\s+", " ") // Normalize spaces
                                   .trim();
            return cleaned.length() > 30 ? cleaned.substring(0, 30) : cleaned;
        }
    }
    
    private static String extractArtist(ID3TagReader.AudioMetadata metadata, String songname) {
        if (!metadata.getArtist().isEmpty()) {
            return metadata.getArtist();
        } else {
            // Try to extract artist from filename (format: "Artist - Title")
            String[] parts = songname.split("\\s*-\\s*", 2);
            if (parts.length >= 2) {
                return parts[0].trim();
            } else {
                return "Unknown Artist";
            }
        }
    }
    
    private static String findImageFile(String title, String artist, File dir, String filename) {
        File imgFile = new File(dir, filename + "_img.png");
        
        if (!imgFile.exists() && AutoStepper.isDownloadImages()) {
            if (AutoStepper.isStepDebug()) logger.fine("Attempting to get image for background & banner...");
            
            // Create better search terms using ID3 metadata when available
            String searchTerm = createImageSearchTerm(title, artist);
            
            if (!searchTerm.isEmpty()) {
                downloadImage(searchTerm, imgFile);
            }
        } else if (!AutoStepper.isDownloadImages() && AutoStepper.isStepDebug()) {
            logger.fine("Image downloading disabled (use downloadimages=false to disable)");
        }
        
        return getImageFileName(imgFile);
    }
    
    private static String createImageSearchTerm(String title, String artist) {
        if (!title.isEmpty() && !artist.isEmpty()) {
            return artist + " " + title;  // Best: "Artist Title"
        } else if (!title.isEmpty()) {
            return title;  // Good: Just title
        } else if (!artist.isEmpty()) {
            return artist;  // OK: Just artist
        }
        return "";
    }
    
    private static void downloadImage(String searchTerm, File imgFile) {
        try {
            // Parse searchTerm back to artist and title
            String[] parts = searchTerm.split(" ", 2);
            String artist = parts.length > 0 ? parts[0] : "";
            String title = parts.length > 1 ? parts[1] : "";
            
            // Try MusicBrainz cover art
            MusicBrainzImageSearch.findAndSaveImage(artist, title, imgFile.getAbsolutePath());
        } catch (Exception e) {
            if (AutoStepper.isStepDebug()) logger.fine("Image search failed: " + e.getMessage());
        }
    }
    
    private static String getImageFileName(File imgFile) {
        if (imgFile.exists()) {
            if (AutoStepper.isStepDebug()) logger.fine("Got an image file!");
            return imgFile.getName();
        } else if (AutoStepper.isStepDebug()) {
            logger.fine("No image file to use :(");
        }
        return "";
    }
    
    private static File setupOutputFile(String outputdir, String filename) {
        File dir = new File(outputdir, filename + DIR_SUFFIX);
        dir.mkdirs();
        return new File(dir, filename + ".sm");
    }
    
    private static BufferedWriter writeSMFile(File smfile, File songfile, SongMetadata metadata, String imgFileName, float bpm, float startTime, String filename) {
        return writeSMFile(smfile, songfile, metadata, imgFileName, bpm, startTime, null, filename, 0f);
    }
    
    private static BufferedWriter writeSMFile(File smfile, File songfile, SongMetadata metadata, String imgFileName, float bpm, float startTime, ArrayList<AutoStepper.BPMChange> bpmChanges, String filename, float songDuration) {
        try {
            deleteExistingSMFile(smfile);
            copyFileUsingStream(songfile, new File(smfile.getParent(), filename));
            
            // Format BPM string - either single BPM or multiple BPM changes
            String bpmString = formatBPMString(bpm, bpmChanges);
            
            // Calculate sample start and length
            // For full songs (songDuration > 0), use actual duration
            // Otherwise use default 30 seconds for preview
            float sampleStart = (songDuration > 30f) ? 30.0f : 0.0f;
            float sampleLength = (songDuration > 0f) ? songDuration : 30.0f;
            
            BufferedWriter writer = new BufferedWriter(new FileWriter(smfile));
            writer.write(HEADER.replace("$TITLE", metadata.shortName)
                             .replace("$ARTIST", metadata.artist)
                             .replace("$GENRE", metadata.genre)
                             .replace("$BGIMAGE", imgFileName)
                             .replace("$MUSICFILE", filename)
                             .replace("$STARTTIME", Float.toString(startTime + AutoStepper.getStartSync()))
                             .replace("$SAMPLESTART", Float.toString(sampleStart))
                             .replace("$SAMPLELENGTH", Float.toString(sampleLength))
                             .replace("$BPM", bpmString));
            return writer;
        } catch(Exception e) {
            // Ignore exceptions during file writing
        }
        return null;
    }
    
    /**
     * Formats the BPM string for the #BPMS line in SM file format.
     * Format: timestamp1=bpm1,timestamp2=bpm2,...
     * Example: 0.000=128.000,64.000=256.000,164.000=156.000
     */
    private static String formatBPMString(float defaultBpm, ArrayList<AutoStepper.BPMChange> bpmChanges) {
        if (bpmChanges == null || bpmChanges.isEmpty()) {
            // Single constant BPM
            return String.format("%.3f", defaultBpm);
        }
        
        // Multiple BPM changes
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bpmChanges.size(); i++) {
            AutoStepper.BPMChange change = bpmChanges.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append(String.format("%.3f=%.3f", change.timestamp, change.bpm));
        }
        return sb.toString();
    }
    
    private static void deleteExistingSMFile(File smfile) {
        try {
            Files.delete(smfile.toPath());
            if (AutoStepper.isStepDebug()) logger.fine("Deleted existing SM file");
        } catch (IOException e) {
            if (AutoStepper.isStepDebug()) logger.fine("Failed to delete existing SM file: " + e.getMessage());
        }
    }
    
    public static BufferedWriter generateSmFromPath(float bpm, float startTime, String filename, String outputDir) {
        File songFile = new File(filename);
        return generateSmFromPath(bpm, startTime, songFile, outputDir);
    }
}
