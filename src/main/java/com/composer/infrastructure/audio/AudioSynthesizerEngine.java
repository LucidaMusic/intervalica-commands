package src.main.java.com.composer.infrastructure.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.List;

public class AudioSynthesizerEngine {
  private static final int SAMPLE_RATE = 44100; // Standard CD Audio Quality Sampling Rate

  /**
   * Renders and streams pure sine waves for a group of frequencies simultaneously.
   * Uses automatic attenuation to prevent digital clipping and safe exponential math.
   */
  public static void playFrequencies(List<Double> frequencies, double durationInSeconds) {
    if (frequencies == null || frequencies.isEmpty() || durationInSeconds <= 0) return;

    AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, true);
    try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
      line.open(format, 4096);
      line.start();

      int totalSamples = (int) (SAMPLE_RATE * durationInSeconds);
      byte[] buffer = new byte[1024];
      int bufferIdx = 0;

      // Amplitude envelope parameters to eliminate pops/clicks (5ms ramp)
      int fadeSamples = (int) (SAMPLE_RATE * 0.005);
      if (fadeSamples * 2 > totalSamples) {
        fadeSamples = totalSamples / 2;
      }

      for (int sample = 0; sample < totalSamples; sample++) {
        double time = (double) sample / SAMPLE_RATE;
        double combinedWave = 0.0;

        // Polyphonic Additive Synthesis Mix
        for (double freq : frequencies) {
          if (freq > 0) {
            combinedWave += Math.sin(2.0 * Math.PI * freq * time);
          }
        }

        // Attenuate volume dynamically by the total number of notes to avoid digital clipping
        combinedWave /= frequencies.size();

        // Apply Amplitude Attack/Release Envelope (Fade-In / Fade-Out)
        double envelope = 1.0;
        if (sample < fadeSamples) {
          envelope = (double) sample / fadeSamples; // Linear Attack Ramp
        } else if (sample > totalSamples - fadeSamples) {
          envelope = (double) (totalSamples - sample) / fadeSamples; // Linear Release Ramp
        }
        combinedWave *= envelope;

        // Scale float wave [-1.0, 1.0] onto signed 16-bit PCM short integer boundary
        short pcmValue = (short) (combinedWave * Short.MAX_VALUE);

        // Write into byte buffer array (Big Endian serialization layout matching format)
        buffer[bufferIdx++] = (byte) ((pcmValue >> 8) & 0xFF);
        buffer[bufferIdx++] = (byte) (pcmValue & 0xFF);

        // Flush buffer block chunk onto the hardware mixer line stream whenever full
        if (bufferIdx >= buffer.length) {
          line.write(buffer, 0, bufferIdx);
          bufferIdx = 0;
        }
      }

      // Flush remaining trailing sample elements
      if (bufferIdx > 0) {
        line.write(buffer, 0, bufferIdx);
      }

      line.drain();
      line.stop();
    } catch (Exception e) {
      System.err.println("[Audio Hardware Engine Exception] " + e.getMessage());
    }
  }
}
