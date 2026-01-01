package autostepper;

import gnu.trove.list.array.TFloatArrayList;
import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Logger;


@SuppressWarnings("java:S00107")
public class StepGenerator {
    
    // Constants for better maintainability
    // Constants for better maintainability
    private static final int ARROW_COUNT = 4;
    private static final int MAX_SIMULTANEOUS_HOLDS = 2;
    private static final float MIN_JUMP_INTERVAL_WITH_MINES = 2.0f;
    private static final float MIN_JUMP_INTERVAL_NORMAL = 4.0f;
    private static final int MINE_SPAWN_CHANCE = 8;
    private static final int MINE_COUNTDOWN_RESET = 8;
    private static final String EMPTY_NOTE_LINE = "0000";
    private static final float SUSTAINED_FFT_LENGTH = 0.75f;
    private static final float SUSTAINED_FFT_THRESHOLD = 0.25f;
    private static final float SUSTAINED_FFT_MULTIPLIER = 0.45f;
    
    // Utility methods to reduce code duplication
    
    /**
     * Checks if an array element is empty and not being held
     * @param index The arrow index to check
     * @param noteLine The current note line
     * @return true if the position is available for a mine
     */
    private boolean isPositionAvailableForMine(int index, char[] noteLine) {
        return noteLine[index] == empty && holding[index] <= 0f;
    }
    
    /**
     * Clears all holds in the holding array
     */
    private void clearAllHolds() {
        for(int i = 0; i < ARROW_COUNT; i++) {
            holding[i] = 0f;
        }
    }
    
    /**
     * Initializes a note line array with empty notes
     * @return A new char array filled with empty notes
     */
    private char[] createEmptyNoteLine() {
        char[] noteLine = new char[ARROW_COUNT];
        for(int i = 0; i < ARROW_COUNT; i++) {
            noteLine[i] = empty;
        }
        return noteLine;
    }
    
    // https://github.com/stepmania/stepmania/wiki/Note-Types
    private char empty = '0';
    private char stop = '3';
    private char mine = 'M';
    
    private Random rand = new Random(System.nanoTime());
    
    private static final Logger logger = Logger.getLogger(StepGenerator.class.getName());
    
    private float[] holding = new float[ARROW_COUNT];
    private float lastJumpTime;
    private ArrayList<char[]> allNoteLines = new ArrayList<>();
    private int mineCount;
    private int commaSeperator;
    private int commaSeperatorReset;
    
    private int getHoldCount() {
        int ret = 0;
        for(int i = 0; i < ARROW_COUNT; i++) {
            if(holding[i] > 0f) ret++;
        }
        return ret;
    }
    
    private int getRandomHold() {
        int hc = getHoldCount();
        if(hc == 0) return -1;
        int pickHold = rand.nextInt(hc);
        for(int i=0; i<ARROW_COUNT; i++) {
            if( holding[i] > 0f ) {
                if( pickHold == 0 ) return i;
                pickHold--;
            }
        }
        return -1;
    }
    
    // make a note line, with lots of checks, balances & filtering
    private char[] getHoldStops(int currentHoldCount, int holds) {
        char[] holdstops = createEmptyNoteLine();
        if( currentHoldCount > 0 ) {
            processNegativeHolds(holds, holdstops, currentHoldCount);
            updateExistingHolds(holdstops, currentHoldCount);
        }        
        // add new holds if needed
        if( holds > 0 ) {
            // Find an empty arrow position for the new hold
            ArrayList<Integer> availablePositions = new ArrayList<>();
            for(int i = 0; i < ARROW_COUNT; i++) {
                if(holding[i] <= 0f && holdstops[i] == empty) {
                    availablePositions.add(i);
                }
            }
            if(!availablePositions.isEmpty()) {
                int idx = rand.nextInt(availablePositions.size());
                int position = availablePositions.get(idx);
                holdstops[position] = '2';
                holding[position] = Math.max(1.0f, (float) holds);  // Hold duration (minimum 1 frame)
            }
        }
        return holdstops;
    }
    
    private int processNegativeHolds(int holds, char[] holdstops, int currentHoldCount) {
        while( holds < 0 ) {
            int index = getRandomHold();
            if( index == -1 ) {
                holds = 0;
                currentHoldCount = 0;
            } else {
                holding[index] = 0f;
                holdstops[index] = stop;
                holds++; 
                currentHoldCount--;
            }
        }
        return currentHoldCount;
    }
    
