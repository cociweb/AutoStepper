/*
 *  Copyright (c) 2007 - 2008 by Damien Di Fede <ddf@compartmental.net>
 *
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU Library General Public License as published
 *   by the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Library General Public License for more details.
 *
 *   You should have received a copy of the GNU Library General Public
 *   License along with this program; if not, write to the Free Software
 *   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package ddf.minim.javasound;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

import java.util.HashMap;
import java.util.Map;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;


import ddf.minim.AudioMetaData;
import ddf.minim.AudioSample;
import ddf.minim.Minim;
import ddf.minim.Recordable;
import ddf.minim.spi.AudioOut;
import ddf.minim.spi.AudioRecording;
import ddf.minim.spi.AudioRecordingStream;
import ddf.minim.spi.AudioStream;
import ddf.minim.spi.MinimServiceProvider;
import ddf.minim.spi.SampleRecorder;

/**
 * JSMinim is an implementation of the {@link MinimServiceProvider} interface that use
 * Javasound to provide all audio functionality. That's about all you really need to know about it.
 * 
 * @author Damien Di Fede
 *
 */

public class JSMinim implements MinimServiceProvider
{
	private boolean debug;
	private Object  fileLoader;
	private Method  sketchPath;
	private Method  createInput;
	private Mixer   inputMixer;
	private Mixer   outputMixer;

	public JSMinim(Object parent)
	{
		debug = true;
		fileLoader = parent;
		inputMixer = null;
		outputMixer = null;
		
		String error = "";
		
		try
		{
			sketchPath = parent.getClass().getMethod( "sketchPath", String.class );
			if ( sketchPath.getReturnType() != String.class )
			{
				error += "The method sketchPath in the file loading object provided does not return a String!\n";
				sketchPath = null;
			}
		}
		catch( NoSuchMethodException ex )
		{
			error += "Couldn't find a sketchPath method on the file loading object provided!\n";
		}
		catch( Exception ex )
		{
			error += "Failed to get method sketchPath from file loading object provided!\n" + ex.getMessage() + "\n";
		}
		
		if ( error.length() > 0 )
		{
			error += "File recording will be disabled.";
			error( error );
		}
		
		error = "";
		
		try
		{
			createInput = parent.getClass().getMethod( "createInput", String.class );
			if ( createInput.getReturnType() != InputStream.class )
			{
				error += "The method createInput in the file loading object provided does not return an InputStream!\n";
				createInput = null;
			}
		}
		catch( NoSuchMethodException ex )
		{
			error += "Couldn't find a createInput method in the file loading object provided!\n";
		}
		catch( Exception ex )
		{
			error += "Failed to get method createInput from the file loading object provided!\n" + ex.getMessage() + "\n";
		}
		
		if ( error.length() > 0 )
		{
			error += "File loading will be disabled.";
			error( error );
		}
	}
  
  public void setInputMixer(Mixer mix)
  {
    inputMixer = mix;
  }
  
  public Mixer getInputMixer()  
  {
    return inputMixer;
  }
  
  public void setOutputMixer(Mixer mix)
  {
    outputMixer = mix;
  }
  
  public Mixer getOutputMixer()
  {
    return outputMixer;
  }

	public void start()
	{
	}

	public void stop()
	{
	}
	
	public void debugOn()
	{
		debug = true;
	}
	
	public void debugOff()
	{
		debug = false;
	}
	
	void debug(String s)
	{
		if ( debug )
		{
			System.out.println("==== JavaSound Minim Debug ====");
			String[] lines = s.split("\n");
			for(int i = 0; i < lines.length; i++)
			{
				System.out.println("==== " + lines[i]);
			}
			System.out.println();
		}
	}
	
	void error(String s)
	{
                // this is always annoying junk, not real errors
		/*System.out.println("==== JavaSound Minim Error ====");
		String[] lines = s.split("\n");
		for(int i = 0; i < lines.length; i++)
		{
			System.out.println("==== " + lines[i]);
		}
		System.out.println();*/
	}

