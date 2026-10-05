package io.github.rootscripts.riff;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class LyricsTest {
  @Test
  public void parsesRepeatedTimestampsAndFractions() {
    List<Lyrics.Line> lines = Lyrics.parse("[ar:Artist]\n[00:03.125][00:01.2]Hello\n[00:05]World");
    assertEquals(3, lines.size());
    assertEquals(1200, lines.get(0).time);
    assertEquals("Hello", lines.get(0).text);
    assertEquals(3125, lines.get(1).time);
    assertEquals(5000, lines.get(2).time);
  }

  @Test
  public void honorsOffsetWithoutNegativeTimes() {
    List<Lyrics.Line> lines = Lyrics.parse("[offset:-1500]\n[00:01]Start\n[00:02.50]Next");
    assertEquals(0, lines.get(0).time);
    assertEquals(1000, lines.get(1).time);
  }

  @Test
  public void activeLineChangesAtTheBoundary() {
    List<Lyrics.Line> lines = Lyrics.parse("[00:01]One\n[00:04]Two");
    assertEquals(-1, Lyrics.active(lines, 999));
    assertEquals(0, Lyrics.active(lines, 1000));
    assertEquals(0, Lyrics.active(lines, 3999));
    assertEquals(1, Lyrics.active(lines, 4000));
  }

  @Test
  public void keepsPlainLyricsAndBlankLines() {
    List<Lyrics.Line> lines = Lyrics.parse("First line\n\nThird line");
    assertEquals(3, lines.size());
    assertEquals(-1, lines.get(0).time);
    assertEquals("", lines.get(1).text);
    assertEquals(-1, Lyrics.active(lines, 5000));
  }
}