    private int updateExistingHolds(char[] holdstops, int currentHoldCount) {
        for(int i=0; i<ARROW_COUNT; i++) {
            if( holding[i] > 0f ) {
                holding[i] -= 1f;
                if( holding[i] <= 0f ) {
                    holding[i] = 0f;
                    holdstops[i] = stop;
                    currentHoldCount--;
                }
            } 
        }
        return currentHoldCount;
    }
    
    /**
     * Gets a note line by index, returns empty line if out of bounds
     * @param i The index of the note line
     * @return String representation of the note line
     */
    private String getNoteLineIndex(int i) {
        if( i < 0 || i >= allNoteLines.size() ) return EMPTY_NOTE_LINE;
        return String.valueOf(allNoteLines.get(i));
    }
    
    private String getLastNoteLine() {
        return getNoteLineIndex(allNoteLines.size()-1);
    }
    
    private void makeNoteLine(String lastLine, float time, boolean[] placeStep, int holds, boolean mines) {
        int steps = countSteps(placeStep);
        if( steps == 0 ) {
            char[] ret = getHoldStops(getHoldCount(), holds);
            allNoteLines.add(ret);
            return;
        }
        
        steps = reduceJumpsIfNeeded(placeStep, steps, time, mines);
        if( steps >= 2 ) {
            lastJumpTime = time;
        }
        
        holds = adjustHoldsAndSteps(placeStep, holds, steps);
        char[] noteLine = getHoldStops(getHoldCount(), holds);
        
        // Place the actual step arrows ('1') based on placeStep array
        for(int i = 0; i < ARROW_COUNT; i++) {
            if(placeStep[i] && noteLine[i] == empty) {
                noteLine[i] = '1';
            }
        }
        
        if( mines ) {
            addMines(noteLine);
        }
        
        String completeLine = String.valueOf(noteLine);
        if( shouldRetryLine(completeLine, lastLine) ) {
            noteLine = retryWithRandomPlacement(placeStep, holds);
        }
        
        allNoteLines.add(noteLine);
    }
    
    private int countSteps(boolean[] placeStep) {
        int steps = 0;
        for(boolean b : placeStep) if(b) steps++;
        return steps;
    }
    
    private int reduceJumpsIfNeeded(boolean[] placeStep, int steps, float time, boolean mines) {
        if( steps > 1 && time - lastJumpTime < (mines ? MIN_JUMP_INTERVAL_WITH_MINES : MIN_JUMP_INTERVAL_NORMAL) ) {
            // Create new placement with single step
            for(int i=0;i<4;i++) {
                if(placeStep[i]) {
                    // Clear all other steps except this one
                    for(int j=0;j<4;j++) {
                        placeStep[j] = (j == i);
                    }
                    break;
                }
            }
            return 1;
        }
        return steps;
    }
    
    private int adjustHoldsAndSteps(boolean[] placeStep, int holds, int steps) {
        int currentHoldCount = getHoldCount(); 
        if( holds + currentHoldCount > MAX_SIMULTANEOUS_HOLDS ) holds = MAX_SIMULTANEOUS_HOLDS - currentHoldCount;
        if( steps + currentHoldCount > MAX_SIMULTANEOUS_HOLDS ) {
            int toReduce = steps + currentHoldCount - MAX_SIMULTANEOUS_HOLDS;
            for(int i=3;i>=0;i--) {
                if(toReduce > 0 && placeStep[i]) {
                    placeStep[i] = false;
                    toReduce--;
                    steps--;
                }
            }
        }
        return holds;
    }
    
    /**
     * Adds mines to the note line based on random chance
     * @param noteLine The current note line to potentially add mines to
     * @return The modified note line with mines added
     */
    private char[] addMines(char[] noteLine) {
        mineCount--;
        if( mineCount <= 0 ) {
            mineCount = rand.nextInt(MINE_COUNTDOWN_RESET);
            for(int i = 0; i < ARROW_COUNT; i++) {
                if( rand.nextInt(MINE_SPAWN_CHANCE) == 0 && isPositionAvailableForMine(i, noteLine) ) {
                    noteLine[i] = mine;
                }
            }
        }
        return noteLine;
    }
    
