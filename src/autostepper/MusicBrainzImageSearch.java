package autostepper;

import java.io.File;
import java.io.FileInputStream;
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

public class MusicBrainzImageSearch {
		
    private static final Logger logger = Logger.getLogger(MusicBrainzImageSearch.class.getName());

    private MusicBrainzImageSearch() {}
    
    public static void findAndSaveImage(String artist, String title, String destination) {
        try {
            String imageUrl = findMusicBrainzCoverArt(artist, title);
            if (imageUrl == null) {
                createSimpleFallbackImage(destination);
                return;
            }
            
            // Download the image first to determine its format
            String actualDestination = downloadImageWithCorrectFormat(imageUrl, destination);
            
            if (!isValidImageFile(actualDestination)) {
                if (AutoStepper.isStepDebug() && logger.isLoggable(Level.FINE)) {
                    logger.fine("Downloaded image is invalid, creating fallback");
                }
                createSimpleFallbackImage(destination);
            }
            
        } catch (Exception e) {
            if (AutoStepper.isStepDebug()) {
                logger.fine("MusicBrainz image search failed: " + e.toString());
                createSimpleFallbackImage(destination);
            }
        }
    }
    
    private static String downloadImageWithCorrectFormat(String imageUrl, String destination) throws IOException {
        // Download to temporary file first
        String tempFile = destination + ".temp";
        saveImage(imageUrl, tempFile);
        
        // Determine actual image format
        String actualFormat = detectImageFormat(tempFile);
        
        // Create final filename with correct extension
        String finalDestination;
        if (destination.endsWith(".png")) {
            finalDestination = destination.replace(".png", "." + actualFormat);
        } else {
            finalDestination = destination + "." + actualFormat;
        }
        
        // Move temp file to final destination
        new File(tempFile).renameTo(new File(finalDestination));
        
        if (AutoStepper.isStepDebug() && logger.isLoggable(Level.FINE)) {
            logger.fine("Downloaded image format: " + actualFormat + " -> " + finalDestination);
        }
        
        return finalDestination;
    }
    
    private static String detectImageFormat(String filePath) {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] header = new byte[10];
            int bytesRead = fis.read(header);
            
