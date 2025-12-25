package autostepper;

import ddf.minim.AudioSample;
import ddf.minim.Minim;
import ddf.minim.MultiChannelBuffer;
import ddf.minim.analysis.BeatDetect;
import ddf.minim.analysis.FFT;
import ddf.minim.spi.AudioRecordingStream;
import gnu.trove.list.array.TFloatArrayList;
import java.io.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

// This class is not a Singleton pattern - it uses static methods for utility functions
public class AutoStepper {
    
    private static boolean stepDebug = false;
    private static float maxBpm = 170f;
    private static float minBpm = 70f;
    private static float bpmSensitivity = 0.05f;
    private static float startSync = 0.0f;
    private static double tapSync = -0.11;
    private static boolean useTapper = false;
    private static boolean hardMode = false;
    private static boolean updateSm = false;
    private static boolean downloadImages = true;
    private static float clearance = 0.0f;
    
    public static boolean isStepDebug() { return stepDebug; }
    public static float getMaxBpm() { return maxBpm; }
    public static float getMinBpm() { return minBpm; }
    public static float getBpmSensitivity() { return bpmSensitivity; }
    public static float getStartSync() { return startSync; }
    public static double getTapSync() { return tapSync; }
    public static boolean isUseTapper() { return useTapper; }
    public static boolean isHardMode() { return hardMode; }
    public static boolean isUpdateSm() { return updateSm; }
    public static boolean isDownloadImages() { return downloadImages; }
    public static float getClearance() { return clearance; }
    
    private static final String SECONDS_SUFFIX = " seconds";
    
    private static final String DEFAULT_FALSE = "false";
    
    private static final Logger logger = Logger.getLogger(AutoStepper.class.getName());
    
    private static final String DURATION_ARG = "duration";
    private static final String MAX_BPM_ARG = "maxbpm";
    private static final String BPM_SENSITIVITY_ARG = "bpmsensitivity";
    private static final String START_SYNC_ARG = "synctime";
    private static final String TAP_ARG = "tap";
    private static final String TAP_SYNC_ARG = "tapsync";
    private static final String HARD_ARG = "hard";
    private static final String UPDATE_SM_ARG = "updatesm";
    private static final String DOWNLOAD_IMAGES_ARG = "downloadimages";
    private static final String DEBUG_ARG = "debug";
    private static final String CLEARANCE_ARG = "clearance";
    private static final String INPUT_ARG = "input";
    private static final String OUTPUT_ARG = "output";
    
    private static Minim minim;
    
    
    public static final int KICKS = 0;
    public static final int ENERGY = 1;
    public static final int SNARE = 2;
    public static final int HAT = 3;
    
    // for minim
    public String sketchPath( String fileName ) {
        return fileName; // Minim compatibility
    }
    
    // for minim
    public InputStream createInput( String fileName ) {
        try {
            return new FileInputStream(new File(fileName));
        } catch(Exception e) {
            return null;
        }
    }
    
    // argument parser
    public static String getArg(String[] args, String argname, String def) {
        try {
            for(String s : args) {
                s = s.replace("\"", "");
                if( s.startsWith(argname) ) {
                    return s.substring(s.indexOf("=") + 1).toLowerCase();
                }
            }
        } catch(Exception e) { /* Ignore exceptions during input operations */ }
        return def;
    }
    
    // argument parser
    public static boolean hasArg(String[] args, String argname) {
        for(String s : args) {
            if( s.toLowerCase().equals(argname) ) return true;
        }
        return false;
    }
    
    public static void main(String[] args) {
        logger.info("Starting AutoStepper by cociweb (See www.github.com/cociweb/AutoStepper for more goodies!)");
        if (shouldShowHelp(args)) {
            logger.info("Argument usage (all fields are optional):\n"
                    + "input=<file or dir> output=<songs dir> duration=<seconds to process, default: 90, -1 for full song> tap=<true/false> tapsync=<tap time offset, default: -0.11> hard=<true/false> updatesm=<true/false> downloadimages=<true/false, default: true> clearance=<seconds to skip from start/end in full song mode, default: 30>");
            return;
        }

        String outputDir = parseAndApplyArgs(args);
        String input = getArg(args, INPUT_ARG, ".");
        float duration = Float.parseFloat(getArg(args, DURATION_ARG, "-1"));

        AutoStepper autoStepper = new AutoStepper();
        minim = new Minim(autoStepper);
        if (stepDebug) minim.debugOn();
        else minim.debugOff(); // Removed to disable debug messages

        processInput(autoStepper, new File(input), duration, outputDir);
    }