    /**
     * Determines if a note line should be retried due to duplication
     * @param completeLine The current complete note line
     * @param lastLine The previous note line
     * @return true if the line should be regenerated
     */
    private boolean shouldRetryLine(String completeLine, String lastLine) {
        return completeLine.equals(lastLine) && !completeLine.equals(EMPTY_NOTE_LINE);
    }
    
    private char[] retryWithRandomPlacement(boolean[] placeStep, int holds) {
        ArrayList<Integer> avail = new ArrayList<>();
        for(int i=0; i<ARROW_COUNT; i++) if(placeStep[i] && holding[i] <= 0f) avail.add(i);
        if(!avail.isEmpty()) {
            int idx = rand.nextInt(avail.size());
            int alt = avail.get(idx);
            boolean[] altPlace = new boolean[ARROW_COUNT];
            altPlace[alt] = true;
            int currentHoldCount = getHoldCount();
            if( holds + currentHoldCount > MAX_SIMULTANEOUS_HOLDS ) holds = MAX_SIMULTANEOUS_HOLDS - currentHoldCount;
            char[] noteLine = getHoldStops(currentHoldCount, holds);
            String completeLine = String.valueOf(noteLine);
            if( completeLine.equals(getLastNoteLine()) && !completeLine.equals(EMPTY_NOTE_LINE) ) {
                return noteLine;
            }
        }
        return getHoldStops(getHoldCount(), holds);
    }
    
    /**
     * Checks if a time is near any time in the list within threshold
     * Optimized with early exit when times exceed threshold
     * @param time The time to check
     * @param timelist List of times to compare against
     * @param threshold Maximum distance to consider "near"
     * @return true if time is near any time in the list
     */
    private boolean isNearATime(float time, TFloatArrayList timelist, float threshold) {
        for(int i=0; i<timelist.size(); i++) {
            float checktime = timelist.get(i);
            if( Math.abs(checktime - time) <= threshold ) return true;
            // Early exit optimization - list is sorted by time
            if( checktime > time + threshold ) return false;
        }
        return false;
    }
    
    /**
     * Gets FFT value at a specific time with bounds checking
     * @param time The time in seconds
     * @param fftMaxes Array of FFT maximum values
     * @param timePerFft Time duration per FFT sample
     * @return FFT value at the time, or 0 if out of bounds
     */
    private float getFft(float time, TFloatArrayList fftMaxes, float timePerFft) {
        int index = (int)Math.floor(time / timePerFft);
        if( index >= 0 && index < fftMaxes.size() ) {
            return fftMaxes.get(index);
        }
        return 0f;
    }
    
    private static class SustainedFftConfig {
        public final float startTime;
        public final float len;
        public final float granularity;
        public final float timePerFft;
        public final TFloatArrayList fftMaxes;
        public final TFloatArrayList fftAvg;
        public final float aboveAvg;
        public final float averageMultiplier;
        
        @SuppressWarnings("all")
        public SustainedFftConfig(float startTime, float len, float granularity, float timePerFft,
                               TFloatArrayList fftMaxes, TFloatArrayList fftAvg, float aboveAvg, float averageMultiplier) {
            this.startTime = startTime;
            this.len = len;
            this.granularity = granularity;
            this.timePerFft = timePerFft;
            this.fftMaxes = fftMaxes;
            this.fftAvg = fftAvg;
            this.aboveAvg = aboveAvg;
            this.averageMultiplier = averageMultiplier;
        }
    }
    
    private boolean sustainedFft(SustainedFftConfig config) {
        int endIndex = (int)Math.floor((config.startTime + config.len) / config.timePerFft);
        if( endIndex >= config.fftMaxes.size() ) return false;
        int wiggleRoom = Math.round(0.1f * config.len / config.timePerFft);
        int startIndex = (int)Math.floor(config.startTime / config.timePerFft);
        int pastGranu = (int)Math.floor((config.startTime + config.granularity) / config.timePerFft);
        boolean startThresholdReached = false;
        for(int i=startIndex;i<=endIndex;i++) {
            float amt = config.fftMaxes.getQuick(i);
            float avg = config.fftAvg.getQuick(i) * config.averageMultiplier;
            if( i <= pastGranu ) {
                startThresholdReached |= amt >= avg + config.aboveAvg;
            } else {
                if( !startThresholdReached ) return false;
                if( amt < avg ) {
                    wiggleRoom--;
                    if( wiggleRoom <= 0 ) return false;
                }
            }
        }
        return true;
    }
    
