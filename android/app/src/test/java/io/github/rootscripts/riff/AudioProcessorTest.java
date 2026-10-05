package io.github.rootscripts.riff;

import static org.junit.Assert.*;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class AudioProcessorTest {
  @Test
  public void drySignalIsUnchanged() throws Exception {
    RiffAudioProcessor p = new RiffAudioProcessor();
    p.configure(new AudioProcessor.AudioFormat(1000, 1, C.ENCODING_PCM_16BIT));
    p.flush();
    ByteBuffer input = ByteBuffer.allocateDirect(6).order(ByteOrder.nativeOrder());
    input.putShort((short) -1234).putShort((short) 0).putShort((short) 2345).flip();
    p.queueInput(input);
    ByteBuffer out = p.getOutput();
    assertEquals(-1234, out.getShort());
    assertEquals(0, out.getShort());
    assertEquals(2345, out.getShort());
  }

  @Test
  public void echoArrivesAfterTheDelayAndDistortionStaysBounded() throws Exception {
    RiffAudioProcessor p = new RiffAudioProcessor();
    p.echo = 1;
    p.distortion = .5f;
    p.configure(new AudioProcessor.AudioFormat(1000, 1, C.ENCODING_PCM_16BIT));
    p.flush();
    ByteBuffer input = ByteBuffer.allocateDirect(800).order(ByteOrder.nativeOrder());
    input.putShort((short) 16000);
    while (input.hasRemaining()) input.putShort((short) 0);
    input.flip();
    p.queueInput(input);
    ByteBuffer out = p.getOutput();
    assertTrue(out.getShort(0) > 16000);
    assertEquals(0, out.getShort(2));
    assertTrue(out.getShort(560) > 0);
  }
}