    private static boolean shouldShowHelp(String[] args) {
        return hasArg(args, "help") || hasArg(args, "h") || hasArg(args, "?") || hasArg(args, "-help") || hasArg(args, "-?") || hasArg(args, "-h");
    }

    private static String parseAndApplyArgs(String[] args) {
        maxBpm = Float.parseFloat(getArg(args, MAX_BPM_ARG, "170f"));

        String outputDir = getArg(args, OUTPUT_ARG, ".");
        if (!outputDir.endsWith("/")) outputDir += "/";

        startSync = Float.parseFloat(getArg(args, START_SYNC_ARG, "0.0"));
        bpmSensitivity = Float.parseFloat(getArg(args, BPM_SENSITIVITY_ARG, "0.05"));
        useTapper = getArg(args, TAP_ARG, DEFAULT_FALSE).equals("true");
        tapSync = Double.parseDouble(getArg(args, TAP_SYNC_ARG, "-0.11"));
        hardMode = getArg(args, HARD_ARG, DEFAULT_FALSE).equals("true");
        updateSm = getArg(args, UPDATE_SM_ARG, DEFAULT_FALSE).equals("true");
        downloadImages = getArg(args, DOWNLOAD_IMAGES_ARG, "true").equals("true");
        stepDebug = getArg(args, DEBUG_ARG, DEFAULT_FALSE).equals("true");
        clearance = Float.parseFloat(getArg(args, CLEARANCE_ARG, "0.0"));
        return outputDir;
    }

    private static void processInput(AutoStepper autoStepper, File inputFile, float duration, String outputDir) {
        if (inputFile.isFile()) {
            autoStepper.analyzeUsingAudioRecordingStream(inputFile, duration, outputDir);
            return;
        }
        if (inputFile.isDirectory()) {
            processDirectory(autoStepper, inputFile, duration, outputDir);
            return;
        }
        if (isStepDebug()) logger.fine("Couldn't find any input files.");
    }

    private static void processDirectory(AutoStepper autoStepper, File inputDir, float duration, String outputDir) {
        logger.info("Processing directory: " + inputDir.getAbsolutePath());
        File[] allfiles = inputDir.listFiles();
        if (allfiles == null) return;
        for (File f : allfiles) {
            if (isSupportedAudioFile(f)) {
                autoStepper.analyzeUsingAudioRecordingStream(f, duration, outputDir);
            } else {
                if (isStepDebug()) logger.fine("Skipping unsupported file: " + f.getName());
            }
        }
    }

    private static boolean isSupportedAudioFile(File f) {
        if (!f.isFile()) return false;
        String extCheck = f.getName().toLowerCase();
        return extCheck.endsWith(".mp3") || extCheck.endsWith(".wav");
    }

    public static float getDifferenceAverage(TFloatArrayList arr) {
        float total = 0;
        for(int i=0;i<arr.size();i++) {
            total += arr.getQuick(i);
        }
        return total / arr.size();
    }

    static TFloatArrayList calculateDifferences(TFloatArrayList arr, float timeThreshold) {
        TFloatArrayList diff = new TFloatArrayList();
        int currentlyAt = 0;
        while(currentlyAt < arr.size() - 1) {
            float current = arr.getQuick(currentlyAt);
            for(int i=currentlyAt+1;i<arr.size();i++) {
                float next = arr.getQuick(i);
                if( next - current > timeThreshold ) {
                    break;
                }
                diff.add(next - current);
            }
            currentlyAt++;
        }
        return diff;
    }
    
    static float getMostCommon(TFloatArrayList arr, float threshold, boolean closestToInteger) {
        ArrayList<TFloatArrayList> values = groupByThreshold(arr, threshold);
        TFloatArrayList longestList = findLongestGroup(values);
        if (longestList == null) return -1f;
        if (longestList.size() == 1 && values.size() > 1) {
            return chooseSingletonValue(arr, closestToInteger);
        }
        return average(longestList);
    }