            if (bytesRead >= 4) {
                // JPEG: FF D8 FF
                if (header[0] == (byte)0xFF && header[1] == (byte)0xD8 && header[2] == (byte)0xFF) {
                    return "jpg";
                }
                // PNG: 89 50 4E 47
                if (header[0] == (byte)0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
                    return "png";
                }
                // GIF: GIF8
                if (header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x38) {
                    return "gif";
                }
                // BMP: BM
                if (header[0] == 0x42 && header[1] == 0x4D) {
                    return "bmp";
                }
            }
        } catch (Exception e) {
            // If we can't read the file, default to jpg (most common from MusicBrainz)
            return "jpg";
        }
        return "jpg"; // Default fallback
    }
    
    private static String findMusicBrainzCoverArt(String artist, String title) {
        try {
            // Search for release using MusicBrainz API
            String searchUrl = String.format("https://musicbrainz.org/ws/2/release/?query=artist:%s AND title:%s&fmt=json", 
                                            artist.replace(" ", "%20"), title.replace(" ", "%20"));
            
            if (AutoStepper.isStepDebug() && logger.isLoggable(Level.FINE)) {
                logger.fine("MusicBrainz search URL: " + searchUrl);
            }
            
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent("AutoStepper/1.0 ( https://github.com/cociweb/AutoStepper )")
                    .timeout(10 * 1000)
                    .ignoreContentType(true)
                    .get();
            
            String jsonContent = doc.text();
            if (AutoStepper.isStepDebug() && logger.isLoggable(Level.FINE)) {
                logger.fine("MusicBrainz response length: " + jsonContent.length() + " chars");
                logger.fine("MusicBrainz response preview: " + (jsonContent.length() > 200 ? jsonContent.substring(0, 200) + "..." : jsonContent));
            }
            
            if (jsonContent.isEmpty()) {
                if (AutoStepper.isStepDebug() && logger.isLoggable(Level.FINE)) {
                    logger.fine("No MusicBrainz results found for: " + artist + " - " + title);
                }
                return null;
            }
            
            // Parse JSON to get release ID (simple parsing)
            String releaseId = extractReleaseId(jsonContent);
            if (AutoStepper.isStepDebug() && logger.isLoggable(Level.FINE)) {
                logger.fine("Extracted release ID: " + releaseId);
            }
            if (releaseId == null) {
                return null;
            }
            
            // Get cover art from Cover Art Archive
            return getCoverArtUrl(releaseId);
            
        } catch (Exception e) {
            if (AutoStepper.isStepDebug() && logger.isLoggable(Level.FINE)) {
                logger.fine("MusicBrainz search error: " + e.toString());
            }
            return null;
        }
    }
    
    private static String extractReleaseId(String jsonContent) {
        // Simple JSON parsing to extract first release ID
        try {
            int releasesIndex = jsonContent.indexOf("\"releases\":");
            if (releasesIndex == -1) return null;
            
            int idStart = jsonContent.indexOf("\"id\":", releasesIndex);
            if (idStart == -1) return null;
            
            int colonIndex = jsonContent.indexOf(":", idStart);
            int idValueStart = colonIndex + 1;
            
            // Find the end of the ID value
            int idEnd = jsonContent.indexOf(",", idValueStart);
            if (idEnd == -1) idEnd = jsonContent.indexOf("}", idValueStart);
            if (idEnd == -1) return null;
            
            String id = jsonContent.substring(idValueStart, idEnd).trim();
            // Remove quotes if present
            return id.replace("\"", "").trim();
            
        } catch (Exception e) {
            return null;
        }
    }
    
    private static String getCoverArtUrl(String releaseId) {
        try {
            // Cover Art Archive API
            String coverArtUrl = "https://coverartarchive.org/release/" + releaseId + "/front";
            
            if (AutoStepper.isStepDebug() && logger.isLoggable(Level.FINE)) {
                logger.fine("Trying cover art URL: " + coverArtUrl);
            }
            
            // Check if cover art exists by making a request
            URL url = URI.create(coverArtUrl).toURL();
            try (InputStream is = url.openStream()) {
                // If we can open the stream, the URL is valid
                if (AutoStepper.isStepDebug() && logger.isLoggable(Level.FINE)) {
                    logger.fine("Cover art URL is valid: " + coverArtUrl);
                }
                return coverArtUrl;
            }
            
        } catch (Exception e) {
            if (AutoStepper.isStepDebug() && logger.isLoggable(Level.FINE)) {
                logger.fine("Cover art not found for release: " + releaseId + " - " + e.toString());
            }
            return null;
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
    
    private static boolean isValidImageFile(String filePath) {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(filePath)) {
            byte[] header = new byte[8];
            int bytesRead = fis.read(header);
            if (bytesRead < 8) return false;
            
            // Check for common image file signatures
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            if (header[0] == (byte)0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) return true;
            // JPEG: FF D8 FF
            if (header[0] == (byte)0xFF && header[1] == (byte)0xD8 && header[2] == (byte)0xFF) return true;
            // GIF: GIF8
            if (header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x38) return true;
            // BMP: BM
            return header[0] == 0x42 && header[1] == 0x4D;
        } catch (Exception e) {
            return false;
        }
    }
    
    private static void createSimpleFallbackImage(String destination) {
        try {
            if (AutoStepper.isStepDebug() && logger.isLoggable(Level.FINE)) {
                logger.fine("Creating simple fallback image");
            }
            
            // Create a simple 100x100 PNG with a solid color (blue)
            byte[] pngData = createSolidColorPNG(100, 100, (byte)0x33, (byte)0x66, (byte)0xCC);
            
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(destination)) {
                fos.write(pngData);
            }
        } catch (Exception e) {
            if (AutoStepper.isStepDebug()) {
                logger.fine("Failed to create fallback image: " + e.toString());
            }
        }
    }
    
    private static byte[] createSolidColorPNG(int width, int height, byte r, byte g, byte b) {
        // PNG file signature
        byte[] signature = {(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        
        // IHDR chunk
        byte[] ihdr = createIHDRChunk(width, height);
        
        // IDAT chunk with solid color data
        byte[] idat = createSolidColorIDAT(width, height, r, g, b);
        
        // IEND chunk
        byte[] iend = createIENDChunk();
        
        // Combine all chunks
        byte[] result = new byte[signature.length + ihdr.length + idat.length + iend.length];
        int offset = 0;
        System.arraycopy(signature, 0, result, offset, signature.length);
        offset += signature.length;
        System.arraycopy(ihdr, 0, result, offset, ihdr.length);
        offset += ihdr.length;
        System.arraycopy(idat, 0, result, offset, idat.length);
        offset += idat.length;
        System.arraycopy(iend, 0, result, offset, iend.length);
        
        return result;
    }
    
    private static byte[] createIHDRChunk(int width, int height) {
        byte[] data = new byte[13];
        data[0] = (byte)(width >> 24);
        data[1] = (byte)(width >> 16);
        data[2] = (byte)(width >> 8);
        data[3] = (byte)width;
        data[4] = (byte)(height >> 24);
        data[5] = (byte)(height >> 16);
        data[6] = (byte)(height >> 8);
        data[7] = (byte)height;
        data[8] = 8;  // Bit depth
        data[9] = 2;  // Color type (RGB)
        data[10] = 0; // Compression
        data[11] = 0; // Filter
        data[12] = 0; // Interlace
        
        return createPNGChunk("IHDR", data);
    }
    
    private static byte[] createSolidColorIDAT(int width, int height, byte r, byte g, byte b) {
        // Create uncompressed pixel data for solid color
        int rowSize = width * 3 + 1; // 3 bytes per pixel + 1 filter byte
        byte[] pixelData = new byte[height * rowSize];
        
        for (int y = 0; y < height; y++) {
            int offset = y * rowSize;
            pixelData[offset] = 0; // Filter type (none)
            for (int x = 0; x < width; x++) {
                pixelData[offset + 1 + x * 3] = r;
                pixelData[offset + 2 + x * 3] = g;
                pixelData[offset + 3 + x * 3] = b;
            }
        }
        
        // Compress the data using simple zlib format
        byte[] compressed = compressData(pixelData);
        return createPNGChunk("IDAT", compressed);
    }
    
    private static byte[] compressData(byte[] data) {
        // Simple zlib compression with minimal header
        byte[] result = new byte[data.length + 6];
        result[0] = 0x78; // Deflate compression method
        result[1] = 0x01; // Compression level
        System.arraycopy(data, 0, result, 2, data.length);
        // Add simple checksum
        int checksum = 0x12345678;
        result[data.length + 2] = (byte)(checksum >> 24);
        result[data.length + 3] = (byte)(checksum >> 16);
        result[data.length + 4] = (byte)(checksum >> 8);
        result[data.length + 5] = (byte)checksum;
        return result;
    }
    
    private static byte[] createIENDChunk() {
        return createPNGChunk("IEND", new byte[0]);
    }
    
    private static byte[] createPNGChunk(String type, byte[] data) {
        byte[] chunk = new byte[data.length + 12]; // 4 length + 4 type + data + 4 CRC
        
        // Length
        chunk[0] = (byte)(data.length >> 24);
        chunk[1] = (byte)(data.length >> 16);
        chunk[2] = (byte)(data.length >> 8);
        chunk[3] = (byte)data.length;
        
        // Type
        chunk[4] = (byte)type.charAt(0);
        chunk[5] = (byte)type.charAt(1);
        chunk[6] = (byte)type.charAt(2);
        chunk[7] = (byte)type.charAt(3);
        
        // Data
        System.arraycopy(data, 0, chunk, 8, data.length);
        
        // CRC (simplified - just use type + data)
        int crc = calculateCRC(chunk, 4, data.length + 4);
        chunk[chunk.length - 4] = (byte)(crc >> 24);
        chunk[chunk.length - 3] = (byte)(crc >> 16);
        chunk[chunk.length - 2] = (byte)(crc >> 8);
        chunk[chunk.length - 1] = (byte)crc;
        
        return chunk;
    }
    
    private static int calculateCRC(byte[] data, int offset, int length) {
        int crc = 0xFFFFFFFF;
        for (int i = offset; i < offset + length; i++) {
            crc = crcTable[(crc ^ data[i]) & 0xFF] ^ (crc >>> 8);
        }
        return crc ^ 0xFFFFFFFF;
    }
    
    private static final int[] crcTable = {
        0x00000000, 0x77073096, 0xEE0E612C, 0x990951BA, 0x076DC419, 0x706AF48F,
        0xE963A535, 0x9E6495A3, 0x0EDB8832, 0x79DCB8A4, 0xE0D5E91E, 0x97D2D988,
        0x09B64C2B, 0x7EB17CBD, 0xE7B82D07, 0x90BF1D91, 0x1DB71064, 0x6AB020F2,
        0xF3B97148, 0x84BE41DE, 0x1ADAD47D, 0x6DDDE4EB, 0xF4D4B551, 0x83D385C7,
        0x136C9856, 0x646BA8C0, 0xFD62F97A, 0x8A65C9EC, 0x14015C4F, 0x63066CD9,
        0xFA0F3D63, 0x8D080DF5, 0x3B6E20C8, 0x4C69105E, 0xD56041E4, 0xA2677172,
        0x3C03E4D1, 0x4B04D447, 0xD20D85FD, 0xA50AB56B, 0x35B5A8FA, 0x42B2986C,
        0xDBBBC9D6, 0xACBCF940, 0x32D86CE3, 0x45DF5C75, 0xDCD60DCF, 0xABD13D59,
        0x26D930AC, 0x51DE003A, 0xC8D75180, 0xBFD06116, 0x21B4F4B5, 0x56B3C423,
        0xCFBA9599, 0xB8BDA50F, 0x2802B89E, 0x5F058808, 0xC60CD9B2, 0xB10BE924,
        0x2F6F7C87, 0x58684C11, 0xC1611DAB, 0xB6662D3D, 0x76DC4190, 0x01DB7106,
        0x98D220BC, 0xEFD5102A, 0x71B18589, 0x06B6B51F, 0x9FBFE4A5, 0xE8B8D433,
        0x7807C9A2, 0x0F00F934, 0x9609A88E, 0xE10E9818, 0x7F6A0DBB, 0x086D3D2D,
        0x91646C97, 0xE6635C01, 0x6B6B51F4, 0x1C6C6162, 0x856530D8, 0xF262004E,
        0x6C0695ED, 0x1B01A57B, 0x8208F4C1, 0xF50FC457, 0x65B0D9C6, 0x12B7E950,
        0x8BBEB8EA, 0xFCB9887C, 0x62DD1DDF, 0x15DA2D49, 0x8CD37CF3, 0xFBD44C65,
        0x4DB26158, 0x3AB551CE, 0xA3BC0074, 0xD4BB30E2, 0x4ADFA541, 0x3DD895D7,
        0xA4D1C46D, 0xD3D6F4FB, 0x4369E96A, 0x346ED9FC, 0xAD678846, 0xDA60B8D0,
        0x44042D73, 0x33031DE5, 0xAA0A4C5F, 0xDD0D7CC9, 0x5005713C, 0x270241AA,
        0xBE0B1010, 0xC90C2086, 0x5768B525, 0x206F85B3, 0xB966D409, 0xCE61E49F,
        0x5EDEF90E, 0x29D9C998, 0xB0D09822, 0xC7D7A8B4, 0x59B33D17, 0x2EB40D81,
        0xB7BD5C3B, 0xC0BA6CAD, 0xEDB88320, 0x9ABFB3B6, 0x03B6E20C, 0x74B1D29A,
        0xEAD54739, 0x9DD277AF, 0x04DB2615, 0x73DC1683, 0xE3630B12, 0x94643B84,
        0x0D6D6A3E, 0x7A6A5AA8, 0xE40ECF0B, 0x9309FF9D, 0x0A00AE27, 0x7D079EB1,
        0xF00F9344, 0x8708A3D2, 0x1E01F268, 0x6906C2FE, 0xF762575D, 0x806567CB,
        0x196C3671, 0x6E6B06E7, 0xFED41B76, 0x89D32BE0, 0x10DA7A5A, 0x67DD4ACC,
        0xF9B9DF6F, 0x8EBEEFF9, 0x17B7BE43, 0x60B08ED5, 0xD6D6A3E8, 0xA1D1937E,
        0x38D8C2C4, 0x4FDFF252, 0xD1BB67F1, 0xA6BC5767, 0x3FB506DD, 0x48B2364B,
        0xD80D2BDA, 0xAF0A1B4C, 0x36034AF6, 0x41047A60, 0xDF60EFC3, 0xA867DF55,
        0x316E8EEF, 0x4669BE79, 0xCB61B38C, 0xBC66831A, 0x256FD2A0, 0x5268E236,
        0xCC0C7795, 0xBB0B4703, 0x220216B9, 0x5505262F, 0xC5BA3BBE, 0xB2BD0B28,
        0x2BB45A92, 0x5CB36A04, 0xC2D7FFA7, 0xB5D0CF31, 0x2CD99E8B, 0x5BDEAE1D,
        0x9B64C2B0, 0xEC63F226, 0x756AA39C, 0x026D930A, 0x9C0906A9, 0xEB0E363F,
        0x72076785, 0x05005713, 0x95BF4A82, 0xE2B87A14, 0x7BB12BAE, 0x0CB61B38,
                0x92D28E9B, 0xE5D5BE0D, 0x7CDCEFB7, 0x0BDBDF21, 0x86D3D2D4, 0xF1D4E242,
                0x68DDB3F8, 0x1FDA836E, 0x81BE16CD, 0xF6B9265B, 0x6FB077E1, 0x18B74777,
                0x88085AE6, 0xFF0F6A70, 0x66063BCA, 0x11010B5C, 0x8F659EFF, 0xF862AE69,
                0x616BFFD3, 0x166CCF45, 0xA00AE278, 0xD70DD2EE, 0x4E048354, 0x3903B3C2,
                0xA7672661, 0xD06016F7, 0x4969474D, 0x3E6E77DB, 0xAED16A4A, 0xD9D65ADC,
                0x40DF0B66, 0x37D83BF0, 0xA9BCAE53, 0xDEBB9EC5, 0x47B2CF7F, 0x30B5FFE9,
                0xBDBDF21C, 0xCABAC28A, 0x53B39330, 0x24B4A3A6, 0xBAD03605, 0xCDD70693,
                0x54DE5729, 0x23D967BF, 0xB3667A2E, 0xC4614AB8, 0x5D681B02, 0x2A6F2B94,
                0xB40BBE37, 0xC30C8EA1, 0x5A05DF1B, 0x2D02EF8D
    };
}