	public SampleRecorder getSampleRecorder(Recordable source, String fileName,
			boolean buffered)
	{
		// do nothing if we can't generate a place to put the file
		if ( sketchPath == null ) return null;
		
		String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
		debug("createRecorder: file extension is " + ext + ".");
		AudioFileFormat.Type fileType = null;
		if (ext.equals(Minim.WAV.getExtension()))
		{
			fileType = Minim.WAV;
		}
		else if (ext.equals(Minim.AIFF.getExtension()) || ext.equals("aif"))
		{
			fileType = Minim.AIFF;
		}
		else if (ext.equals(Minim.AIFC.getExtension()))
		{
			fileType = Minim.AIFC;
		}
		else if (ext.equals(Minim.AU.getExtension()))
		{
			fileType = Minim.AU;
		}
		else if (ext.equals(Minim.SND.getExtension()))
		{
			fileType = Minim.SND;
		}
		else
		{
			error("The extension " + ext + " is not a recognized audio file type.");
			return null;
		}
		
		SampleRecorder recorder = null;
		
		try
		{
			String destPath = (String)sketchPath.invoke( fileLoader, fileName );
			if (buffered)
			{
				recorder = new JSBufferedSampleRecorder(this,
	                                  					destPath,
														fileType,
														source.getFormat(),
														source.bufferSize());
			}
			else
			{
				// TODO: Implement JSStreamingSampleRecorder for non-buffered recording
				recorder = new JSBufferedSampleRecorder(this,
														 destPath,
														 fileType,
														 source.getFormat(),
														source.bufferSize());
			}
		}
		catch( Exception ex )
		{
			Minim.error( "Couldn't invoke the sketchPath method: " + ex.getMessage() );
		}
		
		return recorder;
	}