    private static ArrayList<TFloatArrayList> groupByThreshold(TFloatArrayList arr, float threshold) {
        ArrayList<TFloatArrayList> values = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            float val = arr.get(i);
            TFloatArrayList group = findGroup(values, val, threshold);
            if (group == null) {
                TFloatArrayList newList = new TFloatArrayList();
                newList.add(val);
                values.add(newList);
            } else {
                group.add(val);
            }
        }
        return values;
    }

    private static TFloatArrayList findGroup(ArrayList<TFloatArrayList> values, float val, float threshold) {
        for (int j = 0; j < values.size(); j++) {
            TFloatArrayList tal = values.get(j);
            for (int k = 0; k < tal.size(); k++) {
                float listValue = tal.get(k);
                if (Math.abs(listValue - val) < threshold) {
                    return tal;
                }
            }
        }
        return null;
    }

    private static TFloatArrayList findLongestGroup(ArrayList<TFloatArrayList> values) {
        int longest = 0;
        TFloatArrayList longestList = null;
        for (int i = 0; i < values.size(); i++) {
            TFloatArrayList check = values.get(i);
            if (check.size() > longest ||
                check.size() == longest && getDifferenceAverage(check) < getDifferenceAverage(longestList)) {
                longest = check.size();
                longestList = check;
            }
        }
        return longestList;
    }

    private static float chooseSingletonValue(TFloatArrayList arr, boolean closestToInteger) {
        if (closestToInteger) {
            return chooseClosestToInteger(arr);
        }
        return chooseSmallest(arr);
    }

    private static float chooseClosestToInteger(TFloatArrayList arr) {
        float closestIntDiff = 1f;
        float result = arr.getQuick(0);
        for (int i = 0; i < arr.size(); i++) {
            float diff = Math.abs(Math.round(arr.getQuick(i)) - arr.getQuick(i));
            if (diff < closestIntDiff) {
                closestIntDiff = diff;
                result = arr.getQuick(i);
            }
        }
        return result;
    }

    private static float chooseSmallest(TFloatArrayList arr) {
        float smallest = 99999f;
        for (int i = 0; i < arr.size(); i++) {
            if (arr.getQuick(i) < smallest) smallest = arr.getQuick(i);
        }
        return smallest;
    }

    private static float average(TFloatArrayList values) {
        float avg = 0f;
        for (int i = 0; i < values.size(); i++) {
            avg += values.get(i);
        }
        return avg / values.size();
    }
    
    public static float getBestOffset(float timePerBeat, TFloatArrayList times, float groupBy) {
        TFloatArrayList offsets = new TFloatArrayList();
        for(int i=0;i<times.size();i++) {
            offsets.add(times.getQuick(i) % timePerBeat);
        }
        return getMostCommon(offsets, groupBy, false);
    }
      
    public static float computeAutocorrBPM(TFloatArrayList onsetStrengths, float timePerSample) {
        if (onsetStrengths.size() < 10) return 0f;
        int maxLag = onsetStrengths.size() / 2;
        float[] autocorr = new float[maxLag];
        for(int lag=1; lag<maxLag; lag++) {
            float sum = 0f;
            for(int i=0; i<onsetStrengths.size()-lag; i++) {
                sum += onsetStrengths.get(i) * onsetStrengths.get(i+lag);
            }
            autocorr[lag] = sum / (onsetStrengths.size() - lag);
        }
        float minTime = 60f / getMaxBpm();
        float maxTime = 60f / getMinBpm();
        int minLag = Math.max(1, (int)(minTime / timePerSample));
        int maxLagRange = Math.min(maxLag - 1, (int)(maxTime / timePerSample));
        if (minLag >= maxLagRange) return 0f;
        float maxVal = 0f;
        int bestLag = 0;
        for(int lag=minLag; lag<=maxLagRange; lag++) {
            if (autocorr[lag] > maxVal) {
                maxVal = autocorr[lag];
                bestLag = lag;
            }
        }
        if (bestLag == 0 || maxVal == 0f) return 0f;
        float timePerBeat = bestLag * timePerSample;
        float bpm = 60f / timePerBeat;
        return Math.round(bpm);
    }
      
    public static void addCommonBPMs(TFloatArrayList common, TFloatArrayList times, float doubleSpeed, float timePerSample) {
        float period = getMostCommon(calculateDifferences(times, doubleSpeed), timePerSample, true);
        if ( period <= 0f || Float.isNaN(period) || Float.isInfinite(period) ) {
            return;
        }
        float commonBPM = 60f / period;
        if( commonBPM > maxBpm ) {
            common.add(commonBPM * 0.5f);
        } else if( commonBPM < minBpm / 2f ) {
            common.add(commonBPM * 4f);
        } else if( commonBPM < minBpm ) {
            common.add(commonBPM * 2f);
        } else common.add(commonBPM);
    }
    
    private static float tappedOffset;
    public static int getTappedBpm(String filename) {
        // Disable tapping for MP3 files to avoid loading large decoded audio into memory
        if (filename.toLowerCase().endsWith(".mp3")) {
            if (isStepDebug()) logger.fine("Tapping disabled for MP3 files. Using auto BPM detection instead.");
            return 0;
        }
        // now we load the whole song so we don't have to worry about streaming a variable mp3 with timing inaccuracies
        if (isStepDebug()) logger.fine("Loading whole song for tapping...");
        AudioSample fullSong = minim.loadSample(filename);
        if (fullSong == null) {
            if (isStepDebug()) logger.fine("Failed to load song for tapping. Using auto BPM detection instead.");
            return 0; // Will fall back to auto detection
        }
        if (logger.isLoggable(Level.INFO)) logger.info(String.format("%n********************************************************************%n%nPress [ENTER] to start song, then press [ENTER] to tap to the beat.%nIt will complete after 30 entries.%nDon't worry about hitting the first beat, just start anytime.%n%n********************************************************************"));
        TFloatArrayList positions = new TFloatArrayList();
        Scanner in = new Scanner(System.in);
        try {
            in.nextLine();
        } catch(Exception e) { /* Ignore exceptions during input operations */ }        
        // get the most accurate start time as possible
        long nano = System.nanoTime();
        fullSong.trigger();
        nano = (System.nanoTime() + nano) / 2;
        try {
            for(int i=0;i<30;i++) {
                in.nextLine();
                // get two playtime values & average them together for accuracy
                long now = System.nanoTime();
                // calculate the time difference
                // we note a consistent 0.11 second delay in input to song here
                float time = (float)((now - nano) / 1_000_000_000f + tapSync);
                positions.add(time);                
                if (isStepDebug()) logger.fine(String.format("#%d/30: %fs", positions.size(), time));
            }
        } catch(Exception e) { /* Ignore exceptions during input operations */ }
        fullSong.stop();
        fullSong.close();
        in.close();
        float avg = ((positions.getQuick(positions.size()-1) - positions.getQuick(0)) / (positions.size() - 1));
        int bpm = (int)Math.floor(60f / avg);
        float timePerBeat = 60f / bpm;
        tappedOffset = -getBestOffset(timePerBeat, positions, 0.1f);
        return bpm;
    }
    
    private static AudioAnalysisContext performBeatDetection(AudioRecordingStream stream, int fftSize, float songTime, boolean fullSongMode) {
        AudioAnalysisContext context = new AudioAnalysisContext();
        context.stream = stream;
        context.songTime = songTime;
        context.fullSongMode = fullSongMode;
        
        // create the fft/beatdetect objects we'll use for analysis
        BeatDetect manybd = createBeatDetect(stream, fftSize, BeatDetect.FREQ_ENERGY, bpmSensitivity);
        BeatDetect fewbd = createBeatDetect(stream, fftSize, BeatDetect.FREQ_ENERGY, 60f / maxBpm);
        BeatDetect manybde = createBeatDetect(stream, fftSize, BeatDetect.SOUND_ENERGY, bpmSensitivity);
        BeatDetect fewbde = createBeatDetect(stream, fftSize, BeatDetect.SOUND_ENERGY, 60f / maxBpm);

        context.manybd = manybd;
        context.fewbd = fewbd;
        context.manybde = manybde;
        context.fewbde = fewbde;
        
        FFT fft = new FFT( fftSize, stream.getFormat().getSampleRate() );
        context.fft = fft;

        // create the buffer we use for reading from the stream
        MultiChannelBuffer buffer = new MultiChannelBuffer(fftSize, stream.getFormat().getChannels());
        context.buffer = buffer;

        // figure out how many samples are in the stream so we can allocate the correct number of spectra
        int totalSamples = 0;
        if (fullSongMode) {
            totalSamples = (int)( songTime * stream.getFormat().getSampleRate() );
        }
        context.totalSamples = totalSamples;

        float timePerSample = fftSize / stream.getFormat().getSampleRate();
        context.timePerSample = timePerSample;

        int totalChunks = (totalSamples / fftSize) + 1;
        if (!fullSongMode) {
            // For limited duration, cap the chunks
            totalChunks = Math.min(totalChunks, (int)(songTime * stream.getFormat().getSampleRate() / fftSize) + 1);
        }
        context.totalChunks = totalChunks;

        if (isStepDebug() && logger.isLoggable(Level.FINE)) logger.fine(String.format("Processing %d chunks for %s seconds%s", totalChunks, songTime, (fullSongMode ? " (full song mode)" : "")));

        // Initialize beat time arrays
        TFloatArrayList[] manyTimes = new TFloatArrayList[4];
        TFloatArrayList[] fewTimes = new TFloatArrayList[4];
        initTimeArrays(manyTimes, fewTimes);
        context.manyTimes = manyTimes;
        context.fewTimes = fewTimes;

        TFloatArrayList midFftAmount = new TFloatArrayList();
        TFloatArrayList midFftMaxes = new TFloatArrayList();
        context.midFFTAmount = midFftAmount;
        context.midFFTMaxes = midFftMaxes;

        TFloatArrayList onsetStrengths = new TFloatArrayList();
        context.onsetStrengths = onsetStrengths;

        // Perform the beat detection loop
        context.actualSongTime = runBeatDetectionLoop(context, fftSize);
        context.previousEnergy = 0f;  // Initialize previousEnergy

    return context;
  }

  private static float runBeatDetectionLoop(AudioAnalysisContext context, int fftSize) {
      int consecutiveNoData = 0;
      float actualSongTime = 0f;

      for (int chunkIdx = 0; chunkIdx < context.totalChunks; ++chunkIdx) {
          int framesRead = context.stream.read(context.buffer);
          if (framesRead <= 0) {
              consecutiveNoData++;
              if (consecutiveNoData > 10) {
                  if (isStepDebug()) logger.fine("No data read for 10 consecutive chunks, stopping analysis");
                  break;
              }
              consecutiveNoData = 0;
          } else {
              consecutiveNoData = 0;
          }

          float time = chunkIdx * context.timePerSample;
          actualSongTime = Math.max(actualSongTime, time + context.timePerSample);
          float[] data = context.buffer.getChannel(0);
          analyzeChunk(context, data, fftSize, time);
      }

      return actualSongTime;
  }

  private static BeatDetect createBeatDetect(AudioRecordingStream stream, int fftSize, int mode, float sensitivity) {
      BeatDetect bd = new BeatDetect(mode, fftSize, stream.getFormat().getSampleRate());
      bd.setSensitivity(sensitivity);
      return bd;
  }

  private static void initTimeArrays(TFloatArrayList[] manyTimes, TFloatArrayList[] fewTimes) {
      for (int i = 0; i < fewTimes.length; i++) {
          if (fewTimes[i] == null) fewTimes[i] = new TFloatArrayList();
          if (manyTimes[i] == null) manyTimes[i] = new TFloatArrayList();
          fewTimes[i].clear();
          manyTimes[i].clear();
      }
  }

  private static void analyzeChunk(AudioAnalysisContext context, float[] data, int fftSize, float time) {
      // now analyze the left channel
      context.manybd.detect(data, time);
      context.fewbd.detect(data, time);
      context.manybde.detect(data, time);
      context.fewbde.detect(data, time);

      float[] fftData = new float[fftSize];
      System.arraycopy(data, 0, fftData, 0, fftSize);
      context.fft.forward(fftData);

      float midFFTAmountVal = 0f;
      float midFFTMax = 0f;
      for (int i = 0; i < fftSize; i++) {
          float fftVal = Math.abs(fftData[i]);
          midFFTAmountVal += fftVal;
          if (fftVal > midFFTMax) midFFTMax = fftVal;
      }
      context.midFFTAmount.add(midFFTAmountVal);
      context.midFFTMaxes.add(midFFTMax);

      // store the time of each beat
      if (context.manybd.isKick()) context.manyTimes[KICKS].add(time);
      if (context.manybd.isHat()) context.manyTimes[HAT].add(time);
      if (context.manybd.isSnare()) context.manyTimes[SNARE].add(time);
      if (context.manybde.isOnset()) context.manyTimes[ENERGY].add(time);
      if (context.fewbd.isKick()) context.fewTimes[KICKS].add(time);
      if (context.fewbd.isHat()) context.fewTimes[HAT].add(time);
      if (context.fewbd.isSnare()) context.fewTimes[SNARE].add(time);
      if (context.fewbde.isOnset()) context.fewTimes[ENERGY].add(time);
  }

  private static class BPMResult {
    float bpm;
    float timePerBeat;
    float startTime;
  }
    
    private static BPMResult calculateBPM(AudioAnalysisContext context, File filename, String outputDir, float autocorrBPM) {
        // calculate differences between percussive elements,
        // then find the most common differences among all
        // use this to calculate BPM
        TFloatArrayList common = new TFloatArrayList();
        float doubleSpeed = 60f / (maxBpm * 2f);
        for(int i=0;i<context.fewTimes.length;i++) {
            addCommonBPMs(common, context.fewTimes[i], doubleSpeed, context.timePerSample * 1.5f);
            addCommonBPMs(common, context.manyTimes[i], doubleSpeed, context.timePerSample * 1.5f);
        }
        BPMResult result = new BPMResult();
        BPMResult timing = resolveInitialTiming(filename, outputDir);

        if (timing.bpm == 0f) {
            timing = selectTimingFromCommon(common);
        }

        if (timing.bpm == 0f && autocorrBPM > 0) {
            timing = timingFromAutocorr(context, autocorrBPM);
        }
        if (isStepDebug() && logger.isLoggable(Level.FINE)) logger.fine(String.format("Time per beat: %s, BPM: %s", timing.timePerBeat, timing.bpm));
        if (isStepDebug() && logger.isLoggable(Level.FINE)) logger.fine(String.format("Start Time: %s", timing.startTime));

        result.bpm = timing.bpm;
        result.timePerBeat = timing.timePerBeat;
        result.startTime = timing.startTime;
        return result;
    }

    private static BPMResult selectTimingFromCommon(TFloatArrayList common) {
        BPMResult timing = new BPMResult();
        timing.bpm = 0f;
        timing.timePerBeat = 0f;
        timing.startTime = (clearance > 0f) ? clearance : 0f;

        if (common == null || common.isEmpty()) return timing;

        float bpm = getMostCommon(common, 1f, true);
        if (bpm <= 0f || Float.isNaN(bpm) || Float.isInfinite(bpm)) return timing;

        timing.bpm = bpm;
        timing.timePerBeat = 60f / bpm;
        return timing;
    }

    private static BPMResult resolveInitialTiming(File filename, String outputDir) {
        BPMResult timing = new BPMResult();
        timing.bpm = 0f;
        timing.timePerBeat = 0f;
        timing.startTime = (clearance > 0f) ? clearance : 0f;

        if (useTapper) {
            timing.bpm = getTappedBpm(filename.getAbsolutePath());
            timing.timePerBeat = 60f / timing.bpm;
            timing.startTime = tappedOffset;
            return timing;
        }

        if (updateSm) {
            BPMResult smTiming = readTimingFromSm(filename, outputDir);
            if (smTiming != null) return smTiming;
        }
        return timing;
    }

    private static BPMResult readTimingFromSm(File filename, String outputDir) {
        File smfile = SMGenerator.getSMFile(filename, outputDir);
        if (!smfile.exists()) {
            if (logger.isLoggable(Level.WARNING)) logger.warning(String.format("Couldn't find SM to update: %s", smfile.getAbsolutePath()));
            return null;
        }

        BPMResult timing = new BPMResult();
        timing.bpm = 0f;
        timing.startTime = (clearance > 0f) ? clearance : 0f;

        try (BufferedReader br = new BufferedReader(new FileReader(smfile))) {
            applySmTimingFromReader(br, timing);
            timing.timePerBeat = (timing.bpm == 0f) ? 0f : (60f / timing.bpm);
            return timing;
        } catch(Exception e) { /* Ignore exceptions during input operations */ }
        return null;
    }

    private static void applySmTimingFromReader(BufferedReader br, BPMResult timing) throws IOException {
        while (br.ready() && (timing.bpm == 0f || timing.startTime == 0f)) {
            String line = br.readLine();
            applySmTimingLine(line, timing);
        }
    }

    private static void applySmTimingLine(String line, BPMResult timing) {
        if (line.contains("#OFFSET:")) {
            timing.startTime = parseSmOffset(line);
            if (isStepDebug() && logger.isLoggable(Level.FINE)) logger.fine(String.format("StartTime from SM file: %s", timing.startTime));
        }
        if (line.contains("#BPMS:")) {
            timing.bpm = parseSmBpm(line);
            if (isStepDebug() && logger.isLoggable(Level.FINE)) logger.fine(String.format("BPM from SM file: %s", timing.bpm));
        }
    }

    private static float parseSmOffset(String line) {
        int off = line.indexOf("#OFFSET:") + 8;
        int end = line.indexOf(";", off);
        return Float.parseFloat(line.substring(off, end));
    }

    private static float parseSmBpm(String line) {
        int off = line.indexOf("#BPMS:");
        off = line.indexOf("=", off) + 1;
        int end = line.indexOf(";", off);
        return Float.parseFloat(line.substring(off, end));
    }

    private static BPMResult timingFromAutocorr(AudioAnalysisContext context, float autocorrBPM) {
        BPMResult timing = new BPMResult();
        timing.bpm = autocorrBPM;
        timing.timePerBeat = 60f / timing.bpm;
        timing.startTime = -estimateStartTime(context, timing.timePerBeat);
        return timing;
    }

    private static float estimateStartTime(AudioAnalysisContext context, float timePerBeat) {
        TFloatArrayList startTimes = new TFloatArrayList();
        for (int i = 0; i < context.fewTimes.length; i++) {
            startTimes.add(getBestOffset(timePerBeat, context.fewTimes[i], 0.01f));
            startTimes.add(getBestOffset(timePerBeat, context.manyTimes[i], 0.01f));
        }
        // give extra weight to fewKicks
        float kickStartTime = getBestOffset(timePerBeat, context.fewTimes[KICKS], 0.01f);
        startTimes.add(kickStartTime);
        startTimes.add(kickStartTime);
        return getMostCommon(startTimes, 0.02f, false);
    }

