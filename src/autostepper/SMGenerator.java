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

// Add import for MusicCatalogAPI
import autostepper.MusicCatalogAPI;
// Add import for ID3 tag reading
import autostepper.ID3TagReader;

/**
 *
 * @author Phr00t
 */
public class SMGenerator {
 
    private static String Header = 
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
    
    public static String Challenge =
            "Challenge:\n" +
            "     10:";

    public static String Hard =
            "Hard:\n" +
            "     8:";

    public static String Medium =
            "Medium:\n" +
            "     6:";

    public static String Easy =
            "Easy:\n" +
            "     4:";

    public static String Beginner =
            "Beginner:\n" +
            "     2:";
    
    private static String NoteFramework =
            "//---------------dance-single - ----------------\n" +
            "#NOTES:\n" +
            "     dance-single:\n" +
            "     :\n" +
            "     $DIFFICULTY\n" +
            "     0.733800,0.772920,0.048611,0.850698,0.060764,634.000000,628.000000,6.000000,105.000000,8.000000,0.000000,0.733800,0.772920,0.048611,0.850698,0.060764,634.000000,628.000000,6.000000,105.000000,8.000000,0.000000:\n" +
            "$NOTES\n" +
            ";\n\n";

    private static void copyFileUsingStream(File source, File dest) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            is.close();
            os.close();
        }
    }    
    
    public static void AddNotes(BufferedWriter smfile, String difficulty, String notes) {
        try {
            smfile.write(NoteFramework.replace("$DIFFICULTY", difficulty).replace("$NOTES", notes));
        } catch(Exception e) { }
    }
    
    public static void Complete(BufferedWriter smfile) {
        try {
            smfile.close();
        } catch(Exception e) { }
    }

    public static File getSMFile(File songFile, String outputdir) {
        String filename = songFile.getName();
        File dir = new File(outputdir, filename + "_dir/");
        return new File(dir, filename + ".sm");
    }
    
    public static BufferedWriter GenerateSM(float BPM, float startTime, File songfile, String outputdir) {
        String filename = songfile.getName();
        String songname = filename.replace(".mp3", " ").replace(".wav", " ").replace(".com", " ").replace(".org", " ").replace(".info", " ");

        // Read ID3 metadata first
        ID3TagReader.AudioMetadata metadata = ID3TagReader.readMetadata(songfile);

        // Use ID3 data primarily, fallback to filename parsing
        String title, artist, genre;

        if (!metadata.title.isEmpty()) {
            title = metadata.title;
        } else {
            // Clean up song name for title - remove YouTube URLs and other noise
            songname = songname.replaceAll("\\([^)]*\\)", "") // Remove all parentheses and content
                               .replaceAll("\\[.*?\\]", "") // Remove brackets and content
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

        if (!metadata.artist.isEmpty()) {
            artist = metadata.artist;
        } else {
            // Try to extract artist from filename (format: "Artist - Title")
            String[] parts = songname.split("\\s*-\\s*", 2);
            if (parts.length >= 2) {
                artist = parts[0].trim();
            } else {
                artist = "Unknown Artist";
            }
        }

        genre = metadata.genre.isEmpty() ? "" : metadata.genre;

        System.out.println("Using metadata - Title: '" + title + "', Artist: '" + artist + "', Genre: '" + genre + "'");

        String shortName = title.length() > 30 ? title.substring(0, 30) : title;
        File dir = new File(outputdir, filename + "_dir/");
        dir.mkdirs();
        File smfile = new File(dir, filename + ".sm");
        // get image for sm
        File imgFile = new File(dir, filename + "_img.png");
        String imgFileName = "";
        if( imgFile.exists() == false && AutoStepper.DOWNLOADIMAGES ) {
            System.out.println("Attempting to get image for background & banner...");
            try {
                // Try iTunes API first (better results)
                MusicCatalogAPI.findAlbumArt(songname, imgFile.getAbsolutePath());
            } catch (Exception e) {
                System.out.println("iTunes API failed: " + e.getMessage());
                try {
                    // Fallback to Google Images
                    GoogleImageSearch.FindAndSaveImage(songname, imgFile.getAbsolutePath());
                } catch (Exception e2) {
                    System.out.println("All image search methods failed: " + e2.getMessage());
                }
            }
        } else if (!AutoStepper.DOWNLOADIMAGES) {
            System.out.println("Image downloading disabled (use downloadimages=false to disable)");
        }
        if( imgFile.exists() ) {
            System.out.println("Got an image file!");
            imgFileName = imgFile.getName();
        } else System.out.println("No image file to use :(");
        try {
            smfile.delete();
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
        } catch(Exception e) {}
        return null;
    }
}