	public AudioRecordingStream getAudioRecordingStream(String filename,
			int bufferSize, boolean inMemory)
	{
		// TODO: deal with the case of wanting to have the file fully in memory
		AudioRecordingStream mstream = null;
		AudioInputStream ais = getAudioInputStream(filename);
		if (ais != null)
		{
			if ( inMemory && ais.markSupported() )
			{
				ais.mark( (int)ais.getFrameLength() * ais.getFormat().getFrameSize() );
			}
			
			debug("Reading from " + ais.getClass().toString());
			debug("File format is: " + ais.getFormat().toString());
			AudioFormat format = ais.getFormat();
			// convert to PCM if the decoded stream isn't already PCM
			if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding()))
			{
				AudioFormat baseFormat = format;
				format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
													baseFormat.getSampleRate(), 16,
													baseFormat.getChannels(),
													baseFormat.getChannels() * 2,
													baseFormat.getSampleRate(), false);
				// converts the stream to PCM audio from mp3 audio
				AudioInputStream decAis = getAudioInputStream(format, ais);
				debug("MP3 conversion: original format=" + baseFormat + ", target format=" + format + ", decoded stream=" + (decAis != null ? "success" : "failed"));
				// source data line is for sending the file audio out to the
				// speakers
				SourceDataLine line = getSourceDataLine(format, bufferSize);
				if (decAis != null && line != null)
				{
					Map<String, Object> props = getID3Tags(filename);
					long lengthInMillis = -1;
					if (props.containsKey("duration"))
					{
						Long dur = (Long)props.get("duration");
		            if ( dur.longValue() > 0 )
		            {
		              lengthInMillis = dur.longValue() / 1000;
		            }
					}
					MP3MetaData meta = new MP3MetaData(filename, lengthInMillis, props);
					mstream = new JSMPEGAudioRecordingStream(this, meta, ais,	decAis, line, bufferSize);
				}
			} // format instanceof MpegAudioFormat
			else
			{
				// source data line is for sending the file audio out to the
				// speakers
				SourceDataLine line = getSourceDataLine(format, bufferSize);
				if (line != null)
				{
					long length = (long)((ais.getFrameLength() * 1000.0) / format.getFrameRate());
					BasicMetaData meta = new BasicMetaData(filename, length, ais.getFrameLength());
					mstream = new JSPCMAudioRecordingStream(this, meta, ais, line, bufferSize);
				}
			} // else
		} // ais != null
		return mstream;
	}

	private Map<String, Object> getID3Tags(String filename)
	{
		debug("Getting the properties.");
		Map<String, Object> props = new HashMap<String, Object>();
		try
		{
			InputStream stream = (InputStream)createInput.invoke(fileLoader, filename);
			if ( stream != null )
			{
				// Try to get file format; not all implementations expose properties.
				AudioFileFormat baseFileFormat = AudioSystem.getAudioFileFormat(stream);
				stream.close();
				// If properties are available, attempt to read via reflection-safe cast
				try {
					Map<String, Object> p = baseFileFormat.properties();
					if (p != null) props = p;
				} catch (Exception ignore) { }
			}
		}
		catch (UnsupportedAudioFileException e)
		{
			error("Couldn't get the file format for " + filename + ": "
					+ e.getMessage());
		}
		catch (IOException e)
		{
			error("Couldn't access " + filename + ": " + e.getMessage());
		}
		catch( Exception e )
		{
			error("Error invoking createInput on the file loader object: " + e.getMessage());
		}
		
		return props;
	}

	public AudioStream getAudioInput(int type, int bufferSize,
			float sampleRate, int bitDepth)
	{
		if (bitDepth != 8 && bitDepth != 16)
		{
			throw new IllegalArgumentException("Unsupported bit depth, use either 8 or 16.");
		}
		AudioFormat format = new AudioFormat(sampleRate, bitDepth, type, true, false);
		TargetDataLine line = getTargetDataLine(format, bufferSize * 4);
		if (line != null)
		{
			return new JSAudioInput(line, bufferSize);
		}
		return null;
	}

	@SuppressWarnings("deprecation")
	public AudioSample getAudioSample(String filename, int bufferSize)
	{
		AudioInputStream ais = getAudioInputStream(filename);
		if (ais != null)
		{
			AudioMetaData meta = null;
			AudioFormat format = ais.getFormat();
			FloatSampleBuffer samples = null;
			if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding()))
			{
				AudioFormat baseFormat = format;
				format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
													baseFormat.getSampleRate(), 16,
													baseFormat.getChannels(),
													baseFormat.getChannels() * 2,
													baseFormat.getSampleRate(), false);
				// converts the stream to PCM audio from mp3 audio
				ais = getAudioInputStream(format, ais);
				// get a map of properties so we can find out how long it is
				Map<String, Object> props = getID3Tags(filename);
				// there is a property called mp3.length.bytes, but that is
				// the length in bytes of the mp3 file, which will of course
				// be much shorter than the decoded version. so we use the
				// duration of the file to figure out how many bytes the
				// decoded file will be.
				long dur = ((Long)props.get("duration")).longValue();
				int toRead = (int)(((dur / 1000.0) * format.getFrameRate()) * format.getFrameSize());
				samples = loadFloatAudio(ais, toRead);
				meta = new MP3MetaData(filename, dur / 1000, props);
			}
			else
			{
				samples = loadFloatAudio(ais, (int)ais.getFrameLength() * format.getFrameSize());
				long length = (long)((samples.getSampleCount() * 1000.0) / format.getFrameRate());
				meta = new BasicMetaData(filename, length, samples.getSampleCount());
			}
			AudioOut out = getAudioOutput(format.getChannels(), 
			                                             bufferSize, 
			                                             format.getSampleRate(), 
			                                             format.getSampleSizeInBits());
			if (out != null)
			{
				SampleSignal ssig = new SampleSignal(samples);
				out.setAudioSignal(ssig);
				return new JSAudioSample(meta, ssig, out);
			}
			else
			{
				error("Couldn't acquire an output.");
			}
		}
		return null;
	}
  
  public AudioSample getAudioSample(float[] samples, AudioFormat format, int bufferSize)
  {
    FloatSampleBuffer sample = new FloatSampleBuffer(1, samples.length, format.getSampleRate());
    System.arraycopy(samples, 0, sample.getChannel(0), 0, samples.length);
    return getAudioSampleImp(sample, format, bufferSize);
  }
  
  public AudioSample getAudioSample(float[] left, float[] right, AudioFormat format, int bufferSize)
  {
    FloatSampleBuffer sample = new FloatSampleBuffer(2, left.length, format.getSampleRate());
    System.arraycopy(left, 0, sample.getChannel(0), 0, left.length);
    System.arraycopy(right, 0, sample.getChannel(1), 0, right.length);
    return getAudioSampleImp(sample, format, bufferSize);
  }
  
	@SuppressWarnings("deprecation")
  private JSAudioSample getAudioSampleImp(FloatSampleBuffer samples, AudioFormat format, int bufferSize)
  {
    AudioOut out = getAudioOutput( samples.getChannelCount(), 
                                                bufferSize, 
                                                format.getSampleRate(), 
                                                format.getSampleSizeInBits()
                                              );
    if (out != null)
    {
      SampleSignal ssig = new SampleSignal(samples);
      out.setAudioSignal(ssig);
      long length = (long)((samples.getSampleCount() * 1000.0) / format.getFrameRate());
      BasicMetaData meta = new BasicMetaData(samples.toString(), length, samples.getSampleCount());
      return new JSAudioSample(meta, ssig, out);
    }
    else
    {
      error("Couldn't acquire an output.");
    }
    
    return null;
  }

	public AudioOut getAudioOutput(int type, int bufferSize,
			float sampleRate, int bitDepth)
	{
		if (bitDepth != 8 && bitDepth != 16)
		{
			throw new IllegalArgumentException("Unsupported bit depth, use either 8 or 16.");
		}
		AudioFormat format = new AudioFormat(sampleRate, bitDepth, type, true, false);
		SourceDataLine sdl = getSourceDataLine(format, bufferSize);
		if (sdl != null)
		{
			return new JSAudioOutput(sdl, bufferSize);
		}
		return null;
	}

	/** @deprecated */
	public AudioRecording getAudioRecordingClip(String filename)
	{
		Clip clip = null;
		AudioMetaData meta = null;
		AudioInputStream ais = getAudioInputStream(filename);
		if (ais != null)
		{
			AudioFormat format = ais.getFormat();
			if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding()))
			{
				AudioFormat baseFormat = format;
				format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
													baseFormat.getSampleRate(), 16,
													baseFormat.getChannels(),
													baseFormat.getChannels() * 2,
													baseFormat.getSampleRate(), false);
				// converts the stream to PCM audio from mp3 audio
				ais = getAudioInputStream(format, ais);
			}
			DataLine.Info info = new DataLine.Info(Clip.class, ais.getFormat());
			if (AudioSystem.isLineSupported(info))
			{
				// Obtain and open the line.
				try
				{
					clip = (Clip)AudioSystem.getLine(info);
					clip.open(ais);
				}
				catch (Exception e)
				{
					error("Error obtaining Javasound Clip: " + e.getMessage());
					return null;
				}
				Map<String, Object> props = getID3Tags(filename);
				long lengthInMillis = -1;
				if (props.containsKey("duration"))
				{
					Long dur = (Long)props.get("duration");
					lengthInMillis = dur.longValue() / 1000;
				}
				meta = new MP3MetaData(filename, lengthInMillis, props);
			}
			else
			{
				error("File format not supported.");
				return null;
			}
		}
		if (meta == null)
		{
			// this means we're dealing with not-an-mp3
			meta = new BasicMetaData(filename, clip.getMicrosecondLength() / 1000, -1);
		}
		return new JSAudioRecordingClip(clip, meta);
	}
	
	/** @deprecated */
	public AudioRecording getAudioRecording(String filename)
	{
		AudioMetaData meta = null;
		AudioInputStream ais = getAudioInputStream(filename);
		byte[] samples;
		if (ais != null)
		{
			AudioFormat format = ais.getFormat();
			if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding()))
			{
				AudioFormat baseFormat = format;
				format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
													baseFormat.getSampleRate(), 16,
													baseFormat.getChannels(),
													baseFormat.getChannels() * 2,
													baseFormat.getSampleRate(), false);
				// converts the stream to PCM audio from mp3 audio
				ais = getAudioInputStream(format, ais);
				//	 get a map of properties so we can find out how long it is
				Map<String, Object> props = getID3Tags(filename);
				// there is a property called mp3.length.bytes, but that is
				// the length in bytes of the mp3 file, which will of course
				// be much shorter than the decoded version. so we use the
				// duration of the file to figure out how many bytes the
				// decoded file will be.
				long dur = ((Long)props.get("duration")).longValue();
				int toRead = (int)(((dur / 1000.0) * format.getFrameRate()) * format.getFrameSize());
				samples = loadByteAudio(ais, toRead);
				meta = new MP3MetaData(filename, dur / 1000, props);
			}
			else
			{
				samples = loadByteAudio(ais, (int)ais.getFrameLength() * format.getFrameSize());
				long length = (long)((samples.length * 1000.0) / (format.getFrameRate() * format.getFrameSize()));
				meta = new BasicMetaData(filename, length, samples.length);
			}
			SourceDataLine line = getSourceDataLine(format, 2048);
			if ( line != null )
			{
				return new JSAudioRecording(this, samples, line, meta);
			}
		}
		return null;
	}
	
	private FloatSampleBuffer loadFloatAudio(AudioInputStream ais, int toRead)
	{
		FloatSampleBuffer samples = new FloatSampleBuffer();
		int totalRead = 0;
		byte[] rawBytes = new byte[toRead];
		try
		{
			// we have to read in chunks because the decoded stream won't
			// read more than about 2000 bytes at a time
			while (totalRead < toRead)
			{
				int actualRead = ais.read(rawBytes, totalRead, toRead	- totalRead);
				if (actualRead < 1)
        {
					break;
        }
				totalRead += actualRead;
			}
			ais.close();
		}
		catch (Exception ioe)
		{
			error("Error loading file into memory: " + ioe.getMessage());
		}
		debug("Needed to read " + toRead + " actually read " + totalRead);
		samples.initFromByteArray(rawBytes, 0, totalRead, ais.getFormat());
		return samples;
	}
	
	private byte[] loadByteAudio(AudioInputStream ais, int toRead)
	{
		int totalRead = 0;
		byte[] rawBytes = new byte[toRead];
		try
		{
			// we have to read in chunks because the decoded stream won't
			// read more than about 2000 bytes at a time
			while (totalRead < toRead)
			{
				int actualRead = ais.read(rawBytes, totalRead, toRead	- totalRead);
				if (actualRead < 1)
					break;
				totalRead += actualRead;
			}
			ais.close();
		}
		catch (Exception ioe)
		{
			error("Error loading file into memory: " + ioe.getMessage());
		}
		debug("Needed to read " + toRead + " actually read " + totalRead);
		return rawBytes;
	}

  /**
   * 
   * @param filename the 
   * @param is
   * @return
   */
	AudioInputStream getAudioInputStream(String filename)
	{
		AudioInputStream ais = null;
		BufferedInputStream bis = null;
		if (filename.startsWith("http"))
		{
			try
			{
				URI uri = new URI(filename);
				ais = getAudioInputStream(uri);
			}
			catch (URISyntaxException | MalformedURLException e)
			{
				error("Bad URL: " + e.getMessage());
			}
			catch (UnsupportedAudioFileException e)
			{
				error("URL is in an unsupported audio file format: " + e.getMessage());
			}
			catch (IOException e)
			{
				Minim.error("Error reading the URL: " + e.getMessage());
			}
		}
		else
		{
			try
			{
				InputStream is = (InputStream)createInput.invoke(fileLoader, filename);
				if ( is != null )
				{
					debug("Base input stream is: " + is.toString());
					bis = new BufferedInputStream(is);
					ais = getAudioInputStream(bis);
				
					if ( ais != null )
					{
						// don't mark it like this because it means the entire
						// file will be loaded into memory as it plays. this 
						// will cause out-of-memory problems with very large files.
						// ais.mark((int)ais.available());
						debug("Acquired AudioInputStream.\n" + "It is "
								+ ais.getFrameLength() + " frames long.\n"
								+ "Marking support: " + ais.markSupported());
					}
				}
				else 
				{
					throw new FileNotFoundException(filename);
				}
			}
			catch( Exception e )
			{
				error( e.toString() );
			}
		}
		return ais;
	}

	/**
	 * This method is also part of AppletMpegSPIWorkaround, which uses yet
	 * another workaround to load an internet radio stream.
	 * 
	 * @param url
	 *           the URL of the stream
	 * @return an AudioInputStream of the streaming audio
	 * @throws UnsupportedAudioFileException
	 * @throws IOException
	 */
	AudioInputStream getAudioInputStream(URI uri)
			throws UnsupportedAudioFileException, IOException
	{
		// Use standard AudioSystem for URL input
		return AudioSystem.getAudioInputStream(uri.toURL());
	}

	/**
	 * This method is a replacement for
	 * AudioSystem.getAudioInputStream(InputStream), which includes workaround
	 * for getting an mp3 AudioInputStream when sketch is running in an applet.
	 * The workaround was developed by the Tritonus team and originally comes
	 * from the package javazoom.jlgui.basicplayer
	 * 
	 * @param is
	 *           The stream to convert to an AudioInputStream
	 * @return an AudioInputStream that will read from is
	 * @throws UnsupportedAudioFileException
	 * @throws IOException
	 */
	AudioInputStream getAudioInputStream(InputStream is)
			throws UnsupportedAudioFileException, IOException
	{
		return AudioSystem.getAudioInputStream(is);
	}

	/**
	 * This method is a replacement for
	 * AudioSystem.getAudioInputStream(AudioFormat, AudioInputStream), which is
	 * used for audio format conversion at the stream level. This method includes
	 * a workaround for converting from an mp3 AudioInputStream when the sketch
	 * is running in an applet. The workaround was developed by the Tritonus team
	 * and originally comes from the package javazoom.jlgui.basicplayer
	 * 
	 * @param targetFormat
	 *           the AudioFormat to convert the stream to
	 * @param sourceStream
	 *           the stream containing the unconverted audio
	 * @return an AudioInputStream in the target format
	 */
	AudioInputStream getAudioInputStream(AudioFormat targetFormat,
			AudioInputStream sourceStream)
	{
		        try {
            return AudioSystem.getAudioInputStream(targetFormat, sourceStream);
        } catch (IllegalArgumentException iae) {
            throw new UnsupportedOperationException("MPEG format conversion not supported: " + iae.getMessage());
        }
	}

	SourceDataLine getSourceDataLine(AudioFormat format, int bufferSize)
	{
		SourceDataLine line = null;
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
		if ( AudioSystem.isLineSupported(info) ) 
		{
			try
			{
		        if ( outputMixer == null )
		        {
		          line = (SourceDataLine)AudioSystem.getLine(info);
		        }
		        else
		        {
		          line = (SourceDataLine)outputMixer.getLine(info);
		        }
				// remember that time you spent, like, an entire afternoon fussing
				// with this buffer size to try to get the latency decent on Linux?
				// Yah, don't fuss with this anymore, ok?
				line.open(format, bufferSize * format.getFrameSize() * 4);
				if ( line.isOpen() )
				{
					debug("SourceDataLine is " + line.getClass().toString() + "\n"
					      + "Buffer size is " + line.getBufferSize() + " bytes.\n" 
					      + "Format is "	+ line.getFormat().toString() + ".");
				}
				else
				{
					line = null;
				}
			}
			catch (Exception e)
			{
				error("Couldn't open the line: " + e.getMessage());
				line = null;
			}
		}
		else
		{
			error("Unable to return a SourceDataLine: unsupported format - " + format.toString());
		}

		if ( line == null )
		{
			debug("Falling back to NullSourceDataLine for headless playback.");
			line = new NullSourceDataLine();
			try
			{
				line.open(format, Math.max(1, bufferSize * format.getFrameSize()));
			}
			catch (LineUnavailableException e)
			{
				error("NullSourceDataLine unexpectedly unavailable: " + e.getMessage());
				return null;
			}
		}

		return line;
	}

	TargetDataLine getTargetDataLine(AudioFormat format, int bufferSize)
	{
		TargetDataLine line = null;
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
		if (AudioSystem.isLineSupported(info))
		{
			try
			{
        if ( inputMixer == null )
        {
          line = (TargetDataLine)AudioSystem.getLine(info);
        }
        else
        {
          line = (TargetDataLine)inputMixer.getLine(info);
        }
				line.open(format, bufferSize * format.getFrameSize());
				debug("TargetDataLine buffer size is " + line.getBufferSize()
						+ "\n" + "TargetDataLine format is "
						+ line.getFormat().toString() + "\n"
						+ "TargetDataLine info is " + line.getLineInfo().toString());
			}
			catch (Exception e)
			{
				error("Error acquiring TargetDataLine: " + e.getMessage());
			}
		}
		else
		{
			error("Unable to return a TargetDataLine: unsupported format - " + format.toString());
		}
		return line;
	}

}