void analyzeUsingAudioRecordingStream(File filename, float seconds, String outputDir) {
    int fftSize = 512;
    
    boolean fullSongMode = (seconds == -1);
    logProcessingHeader(filename, seconds, fullSongMode);
    AudioRecordingStream stream = minim.loadFileStream(filename.getAbsolutePath(), fftSize, false);

    // tell it to "play" so we can read from it.
    stream.play();

    // figure out how many samples are in the stream so we can allocate the correct number of spectra
    // Try to get length from AudioFileFormat (standard Java Sound)
    float songTime = determineSongTimeSeconds(filename, fullSongMode);

    AudioAnalysisContext context = performBeatDetection(stream, fftSize, songTime, fullSongMode);
    
    // Update songTime to actual duration processed
    if (fullSongMode && context.actualSongTime > 0) {
        songTime = context.actualSongTime;
        if (isStepDebug() && logger.isLoggable(Level.FINE)) logger.fine(String.format("Actual song duration processed: %s%s", songTime, SECONDS_SUFFIX));
    }
    float autocorrBPM = computeAutocorrBPM(context.onsetStrengths, context.timePerSample);
    if (isStepDebug() && logger.isLoggable(Level.FINE)) logger.fine(String.format("Autocorr BPM: %s", autocorrBPM));
    
    // Calculate normalization values
    float largestAvg = computeLargestAvg(context);
    if (isStepDebug() && logger.isLoggable(Level.FINE)) logger.fine(String.format("Loudest midrange average to normalize to 1: %s", largestAvg));
    // start making the SM
    BPMResult bpmResult = calculateBPM(context, filename, outputDir, autocorrBPM);
    float bpm = bpmResult.bpm;
    float timePerBeat = bpmResult.timePerBeat;
    float startTime = bpmResult.startTime;
    
    // Use Effective songTime for full song mode, seconds for limited mode
    float effectiveTime = fullSongMode ? (songTime - 2*clearance) : seconds;
    
    // start making the SM
    StepGenerator stepGenerator = new StepGenerator();
    BufferedWriter smfile = SMGenerator.generateSmFromPath(bpm, startTime, filename, outputDir);
    
    if( hardMode && isStepDebug() ) logger.fine("Hard mode enabled! Extra steps for you! ;-)");
    
    SMGenerator.addNotes(smfile, SMGenerator.getBeginner(), stepGenerator.generateNotes(4, 8, 0, context.manyTimes, context.fewTimes, context.midFFTAmount, context.midFFTMaxes, context.timePerSample, timePerBeat*2, startTime, effectiveTime, false));
    SMGenerator.addNotes(smfile, SMGenerator.getEasy(), stepGenerator.generateNotes(4, 4, 1, context.manyTimes, context.fewTimes, context.midFFTAmount, context.midFFTMaxes, context.timePerSample, timePerBeat*2, startTime, effectiveTime, false));
    SMGenerator.addNotes(smfile, SMGenerator.getMedium(), stepGenerator.generateNotes(2, 2, 2, context.manyTimes, context.fewTimes, context.midFFTAmount, context.midFFTMaxes, context.timePerSample, timePerBeat*2, startTime, effectiveTime, false));
    SMGenerator.addNotes(smfile, SMGenerator.getHard(), stepGenerator.generateNotes(2, 1, 3, context.manyTimes, context.fewTimes, context.midFFTAmount, context.midFFTMaxes, context.timePerSample, timePerBeat*2, startTime, effectiveTime, false));
    SMGenerator.addNotes(smfile, SMGenerator.getChallenge(), stepGenerator.generateNotes(1, 1, 5, context.manyTimes, context.fewTimes, context.midFFTAmount, context.midFFTMaxes, context.timePerSample, timePerBeat, startTime, effectiveTime, true));
    SMGenerator.complete(smfile);
    
    logger.info("[--------- SUCCESS ----------]");
} // added closing brace for the analyzeUsingAudioRecordingStream method

