package autostepper;

import gnu.trove.list.array.TFloatArrayList;
import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Logger;


@SuppressWarnings("java:S00107")
public class StepGenerator {
    
    // https://github.com/stepmania/stepmania/wiki/Note-Types
    private char empty = '0';
    private char stop = '3';
    
    private Random rand = new Random();
    
    private static final Logger logger = Logger.getLogger(StepGenerator.class.getName());
    
    private float[] holding = new float[4];
    private float lastJumpTime;
    private ArrayList<char[]> allNoteLines = new ArrayList<>();
    private int mineCount;
    
    private int getHoldCount() {
        int ret = 0;
        if( holding[0] > 0f ) ret++;
        if( holding[1] > 0f ) ret++;
        if( holding[2] > 0f ) ret++;
        if( holding[3] > 0f ) ret++;
        return ret;
    }
    
    private int getRandomHold() {
        int hc = getHoldCount();
        if(hc == 0) return -1;
        int pickHold = rand.nextInt(hc);
        for(int i=0;i<4;i++) {
            if( holding[i] > 0f ) {
                if( pickHold == 0 ) return i;
                pickHold--;
            }
        }
        return -1;
    }
    
    // make a note line, with lots of checks, balances & filtering
    private char[] getHoldStops(int currentHoldCount, int holds) {
        char[] holdstops = new char[4];
        holdstops[0] = empty;
        holdstops[1] = empty;
        holdstops[2] = empty;
        holdstops[3] = empty;
        if( currentHoldCount > 0 ) {
            processNegativeHolds(holds, holdstops, currentHoldCount);
            updateExistingHolds(holdstops, currentHoldCount);
        }        
        // add new holds if needed
        if( holds > 0 ) {
            int index = getRandomHold();
            if( index != -1 ) {
                holdstops[index] = '2';
                holding[index] = 1f;
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
        for(int i=0;i<4;i++) {
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
    
    private String getNoteLineIndex(int i) {
        if( i < 0 || i >= allNoteLines.size() ) return "" + empty + empty + empty + empty;
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
        
        holds = adjustHoldsAndSteps(holds, steps);
        char[] noteLine = getHoldStops(getHoldCount(), holds);
        
        if( mines ) {
            noteLine = addMines(noteLine);
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
        if( steps > 1 && time - lastJumpTime < (mines ? 2f : 4f) ) {
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
    
    private int adjustHoldsAndSteps(int holds, int steps) {
        int currentHoldCount = getHoldCount(); 
        if( holds + currentHoldCount > 2 ) holds = 2 - currentHoldCount;
        if( steps + currentHoldCount > 2 ) {
            int toReduce = steps + currentHoldCount - 2;
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
    
    private char[] addMines(char[] noteLine) {
        mineCount--;
        if( mineCount <= 0 ) {
            mineCount = rand.nextInt(8);
            if( rand.nextInt(8) == 0 && noteLine[0] == EMPTY && holding[0] <= 0f ) noteLine[0] = MINE;
            if( rand.nextInt(8) == 0 && noteLine[1] == EMPTY && holding[1] <= 0f ) noteLine[1] = MINE;
            if( rand.nextInt(8) == 0 && noteLine[2] == EMPTY && holding[2] <= 0f ) noteLine[2] = MINE;
            if( rand.nextInt(8) == 0 && noteLine[3] == EMPTY && holding[3] <= 0f ) noteLine[3] = MINE;
        }
        return noteLine;
    }
    
    private boolean shouldRetryLine(String completeLine, String lastLine) {
        return completeLine.equals(lastLine) && !completeLine.equals("0000");
    }
    
    private char[] retryWithRandomPlacement(boolean[] placeStep, int holds) {
        ArrayList<Integer> avail = new ArrayList<>();
        for(int i=0;i<4;i++) if(placeStep[i] && holding[i] <= 0f) avail.add(i);
        if(!avail.isEmpty()) {
            int idx = rand.nextInt(avail.size());
            int alt = avail.get(idx);
            boolean[] altPlace = new boolean[4];
            altPlace[alt] = true;
            int currentHoldCount = getHoldCount();
            if( holds + currentHoldCount > 2 ) holds = 2 - currentHoldCount;
            char[] noteLine = getHoldStops(currentHoldCount, holds);
            String completeLine = String.valueOf(noteLine);
            if( completeLine.equals(getLastNoteLine()) && !completeLine.equals("0000") ) {
                return noteLine;
            }
        }
        return getHoldStops(getHoldCount(), holds);
    }
    
    private boolean isNearATime(float time, TFloatArrayList timelist, float threshold) {
        for(int i=0;i<timelist.size();i++) {
            float checktime = timelist.get(i);
            if( Math.abs(checktime - time) <= threshold ) return true;
            if( checktime > time + threshold ) return false;
        }
        return false;
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
    
    @SuppressWarnings("all")
    private boolean sustainedFft(float startTime, float len, float granularity, float timePerFft, TFloatArrayList fftMaxes, TFloatArrayList fftAvg, float aboveAvg, float averageMultiplier) {
        SustainedFftConfig config = new SustainedFftConfig(startTime, len, granularity, timePerFft, fftMaxes, fftAvg, aboveAvg, averageMultiplier);
        return sustainedFft(config);
    }
    
    public static class NoteGenerationConfig {
        public final int stepGranularity;
        public final int skipChance;
        public final int holdDensity;
        public final TFloatArrayList[] fewTimes;
        public final TFloatArrayList fftAverages;
        public final TFloatArrayList fftMaxes;
        public final float timePerFft;
        public final float timePerBeat;
        public final float timeOffset;
        public final float totalTime;
        public final boolean allowMines;
        
        @SuppressWarnings("all")
        public NoteGenerationConfig(int stepGranularity, int skipChance, int holdDensity,
                                  TFloatArrayList[] fewTimes, TFloatArrayList fftAverages, 
                                  TFloatArrayList fftMaxes, float timePerFft,
                                  float timePerBeat, float timeOffset, float totalTime,
                                  boolean allowMines) {
            this.stepGranularity = stepGranularity;
            this.skipChance = skipChance;
            this.holdDensity = holdDensity;
            this.fewTimes = fewTimes;
            this.fftAverages = fftAverages;
            this.fftMaxes = fftMaxes;
            this.timePerFft = timePerFft;
            this.timePerBeat = timePerBeat;
            this.timeOffset = timeOffset;
            this.totalTime = totalTime;
            this.allowMines = allowMines;
        }
    }
    
    public String generateNotes(NoteGenerationConfig config) {      
        resetState(config.stepGranularity);
        return generateNoteLines(config);
    }
    
    @SuppressWarnings("all")
    public String generateNotes(int stepGranularity, int skipChance, int holdDensity,
                              TFloatArrayList[] fewTimes, TFloatArrayList fftAverages, 
                              TFloatArrayList fftMaxes, float timePerFft,
                              float timePerBeat, float timeOffset, float totalTime,
                              boolean allowMines) {
        NoteGenerationConfig config = new NoteGenerationConfig(
            stepGranularity, skipChance, holdDensity, fewTimes, fftAverages, 
            fftMaxes, timePerFft, timePerBeat, timeOffset, totalTime, allowMines);
        return generateNotes(config);
    }
    
    private void resetState(int stepGranularity) {
        allNoteLines.clear();
        lastJumpTime = -10f;
        holding[0] = 0f;
        holding[1] = 0f;
        holding[2] = 0f;
        holding[3] = 0f;
        lastKickTime = 0f;
        commaSeperatorReset = 4 * stepGranularity;
    }
    
    private String generateNoteLines(NoteGenerationConfig config) {
        int timeIndex = 0;
        float timeGranularity = config.timePerBeat / config.stepGranularity;
        for(float t = config.timeOffset; t <= config.totalTime; t += timeGranularity) {
            StepDecision decision = analyzeStepTiming(t, config, timeIndex);
            if( AutoStepper.STEP_DEBUG ) {
                makeNoteLine(getLastNoteLine(), t, timeIndex % 2 == 0 ? 1 : 0, -2, config.allowMines);
            } else makeNoteLine(getLastNoteLine(), t, decision.steps, decision.holds, config.allowMines);
            timeIndex++;
        }
        return formatOutput();
    }
    
    private StepDecision analyzeStepTiming(float t, NoteGenerationConfig config, int timeIndex) {
        StepDecision decision = new StepDecision();
        if( t > 0f ) {
            float fftmax = getFft(t, config.fftMaxes, config.timePerFft);
            boolean sustained = sustainedFft(t, 0.75f, config.timePerBeat / config.stepGranularity, 
                                           config.timePerFft, config.fftMaxes, config.fftAverages, 0.25f, 0.45f);
            boolean nearKick = isNearATime(t, config.fewTimes[AutoStepper.KICKS], config.timePerBeat / config.stepGranularity);
            boolean nearSnare = isNearATime(t, config.fewTimes[AutoStepper.SNARE], config.timePerBeat / config.stepGranularity);
            boolean nearEnergy = isNearATime(t, config.fewTimes[AutoStepper.ENERGY], config.timePerBeat / config.stepGranularity);
            
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
        if( timeIndex % 2 == 1 &&
            (config.skipChance > 1 && timeIndex % 2 == 1 && rand.nextInt(config.skipChance) > 0 || getHoldCount() > 0) ||
            t - lastJumpTime < config.timePerBeat ) {
            decision.steps = 0;
            if( decision.holds > 0 ) decision.holds = 0;
        }
        return decision;
    }
    
    private static class StepDecision {
        int steps = 0;
        int holds = 0;
    }
    
    private String formatOutput() {
        StringBuilder allNotes = new StringBuilder();
        buildNoteLines(allNotes);
        fillEmptyLines(allNotes);
        String[] lines = allNotes.toString().split("\n");
        NoteStatistics stats = calculateStatistics(lines);
        logStatistics(stats);
        return allNotes.toString();
    }
    
    private void buildNoteLines(StringBuilder allNotes) {
        commaSeperator = commaSeperatorReset;
        for(int i=0;i<allNoteLines.size();i++) {
            allNotes.append(getNoteLineIndex(i)).append("\n");
            commaSeperator--;
            if( commaSeperator == 0 ) {
                allNotes.append(",\n");
                commaSeperator = commaSeperatorReset;
            }
        }
    }
    
    private void fillEmptyLines(StringBuilder allNotes) {
        while( commaSeperator > 0 ) {
            allNotes.append("3333");
            commaSeperator--;
            if( commaSeperator > 0 ) allNotes.append("\n");
        }
    }
    
    private NoteStatistics calculateStatistics(String[] lines) {
        NoteStatistics stats = new NoteStatistics();
        for (String line : lines) {
            int ones = line.length() - line.replace("1", "").length();
            int twos = line.length() - line.replace("2", "").length();
            if ((ones == 1 || twos == 1) && (ones + twos == 1)) stats.taps++;
            else if ((ones == 2 || twos == 2) && (ones + twos == 2)) stats.jumps++;
            else if (ones == 3) stats.hands++;
            else if (ones >= 4) stats.quads++;
        }
        String allNotesStr = allNoteLines.toString();
        stats.holdCount = allNotesStr.length() - allNotesStr.replace("2", "").length();
        stats.mineCount = allNotesStr.length() - allNotesStr.replace("M", "").length();
        return stats;
    }
    
    private void logStatistics(NoteStatistics stats) {
        if (logger.isLoggable(java.util.logging.Level.INFO)) {
            logger.info(String.format("Taps: %d, Jumps: %d, Hands: %d, Quads: %d, Holds: %d, Mines: %d", 
                stats.taps + stats.jumps, stats.jumps, stats.hands, stats.quads, stats.holdCount, stats.mineCount));
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
