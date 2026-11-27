package guessarena;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Draws the original Guess Arena logo with Java2D: a target with a question mark. */
public final class Logo {
    private Logo() { }

    public static BufferedImage create(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(size / 100.0, size / 100.0);
        g.setPaint(new GradientPaint(0, 0, new Color(0x5B3FD6), 100, 100, new Color(0x1B1446)));
        g.fill(new RoundRectangle2D.Double(0, 0, 100, 100, 26, 26));
        g.setStroke(new BasicStroke(6f));
        g.setColor(new Color(0xFFC83D));
        g.draw(new Ellipse2D.Double(14, 14, 72, 72));
        g.setColor(Color.WHITE);
        // The question mark is drawn with shapes, so it looks the same on every PC.
        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D q = new Path2D.Double();
        q.moveTo(38, 40);
        q.curveTo(38, 25, 63, 25, 63, 40);
        q.curveTo(63, 49, 50, 48, 50, 60);
        g.draw(q);
        g.fill(new Ellipse2D.Double(45, 68, 10, 10));
        g.dispose();
        return image;
    }

    public static List<Image> icons() {
        List<Image> list = new ArrayList<>();
        for (int s : new int[] {16, 32, 48, 64, 128, 256}) list.add(create(s));
        return list;
    }
}
