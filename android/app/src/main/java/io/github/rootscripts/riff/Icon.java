package io.github.rootscripts.riff;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

public final class Icon extends View {
  public String kind;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path path = new Path();
  private int color;

  public Icon(Context c, String kind, int color) {
    super(c);
    this.kind = kind;
    this.color = color;
    setMinimumWidth(24);
    setMinimumHeight(24);
  }

  public Icon(Context c) {
    this(c, "music", android.graphics.Color.WHITE);
  }

  public void kind(String value) {
    kind = value;
    invalidate();
  }

  public void color(int value) {
    color = value;
    invalidate();
  }

  private void line(Canvas c, float... xy) {
    path.reset();
    path.moveTo(xy[0], xy[1]);
    for (int i = 2; i < xy.length; i += 2) path.lineTo(xy[i], xy[i + 1]);
    c.drawPath(path, paint);
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    c.save();
    float side = Math.min(getWidth(), getHeight());
    c.translate((getWidth() - side) / 2, (getHeight() - side) / 2);
    c.scale(side / 24, side / 24);
    paint.setColor(color);
    paint.setStrokeWidth(1.8f);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
    paint.setStyle(Paint.Style.STROKE);
    switch (kind) {
      case "play":
        paint.setStyle(Paint.Style.FILL);
        line(c, 8, 5, 20, 12, 8, 19, 8, 5);
        break;
      case "pause":
        paint.setStyle(Paint.Style.FILL);
        c.drawRoundRect(6, 5, 10, 19, 1, 1, paint);
        c.drawRoundRect(14, 5, 18, 19, 1, 1, paint);
        break;
      case "next":
        paint.setStyle(Paint.Style.FILL);
        line(c, 5, 5, 16, 12, 5, 19, 5, 5);
        c.drawRect(18, 5, 20, 19, paint);
        break;
      case "prev":
        paint.setStyle(Paint.Style.FILL);
        line(c, 19, 5, 8, 12, 19, 19, 19, 5);
        c.drawRect(4, 5, 6, 19, paint);
        break;
      case "search":
        c.drawCircle(10.5f, 10.5f, 6.5f, paint);
        line(c, 16, 16, 21, 21);
        break;
      case "home":
        line(c, 3, 10, 12, 3, 21, 10, 21, 21, 15, 21, 15, 14, 9, 14, 9, 21, 3, 21, 3, 10);
        break;
      case "library":
        c.drawRoundRect(3, 4, 8, 20, 1, 1, paint);
        c.drawRoundRect(11, 4, 16, 20, 1, 1, paint);
        line(c, 19, 5, 22, 20);
        break;
      case "playlist":
        line(c, 3, 6, 15, 6);
        line(c, 3, 11, 15, 11);
        line(c, 3, 16, 10, 16);
        line(c, 18, 9, 18, 19);
        c.drawCircle(15.5f, 19, 2.5f, paint);
        break;
      case "settings":
        c.drawCircle(12, 12, 4, paint);
        for (int i = 0; i < 8; i++) {
          double a = i * Math.PI / 4;
          line(
              c,
              (float) (12 + 8 * Math.cos(a)),
              (float) (12 + 8 * Math.sin(a)),
              (float) (12 + 10 * Math.cos(a)),
              (float) (12 + 10 * Math.sin(a)));
        }
        c.drawCircle(12, 12, 8, paint);
        break;
      case "download":
        line(c, 12, 3, 12, 15);
        line(c, 7, 10, 12, 15, 17, 10);
        line(c, 4, 16, 4, 21, 20, 21, 20, 16);
        break;
      case "plus":
        line(c, 12, 5, 12, 19);
        line(c, 5, 12, 19, 12);
        break;
      case "more":
        paint.setStyle(Paint.Style.FILL);
        for (int y = 5; y <= 19; y += 7) c.drawCircle(12, y, 1.6f, paint);
        break;
      case "back":
        line(c, 15, 5, 8, 12, 15, 19);
        break;
      case "close":
        line(c, 6, 6, 18, 18);
        line(c, 6, 18, 18, 6);
        break;
      case "down":
        line(c, 5, 9, 12, 16, 19, 9);
        break;
      case "shuffle":
        line(c, 3, 5, 7, 5, 17, 19, 21, 19);
        line(c, 3, 19, 7, 19, 17, 5, 21, 5);
        line(c, 18, 2, 21, 5, 18, 8);
        line(c, 18, 16, 21, 19, 18, 22);
        break;
      case "repeat":
        line(c, 4, 9, 4, 6, 20, 6, 17, 3);
        line(c, 20, 15, 20, 18, 4, 18, 7, 21);
        break;
      case "heart":
      case "heartfill":
        {
          if (kind.equals("heartfill")) paint.setStyle(Paint.Style.FILL);
          path.reset();
          path.moveTo(12, 21);
          path.cubicTo(10, 19, 2, 13, 2, 8);
          path.cubicTo(2, 2, 9, 1, 12, 6);
          path.cubicTo(15, 1, 22, 2, 22, 8);
          path.cubicTo(22, 13, 14, 19, 12, 21);
          c.drawPath(path, paint);
          break;
        }
      case "lyrics":
        c.drawRoundRect(3, 3, 21, 19, 3, 3, paint);
        line(c, 7, 8, 17, 8);
        line(c, 7, 13, 14, 13);
        line(c, 9, 19, 6, 22, 6, 19);
        break;
      case "effects":
        line(c, 5, 3, 5, 21);
        line(c, 12, 3, 12, 21);
        line(c, 19, 3, 19, 21);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(5, 8, 3, paint);
        c.drawCircle(12, 16, 3, paint);
        c.drawCircle(19, 10, 3, paint);
        break;
      case "check":
        line(c, 4, 12, 9, 17, 20, 6);
        break;
      case "folder":
        line(c, 2, 6, 10, 6, 12, 9, 22, 9, 22, 20, 2, 20, 2, 6);
        break;
      default:
        line(c, 12, 3, 12, 17, 5, 17);
        c.drawCircle(5, 18, 3, paint);
        line(c, 12, 3, 20, 3);
    }
    c.restore();
  }
}