    /**
     * Checks if FFT values are sustained above threshold for a duration
     * Convenience method that creates config and delegates to main implementation
     */
    @SuppressWarnings("all")
    private boolean sustainedFft(float startTime, float len, float granularity, float timePerFft, TFloatArrayList fftMaxes, TFloatArrayList fftAvg, float aboveAvg, float averageMultiplier) {
        SustainedFftConfig config = new SustainedFftConfig(startTime, len, granularity, timePerFft, fftMaxes, fftAvg, aboveAvg, averageMultiplier);
        return sustainedFft(config);
    }
    
    public static class NoteGenerationConfig {
        public final int stepGranularity;
        public final int skipChance;
        public final int holdDensity;
        public final TFloatArrayList[] manyTimes;
        public final TFloatArrayList[] fewTimes;
        public final TFloatArrayList fftAverages;
        public final TFloatArrayList fftMaxes;
        public final float timePerFft;
        public final float timePerBeat;
        public final float timeOffset;
        public final float totalTime;
        public final boolean allowMines;
        public final float energySensitivity; // Multiplier for energy detection (1.0 = normal, <1.0 = less sensitive)
        public final String difficultyLevel; // Difficulty level name for logging
        public final int targetActionCount; // Target number of total actions for quality mode
        
        @SuppressWarnings("all")
        public NoteGenerationConfig(int stepGranularity, int skipChance, int holdDensity,
                                  TFloatArrayList[] manyTimes, TFloatArrayList[] fewTimes, 
                                  TFloatArrayList fftAverages, TFloatArrayList fftMaxes, float timePerFft,
                                  float timePerBeat, float timeOffset, float totalTime,
                                  boolean allowMines) {
            this(stepGranularity, skipChance, holdDensity, manyTimes, fewTimes, fftAverages,
                 fftMaxes, timePerFft, timePerBeat, timeOffset, totalTime, allowMines, 1.0f, "Unknown", -1);
        }
        
        @SuppressWarnings("all")
        public NoteGenerationConfig(int stepGranularity, int skipChance, int holdDensity,
                                  TFloatArrayList[] manyTimes, TFloatArrayList[] fewTimes, 
                                  TFloatArrayList fftAverages, TFloatArrayList fftMaxes, float timePerFft,
                                  float timePerBeat, float timeOffset, float totalTime,
                                  boolean allowMines, float energySensitivity) {
            this(stepGranularity, skipChance, holdDensity, manyTimes, fewTimes, fftAverages,
                 fftMaxes, timePerFft, timePerBeat, timeOffset, totalTime, allowMines, energySensitivity, "Unknown", -1);
        }
        
        @SuppressWarnings("all")
        public NoteGenerationConfig(int stepGranularity, int skipChance, int holdDensity,
                                  TFloatArrayList[] manyTimes, TFloatArrayList[] fewTimes, 
                                  TFloatArrayList fftAverages, TFloatArrayList fftMaxes, float timePerFft,
                                  float timePerBeat, float timeOffset, float totalTime,
                                  boolean allowMines, float energySensitivity, String difficultyLevel, int targetActionCount) {
            this.stepGranularity = stepGranularity;
            this.skipChance = skipChance;
            this.holdDensity = holdDensity;
            this.manyTimes = manyTimes;
            this.fewTimes = fewTimes;
            this.fftAverages = fftAverages;
            this.fftMaxes = fftMaxes;
            this.timePerFft = timePerFft;
            this.timePerBeat = timePerBeat;
            this.timeOffset = timeOffset;
            this.totalTime = totalTime;
            this.allowMines = allowMines;
            this.energySensitivity = energySensitivity;
            this.difficultyLevel = difficultyLevel;
            this.targetActionCount = targetActionCount;
        }
    }
    
    public String generateNotes(NoteGenerationConfig config) {
        this.currentConfig = config;
        resetState(config.stepGranularity);
        return generateNoteLines(config);
    }
    