private static void logProcessingHeader(File filename, float seconds, boolean fullSongMode) {
    if (fullSongMode) {
        if (logger.isLoggable(Level.INFO)) logger.info(String.format("%n[--- Processing FULL SONG %s ---]", filename.getName()));
    } else {
        if (logger.isLoggable(Level.INFO)) logger.info(String.format("%n[--- Processing %ss of %s ---]", seconds, filename.getName()));
    }
}

private static float determineSongTimeSeconds(File filename, boolean fullSongMode) {
    float songTime = -1f;
    try {
        if (fullSongMode) {
            songTime = readAudioFileDurationSeconds(filename);
        }
        if (songTime <= 0f) {
            songTime = 600f;  // Fallback to 10 minutes
            if (isStepDebug() && logger.isLoggable(Level.FINE)) logger.fine(String.format("Audio length unknown, estimating up to %s%s", songTime, SECONDS_SUFFIX));
        }
    } catch (IOException | UnsupportedAudioFileException e) {
        logger.warning(String.format("Could not determine audio length from file: %s", e.getMessage()));
        songTime = 600f;  // Fallback
    }
    return songTime;
}

private static float readAudioFileDurationSeconds(File filename) throws IOException, UnsupportedAudioFileException {
    if (isStepDebug() && logger.isLoggable(Level.FINE)) logger.fine(String.format("AudioFileFormat: %s", filename));
    AudioFileFormat aff = AudioSystem.getAudioFileFormat(filename);
    long frameLength = aff.getFrameLength();
    float frameRate = aff.getFormat().getFrameRate();
    if (frameLength > 0 && frameRate > 0) {
        float songTime = frameLength / frameRate;  // Length in seconds
        if (isStepDebug() && logger.isLoggable(Level.FINE)) logger.fine(String.format("Audio length from AudioFileFormat: %s%s", songTime, SECONDS_SUFFIX));
        return songTime;
    }
    // Check properties for duration (some MP3 SPIs populate this)
    Map<String, Object> props = aff.properties();
    if (props.containsKey(DURATION_ARG)) {
        long durationMicros = ((Long) props.get(DURATION_ARG)).longValue();
        float songTime = durationMicros / 1_000_000f;  // Convert microseconds to seconds
        if (isStepDebug() && logger.isLoggable(Level.FINE)) logger.fine(String.format("Audio length from properties: %s%s", songTime, SECONDS_SUFFIX));
        return songTime;
    }
    return -1f;
}

private static float computeLargestAvg(AudioAnalysisContext context) {
    float largestAvg = 0f;
    for (int i = 0; i < context.midFFTAmount.size(); i++) {
        largestAvg = Math.max(largestAvg, context.midFFTAmount.get(i));
    }
    return largestAvg;
}

  private static class AudioAnalysisContext {
    AudioRecordingStream stream;
    BeatDetect manybd;
    BeatDetect fewbd;
    BeatDetect manybde;
    BeatDetect fewbde;
    FFT fft;
    MultiChannelBuffer buffer;
    float songTime;
    int totalSamples;
    int totalChunks;
    float timePerSample;
    boolean fullSongMode;
    TFloatArrayList[] manyTimes;
    TFloatArrayList[] fewTimes;
    TFloatArrayList midFFTAmount;
    TFloatArrayList midFFTMaxes;
    TFloatArrayList onsetStrengths;
    float largestAvg;
    float largestMax;
    float actualSongTime;
    float previousEnergy;
    float bpm;
    float timePerBeat;
    float startTime;
    File filename;
    String outputDir;
  }
}