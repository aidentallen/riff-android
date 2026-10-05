package io.github.rootscripts.riff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class Io {
  static void copy(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[32768];
    int count;
    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
  }

  static byte[] read(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    copy(in, out);
    return out.toByteArray();
  }
}