    @SuppressWarnings("all")
    public String generateNotes(int stepGranularity, int skipChance, int holdDensity,
                              TFloatArrayList[] manyTimes, TFloatArrayList[] fewTimes, 
                              TFloatArrayList fftAverages, TFloatArrayList fftMaxes, float timePerFft,
                              float timePerBeat, float timeOffset, float totalTime,
                              boolean allowMines) {
        // Determine difficulty level from parameters
        String difficultyLevel = determineDifficultyLevel(stepGranularity, skipChance, holdDensity, allowMines);
        
        NoteGenerationConfig config = new NoteGenerationConfig(
            stepGranularity, skipChance, holdDensity, manyTimes, fewTimes, fftAverages, 
            fftMaxes, timePerFft, timePerBeat, timeOffset, totalTime, allowMines, 1.0f, difficultyLevel, -1);
        
        // Apply quality normalization if not in hard mode
        if (!AutoStepper.isHardMode()) {
            config = applyQualityNormalization(config, timePerBeat);
        }
        
        return generateNotes(config);
    }
    
    /**
     * Determines the difficulty level name based on generation parameters
     */
    private String determineDifficultyLevel(int stepGranularity, int skipChance, int holdDensity, boolean allowMines) {
        if (stepGranularity == 2 && skipChance == 8 && holdDensity == 0) {
            return "Beginner";
        } else if (stepGranularity == 2 && skipChance == 4 && holdDensity == 1) {
            return "Easy";
        } else if (stepGranularity == 2 && skipChance == 2 && holdDensity == 2) {
            return "Medium";
        } else if (stepGranularity == 2 && skipChance == 1 && holdDensity == 3) {
            return "Hard";
        } else if (stepGranularity == 1 && skipChance == 1 && holdDensity == 5 && allowMines) {
            return "Challenge";
        }
        return "Custom";
    }
    
    /**
     * Applies quality-based normalization to calibrate step density.
     * Target: 60 actions per 100 seconds for beginner mode at 100 BPM.
     * Target: 200 actions per 100 seconds for challenge mode.
     * Scales with BPM and adjusts energy thresholds accordingly.
     */
    private NoteGenerationConfig applyQualityNormalization(NoteGenerationConfig config, float timePerBeat) {
        // Note: timePerBeat is already doubled for most difficulties (timePerBeat*2)
        // So we need to account for this in BPM calculation
        float bpm = 60.0f / timePerBeat;
        float songDuration = config.totalTime - config.timeOffset;
        
        // Calculate target actions per 100 seconds based on difficulty level
        float targetActionsPer100s;
        if (config.stepGranularity == 2 && config.skipChance == 8) {
            targetActionsPer100s = 60.0f * (bpm / 100.0f);  // Beginner
        } else if (config.stepGranularity == 2 && config.skipChance == 4) {
            targetActionsPer100s = 90.0f * (bpm / 100.0f);  // Easy
        } else if (config.stepGranularity == 2 && config.skipChance == 2) {
            targetActionsPer100s = 120.0f * (bpm / 100.0f); // Medium
        } else if (config.stepGranularity == 2 && config.skipChance == 1) {
            targetActionsPer100s = 150.0f * (bpm / 100.0f); // Hard
        } else if (config.stepGranularity == 4 && config.skipChance == 1) {
            targetActionsPer100s = 200.0f * (bpm / 100.0f); // Challenge
        } else {
            targetActionsPer100s = 150.0f * (bpm / 100.0f); // Default
        }
        
        float targetTotalActions = (targetActionsPer100s / 100.0f) * songDuration;
        
        // Calculate energy sensitivity based on target density
        // Lower values = fewer actions detected
        float energySensitivity = Math.min(1.0f, targetTotalActions / (songDuration * 2.0f));
        energySensitivity = Math.max(0.1f, energySensitivity);
        
        if (logger.isLoggable(java.util.logging.Level.INFO)) {
            logger.info(String.format("Quality: %s - Target %.0f actions (%.1f/100s) for %.1fs @ %.1f BPM",
                config.difficultyLevel, targetTotalActions, targetActionsPer100s, songDuration, bpm));
        }
        
        return new NoteGenerationConfig(
            config.stepGranularity,
            config.skipChance,
            config.holdDensity,
            config.manyTimes,
            config.fewTimes,
            config.fftAverages,
            config.fftMaxes,
            config.timePerFft,
            config.timePerBeat,
            config.timeOffset,
            config.totalTime,
            config.allowMines,
            energySensitivity,
            config.difficultyLevel,
            (int)targetTotalActions
        );
    }
    
