package ddf.minim.javasound;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Control;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * A SourceDataLine implementation that silently discards all audio.
 * This allows Minim to operate in environments without an available audio device,
 * such as headless servers used for offline analysis.
 */
final class NullSourceDataLine implements SourceDataLine
{
    private final List<LineListener> listeners = new CopyOnWriteArrayList<>();
    private volatile AudioFormat format;
    private volatile int bufferSize;
    private volatile boolean open;
    private volatile boolean running;

    @Override
    public void open(AudioFormat audioFormat, int requestedBufferSize) throws LineUnavailableException
    {
        this.format = audioFormat;
        this.bufferSize = requestedBufferSize;
        this.open = true;
        this.running = false;
        notifyListeners(LineEvent.Type.OPEN);
    }

    @Override
    public void open(AudioFormat audioFormat) throws LineUnavailableException
    {
        int defaultSize = audioFormat != null
                ? Math.max(1, (int)Math.ceil(audioFormat.getFrameRate())) * Math.max(1, audioFormat.getFrameSize())
                : 0;
        open(audioFormat, defaultSize);
    }

    @Override
    public void open() throws LineUnavailableException
    {
        if (format == null)
        {
            throw new LineUnavailableException("No AudioFormat specified for NullSourceDataLine");
        }
        if (!open)
        {
            open(format, bufferSize > 0 ? bufferSize : format.getFrameSize());
        }
    }

    @Override
    public int write(byte[] b, int off, int len)
    {
        return open ? len : 0;
    }

    @Override
    public void drain()
    {
        // no-op
    }

    @Override
    public void flush()
    {
        // no-op
    }

    @Override
    public void start()
    {
        running = true;
        notifyListeners(LineEvent.Type.START);
    }

    @Override
    public void stop()
    {
        running = false;
        notifyListeners(LineEvent.Type.STOP);
    }

    @Override
    public boolean isRunning()
    {
        return running;
    }

    @Override
    public boolean isActive()
    {
        return running;
    }

    @Override
    public AudioFormat getFormat()
    {
        return format;
    }

    @Override
    public int getBufferSize()
    {
        return bufferSize;
    }

    @Override
    public int available()
    {
        return bufferSize;
    }

    @Override
    public int getFramePosition()
    {
        return 0;
    }

    @Override
    public long getLongFramePosition()
    {
        return 0L;
    }

    @Override
    public long getMicrosecondPosition()
    {
        return 0L;
    }

    @Override
    public float getLevel()
    {
        return 0f;
    }

    @Override
    public boolean isOpen()
    {
        return open;
    }

    @Override
    public void close()
    {
        open = false;
        running = false;
        notifyListeners(LineEvent.Type.CLOSE);
    }

    @Override
    public Line.Info getLineInfo()
    {
        return new DataLine.Info(SourceDataLine.class, format);
    }

    @Override
    public void addLineListener(LineListener listener)
    {
        if (listener != null)
        {
            listeners.add(listener);
        }
    }

    @Override
    public void removeLineListener(LineListener listener)
    {
        listeners.remove(listener);
    }

    @Override
    public Control[] getControls()
    {
        return new Control[0];
    }

    @Override
    public boolean isControlSupported(Control.Type control)
    {
        return false;
    }

    @Override
    public Control getControl(Control.Type control)
    {
        throw new IllegalArgumentException("No controls supported by NullSourceDataLine");
    }

    private void notifyListeners(LineEvent.Type type)
    {
        LineEvent event = new LineEvent(this, type, -1);
        for (LineListener listener : listeners)
        {
            listener.update(event);
        }
    }
}
