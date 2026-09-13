package org.intervalica.infrastructure.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.List;

public class AudioSynthesizerEngine {
  private static final int SAMPLE_RATE = 44100;

  public enum Waveform {
    SINE, SQUARE, SAWTOOTH, TRIANGLE
  }

  /**
   * Synthesizes and streams multiple frequencies simultaneously using advanced wave synthesis models.
   */
  public static void playFrequencies(List<Double> frequencies, double durationInSeconds, Waveform waveform) {
    if (frequencies == null || frequencies.isEmpty() || durationInSeconds <= 0) return;

    AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, true);
    try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
      line.open(format, 4096);
      line.start();

      int totalSamples = (int) (SAMPLE_RATE * durationInSeconds);
      byte[] buffer = new byte[1024];
      int bufferIdx = 0;

      int fadeSamples = (int) (SAMPLE_RATE * 0.005); // 5ms anti-click ramp
      if (fadeSamples * 2 > totalSamples) fadeSamples = totalSamples / 2;

      for (int sample = 0; sample < totalSamples; sample++) {
        double time = (double) sample / SAMPLE_RATE;
        double combinedWave = 0.0;

        for (double freq : frequencies) {
          if (freq <= 0) continue;

          // Core math cycle calculation for periodic synthesis [0.0, 1.0]
          double cycle = (time * freq) % 1.0;

          switch (waveform) {
            case SQUARE:
              // High state (+1) for first half of cycle, Low state (-1) for second half
              combinedWave += (cycle < 0.5) ? 1.0 : -1.0;
              break;

            case SAWTOOTH:
              // Linear ramp from -1.0 to +1.0 across the entire cycle period
              combinedWave += 2.0 * cycle - 1.0;
              break;

            case TRIANGLE:
              // Double linear ramp rising and falling smoothly
              if (cycle < 0.25) {
                combinedWave += 4.0 * cycle;
              } else if (cycle < 0.75) {
                combinedWave += 2.0 - 4.0 * cycle;
              } else {
                combinedWave += 4.0 * cycle - 4.0;
              }
              break;

            case SINE:
            default:
              // Standard pure harmonic sinusoidal wave mathematical function
              combinedWave += Math.sin(2.0 * Math.PI * freq * time);
              break;
          }
        }

        // Attenuation mix to block structural digital clipping overflow
        combinedWave /= frequencies.size();

        // Anti-pop linear attack/release tracking
        double envelope = 1.0;
        if (sample < fadeSamples) {
          envelope = (double) sample / fadeSamples;
        } else if (sample > totalSamples - fadeSamples) {
          envelope = (double) (totalSamples - sample) / fadeSamples;
        }
        combinedWave *= envelope;

        short pcmValue = (short) (combinedWave * Short.MAX_VALUE);

        buffer[bufferIdx++] = (byte) ((pcmValue >> 8) & 0xFF);
        buffer[bufferIdx++] = (byte) (pcmValue & 0xFF);

        if (bufferIdx >= buffer.length) {
          line.write(buffer, 0, bufferIdx);
          bufferIdx = 0;
        }
      }

      if (bufferIdx > 0) {
        line.write(buffer, 0, bufferIdx);
      }

      line.drain();
      line.stop();
    } catch (Exception e) {
      System.err.println("[Audio Engine Exception] Synthesis stream line failure: " + e.getMessage());
    }
  }
}