    /**
     * Resets the generator state for a new note generation session
     * @param stepGranularity The granularity for step placement
     */
    private void resetState(int stepGranularity) {
        allNoteLines.clear();
        lastJumpTime = -10f;
        clearAllHolds();
        commaSeperatorReset = 4 * stepGranularity;
    }
    
    private String generateNoteLines(NoteGenerationConfig config) {
        int timeIndex = 0;
        int actionCount = 0;
        float t = config.timeOffset;
        float timeGranularity = config.timePerBeat / config.stepGranularity;
        
        while( t < config.totalTime ) {
            StepDecision decision = analyzeStepTiming(t, config, timeIndex);
            
            // Apply quality control - stop generating if we've reached target
            if (config.targetActionCount > 0 && actionCount >= config.targetActionCount) {
                decision.steps = 0;
                decision.holds = 0;
            }
            
            if( AutoStepper.isStepDebug() ) {
                boolean[] debugSteps = new boolean[ARROW_COUNT];
                debugSteps[0] = (timeIndex % 2 == 0);
                makeNoteLine(getLastNoteLine(), t, debugSteps, -2, config.allowMines);
            } else {
                boolean[] stepArray = new boolean[ARROW_COUNT];
                if( decision.steps > 0 ) {
                    stepArray[0] = true; // Place step on first arrow
                    actionCount++; // Count taps
                }
                if( decision.holds > 0 ) {
                    actionCount++; // Count holds
                }
                makeNoteLine(getLastNoteLine(), t, stepArray, decision.holds, config.allowMines);
            }
            timeIndex++;
            t += timeGranularity;
        }
        return formatOutput();
    }
    
    /**
     * Analyzes timing to determine step placement decisions
     * @param t Current time in seconds
     * @param config Note generation configuration
     * @param timeIndex Current time index in the sequence
     * @return StepDecision containing step and hold counts
     */
    private StepDecision analyzeStepTiming(float t, NoteGenerationConfig config, int timeIndex) {
        StepDecision decision = new StepDecision();
        if( t > 0f ) {
            float fftmax = getFft(t, config.fftMaxes, config.timePerFft);
            // Use normal thresholds for sustained energy detection (for holds)
            boolean sustained = sustainedFft(t, SUSTAINED_FFT_LENGTH, config.timePerBeat / config.stepGranularity, 
                                           config.timePerFft, config.fftMaxes, config.fftAverages, SUSTAINED_FFT_THRESHOLD, SUSTAINED_FFT_MULTIPLIER);
            boolean nearKick = isNearATime(t, config.fewTimes[AutoStepper.KICKS], config.timePerBeat / config.stepGranularity);
            boolean nearSnare = isNearATime(t, config.fewTimes[AutoStepper.SNARE], config.timePerBeat / config.stepGranularity);
            boolean nearEnergy = isNearATime(t, config.fewTimes[AutoStepper.ENERGY], config.timePerBeat / config.stepGranularity);
            
            // Original simple logic - works correctly
            decision.steps = sustained || nearKick || nearSnare || nearEnergy ? 1 : 0;
            if( sustained ) {
                decision.holds = config.holdDensity * 2 + (nearEnergy ? config.holdDensity : 0);
            } else if( fftmax < 0.5f ) {
                decision.holds = fftmax < 0.25f ? -2 : -1;
            }
            
            decision = checkJumpConditions(decision, nearKick, nearSnare, nearEnergy, timeIndex);
            decision = applySkipLogic(decision, config, timeIndex, t);
        }
        return decision;
    }
    
    private StepDecision checkJumpConditions(StepDecision decision, boolean nearKick, boolean nearSnare, boolean nearEnergy, int timeIndex) {
        String lastLine = getLastNoteLine();
        if( nearKick && (nearSnare || nearEnergy) && timeIndex % 2 == 0 &&
            decision.steps > 0 && !lastLine.contains("1") && !lastLine.contains("2") && !lastLine.contains("3") ) {
            decision.steps = 2;
        }
        return decision;
    }
    
