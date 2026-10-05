package io.github.rootscripts.riff;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@androidx.media3.common.util.UnstableApi
public final class RiffAudioProcessor extends BaseAudioProcessor {
  public volatile float echo, distortion;
  private float[] delay = new float[1];
  private int cursor;

  @Override
  protected AudioFormat onConfigure(AudioFormat input)
      throws AudioProcessor.UnhandledAudioFormatException {
    if (input.encoding != C.ENCODING_PCM_16BIT)
      throw new AudioProcessor.UnhandledAudioFormatException(input);
    return input;
  }

  @Override
  protected void onFlush() {
    cursor = 0;
    delay =
        new float
            [Math.max(
                1, (int) (inputAudioFormat.sampleRate * .28) * inputAudioFormat.channelCount)];
  }

  @Override
  public void queueInput(ByteBuffer input) {
    ByteBuffer output = replaceOutputBuffer(input.remaining()).order(ByteOrder.nativeOrder());
    float echoMix = echo, drive = distortion, amount = 1 + drive * 14;
    while (input.remaining() >= 2) {
      float value = input.getShort() / 32768f;
      if (drive > 0) value = (float) (Math.tanh(value * amount) / Math.tanh(amount));
      float previous = delay[cursor];
      delay[cursor] = value + previous * echoMix * .45f;
      value += previous * echoMix * .55f;
      cursor = (cursor + 1) % delay.length;
      output.putShort((short) Math.max(-32768, Math.min(32767, Math.round(value * 32768))));
    }
    output.flip();
  }

  @Override
  protected void onReset() {
    delay = new float[1];
    cursor = 0;
  }
}