    private StepDecision applySkipLogic(StepDecision decision, NoteGenerationConfig config, int timeIndex, float t) {
        // Apply skip logic to control difficulty
        // Higher skipChance = more skipping = easier (fewer actions)
        boolean shouldSkip = false;
        
        // Apply skipChance on ALL time indices, not just odd ones
        if (config.skipChance > 1 && decision.steps > 0) {
            shouldSkip = rand.nextInt(config.skipChance) > 0;
        }
        
        // Also skip if there are existing holds (to avoid conflicts)
        if (getHoldCount() > 0) {
            shouldSkip = true;
        }
        
        // Also skip if too close to last jump
        if (t - lastJumpTime < config.timePerBeat) {
            shouldSkip = true;
        }
        
        if (shouldSkip) {
            decision.steps = 0;
            if (decision.holds > 0) decision.holds = 0;
        }
        
        return decision;
    }
    
    private static class StepDecision {
        int steps = 0;
        int holds = 0;
    }
    
    /**
     * Formats the generated notes into final output string
     * Optimized with capacity hint to reduce memory allocations
     * @return Formatted note string ready for SM file
     */
    private String formatOutput() {
        // Pre-allocate StringBuilder capacity (estimate: 6 chars per line)
        int estimatedCapacity = allNoteLines.size() * 6;
        StringBuilder allNotes = new StringBuilder(estimatedCapacity);
        buildNoteLines(allNotes);
        fillEmptyLines(allNotes);
        String[] lines = allNotes.toString().split("\n");
        NoteStatistics stats = calculateStatistics(lines);
        logStatistics(stats);
        return allNotes.toString();
    }
    
    /**
     * Builds note lines into StringBuilder with proper formatting
     * @param allNotes The StringBuilder to append note lines to
     */
    private void buildNoteLines(StringBuilder allNotes) {
        commaSeperator = commaSeperatorReset;
        for(int i=0; i<allNoteLines.size(); i++) {
            allNotes.append(getNoteLineIndex(i)).append("\n");
            commaSeperator--;
            if( commaSeperator == 0 ) {
                allNotes.append(",\n");
                commaSeperator = commaSeperatorReset;
            }
        }
    }
    
    /**
     * Fills remaining lines with stop notes to complete the measure
     * @param allNotes The StringBuilder to append stop notes to
     */
    private void fillEmptyLines(StringBuilder allNotes) {
        while( commaSeperator > 0 ) {
            allNotes.append("3333");
            commaSeperator--;
            if( commaSeperator > 0 ) allNotes.append("\n");
        }
    }
    
    /**
     * Calculates statistics about the generated notes
     * @param lines Array of note lines to analyze
     * @return NoteStatistics object with counts of different note types
     */
    private NoteStatistics calculateStatistics(String[] lines) {
        NoteStatistics stats = new NoteStatistics();
        
        // Build the complete output string from lines for hold/mine counting
        StringBuilder allNotesStr = new StringBuilder();
        for (String line : lines) {
            allNotesStr.append(line);
            
            int ones = line.length() - line.replace("1", "").length();
            int twos = line.length() - line.replace("2", "").length();
            if ((ones == 1 || twos == 1) && (ones + twos == 1)) stats.taps++;
            else if ((ones == 2 || twos == 2) && (ones + twos == 2)) stats.jumps++;
            else if (ones == 3) stats.hands++;
            else if (ones >= 4) stats.quads++;
        }
        
        String allNotes = allNotesStr.toString();
        stats.holdCount = allNotes.length() - allNotes.replace("2", "").length();
        stats.mineCount = allNotes.length() - allNotes.replace("M", "").length();
        return stats;
    }
    
    private NoteGenerationConfig currentConfig;
    
    /**
     * Logs statistics about the generated notes
     * @param stats The statistics to log
     */
    private void logStatistics(NoteStatistics stats) {
        if (logger.isLoggable(java.util.logging.Level.INFO)) {
            String level = currentConfig != null ? currentConfig.difficultyLevel : "Unknown";
            logger.info(String.format("Level: %s, Taps: %d, Jumps: %d, Hands: %d, Quads: %d, Holds: %d, Mines: %d", 
                level, stats.taps + stats.jumps, stats.jumps, stats.hands, stats.quads, stats.holdCount, stats.mineCount));
        }
    }
    
    private static class NoteStatistics {
        int taps = 0;
        int jumps = 0;
        int hands = 0;
        int quads = 0;
        int holdCount = 0;
        int mineCount = 0;
    }
    
}
