package org.booklore.service.metadata;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CoverImageGenerator {

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 1600;
    private static final int SQUARE_SIZE = 1200;
    private static final int SCALE = 2;
    private static final double PHI = 1.618033988749;
    private static final double PHI_INV = 1.0 / PHI;
    private static final double PHI_SQ_INV = 1.0 / (PHI * PHI);
    private static final int MAX_TITLE_LINES = 5;
    private static final int MAX_AUTHOR_LINES = 2;
    private static final int MAX_TITLE_LEN = 200;
    private static final int MAX_AUTHOR_LEN = 100;
    private static final int MIN_FONT = 36 * SCALE;
    private static final Pattern WS = Pattern.compile("\\s+");

    public byte[] generateCover(String title, String author) {
        return generateCover(title, author, null);
    }

    public byte[] generateCover(String title, String author, String subtitle) {
        BufferedImage render = null;
        BufferedImage result = null;
        Graphics2D g = null;

        try {
            String safeTitle = sanitize(title, MAX_TITLE_LEN, "Unknown Title");
            String safeAuthor = sanitize(author, MAX_AUTHOR_LEN, "Unknown Author");
            String safeSubtitle = sanitize(subtitle, MAX_TITLE_LEN, ""); // Using title length limit for subtitle

            int w = WIDTH * SCALE;
            int h = HEIGHT * SCALE;

            render = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            g = render.createGraphics();

            try {
                configureGraphics(g);
                Palette p = selectPalette(safeTitle);
                int margin = calcMargin(w);

                renderBaseGradient(g, p, w, h);
                renderDepthGradient(g, p, w, h);
                renderGlow(g, p, w, h);
                renderBokeh(g, p, w, h);
                renderTexture(g, w, h);
                renderFrame(g, p, w, h);
                int titleEnd = renderTitle(g, safeTitle, p, w, h, margin);
                int subtitleEnd = titleEnd;
                if (safeSubtitle != null && !safeSubtitle.isEmpty()) {
                    subtitleEnd = renderSubtitle(g, safeSubtitle, p, w, h, margin, titleEnd);
                }
                renderDivider(g, p, w, subtitleEnd, h);
                renderAuthor(g, safeAuthor, p, w, h, margin);
                renderVignette(g, w, h);
                renderEdges(g, w, h);

            } finally {
                if (g != null) g.dispose();
            }

            result = downscale(render);
            render.flush();
            render = null;

            return encodeJpeg(result);

        } catch (Exception e) {
            log.error("Cover generation failed: {}", title, e);
            throw new RuntimeException("Cover generation failed", e);
        } finally {
            cleanup(g, render, result);
        }
    }

    /**
     * Generates a square cover image suitable for audiobooks.
     */
    public byte[] generateSquareCover(String title, String author) {
        BufferedImage render = null;
        BufferedImage result = null;
        Graphics2D g = null;

        try {
            String safeTitle = sanitize(title, MAX_TITLE_LEN, "Unknown Title");
            String safeAuthor = sanitize(author, MAX_AUTHOR_LEN, "Unknown Author");

            int size = SQUARE_SIZE * SCALE;

            render = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            g = render.createGraphics();

            try {
                configureGraphics(g);
                Palette p = selectPalette(safeTitle);
                int margin = calcSquareMargin(size);

                renderBaseGradient(g, p, size, size);
                renderDepthGradient(g, p, size, size);
                renderGlow(g, p, size, size);
                renderBokeh(g, p, size, size);
                renderTexture(g, size, size);
                renderSquareFrame(g, p, size);
                renderSquareTitle(g, safeTitle, p, size, margin);
                renderSquareAuthor(g, safeAuthor, p, size, margin);
                renderVignette(g, size, size);
                renderEdges(g, size, size);

            } finally {
                if (g != null) g.dispose();
            }

            result = downscaleSquare(render);
            render.flush();
            render = null;

            return encodeJpeg(result);

        } catch (Exception e) {
            log.error("Square cover generation failed: {}", title, e);
            throw new RuntimeException("Square cover generation failed", e);
        } finally {
            cleanup(g, render, result);
        }
    }

    private record Palette(Color primary, Color secondary, Color tertiary, Color accent,
                           Color textMain, Color textSub, Color ornament) {}

    private int calcMargin(int w) {
        int frameOuter = (int) (w * PHI_SQ_INV * 0.12);
        int frameInner = (int) (frameOuter * PHI);
        return frameInner + (int) (w * 0.04);
    }

    private Palette selectPalette(String title) {
        Palette[] palettes = {
                new Palette(new Color(20, 30, 55), new Color(40, 55, 90), new Color(30, 42, 72),
                        new Color(218, 180, 120), new Color(252, 250, 245), new Color(210, 200, 185), new Color(190, 165, 110)),
                new Palette(new Color(70, 30, 40), new Color(105, 50, 60), new Color(85, 40, 50),
                        new Color(230, 205, 175), new Color(255, 252, 248), new Color(225, 215, 200), new Color(200, 175, 145)),
                new Palette(new Color(25, 50, 45), new Color(45, 80, 70), new Color(35, 65, 57),
                        new Color(205, 190, 150), new Color(250, 248, 242), new Color(215, 210, 195), new Color(180, 165, 130)),
                new Palette(new Color(45, 30, 65), new Color(75, 50, 100), new Color(60, 40, 82),
                        new Color(215, 195, 165), new Color(252, 250, 248), new Color(220, 210, 200), new Color(190, 170, 145)),
                new Palette(new Color(55, 45, 35), new Color(90, 75, 60), new Color(72, 60, 47),
                        new Color(210, 185, 145), new Color(255, 252, 245), new Color(230, 220, 200), new Color(185, 165, 125)),
                new Palette(new Color(25, 50, 60), new Color(45, 85, 100), new Color(35, 67, 80),
                        new Color(220, 200, 165), new Color(250, 250, 248), new Color(215, 210, 200), new Color(190, 175, 145)),
                new Palette(new Color(40, 40, 45), new Color(65, 67, 73), new Color(52, 52, 58),
                        new Color(205, 190, 160), new Color(252, 252, 250), new Color(205, 203, 200), new Color(180, 170, 150)),
                new Palette(new Color(65, 40, 30), new Color(105, 70, 50), new Color(85, 55, 40),
                        new Color(225, 200, 160), new Color(255, 250, 242), new Color(230, 220, 200), new Color(200, 180, 145)),
                new Palette(new Color(30, 40, 75), new Color(50, 65, 115), new Color(40, 52, 95),
                        new Color(220, 200, 155), new Color(252, 250, 248), new Color(210, 205, 195), new Color(190, 170, 135)),
                new Palette(new Color(50, 55, 40), new Color(80, 90, 65), new Color(65, 72, 52),
                        new Color(215, 205, 170), new Color(252, 250, 245), new Color(220, 215, 200), new Color(185, 180, 150))
        };
        return palettes[Math.abs(title.hashCode()) % palettes.length];
    }

    private static void renderBaseGradient(Graphics2D g, Palette p, int w, int h) {
        float[] fractions = {0f, (float) PHI_SQ_INV, 0.5f, (float) PHI_INV, 1f};
        Color[] colors = {darken(p.primary, 0.2f), p.primary, p.tertiary, p.secondary, darken(p.secondary, 0.15f)};
        g.setPaint(new LinearGradientPaint(0, 0, w * (float) PHI_INV, h, fractions, colors));
        g.fillRect(0, 0, w, h);
    }

    private void renderDepthGradient(Graphics2D g, Palette p, int w, int h) {
        g.setPaint(new RadialGradientPaint(w * 0.5f, h * 0.35f, (float) (h * PHI_INV),
                new float[]{0f, 0.5f, 1f},
                new Color[]{alpha(lighten(p.tertiary, 0.15f), 45), alpha(p.tertiary, 25), alpha(p.primary, 0)}));
        g.fillRect(0, 0, w, h);

        g.setPaint(new RadialGradientPaint(w * 0.85f, h * 1.15f, (float) (w * PHI_INV),
                new float[]{0f, 0.6f, 1f},
                new Color[]{alpha(darken(p.secondary, 0.25f), 55), alpha(darken(p.secondary, 0.15f), 30), alpha(p.secondary, 0)}));
        g.fillRect(0, 0, w, h);

        g.setPaint(new RadialGradientPaint(w * 0.1f, h * 0.9f, (float) (w * PHI_SQ_INV),
                new float[]{0f, 0.5f, 1f},
                new Color[]{alpha(darken(p.primary, 0.2f), 40), alpha(p.primary, 20), alpha(p.primary, 0)}));
        g.fillRect(0, 0, w, h);
    }

    private static void renderGlow(Graphics2D g, Palette p, int w, int h) {
        int cy = (int) (h * PHI_SQ_INV);
        float r = (float) (w * PHI_INV * 1.3);
        Color glow = new Color(
                Math.min(255, p.accent.getRed() + 55),
                Math.min(255, p.accent.getGreen() + 45),
                Math.min(255, p.accent.getBlue() + 35));

        g.setPaint(new RadialGradientPaint(w / 2f, cy, r,
                new float[]{0f, (float) PHI_SQ_INV, (float) PHI_INV, 1f},
                new Color[]{alpha(glow, 32), alpha(glow, 20), alpha(glow, 10), alpha(glow, 0)}));
        g.fillRect(0, 0, w, h);

        g.setPaint(new RadialGradientPaint(w / 2f, (float) (h * (1 - PHI_SQ_INV * 0.45)), r * 0.65f,
                new float[]{0f, 0.5f, 1f},
                new Color[]{alpha(Color.WHITE, 14), alpha(Color.WHITE, 7), alpha(Color.WHITE, 0)}));
        g.fillRect(0, 0, w, h);
    }

    private void renderBokeh(Graphics2D g, Palette p, int w, int h) {
        Composite orig = g.getComposite();
        int seed = p.primary.getRGB();

        for (int i = 0; i < 15; i++) {
            int px = ((seed * (i + 1) * 17) % w + w) % w;
            int py = ((seed * (i + 1) * 31) % h + h) % h;
            int size = Math.max(1, ((25 + Math.abs((seed * (i + 1) * 7) % 60)) * SCALE));
            float opacity = 0.018f + Math.abs((seed * (i + 1) * 3) % 25) * 0.0012f;

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            Color bokehColor = (i % 3 == 0) ? lighten(p.accent, 0.25f) : (i % 3 == 1) ? Color.WHITE : lighten(p.ornament, 0.2f);

            g.setPaint(new RadialGradientPaint(px, py, size,
                    new float[]{0f, 0.6f, 1f},
                    new Color[]{alpha(bokehColor, 90), alpha(bokehColor, 35), alpha(bokehColor, 0)}));
            g.fillOval(px - size, py - size, size * 2, size * 2);
        }

        g.setComposite(orig);
    }

    private static void renderTexture(Graphics2D g, int w, int h) {
        Composite orig = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.018f));

        for (int y = 0; y < h; y += 2) {
            for (int x = 0; x < w; x += 2) {
                int n = (x * 17 + y * 31) % 29;
                if (n < 10) {
                    g.setColor(n < 5 ? Color.WHITE : Color.BLACK);
                    g.fillRect(x, y, 1, 1);
                }
            }
        }

        g.setComposite(orig);
    }

    private void renderFrame(Graphics2D g, Palette p, int w, int h) {
        int outer = (int) (w * PHI_SQ_INV * 0.12);
        int inner = (int) (outer * PHI);

        g.setColor(alpha(p.ornament, 28));
        g.setStroke(new BasicStroke(10f));
        g.drawRect(outer - 4, outer - 4, w - (outer - 4) * 2, h - (outer - 4) * 2);

        g.setColor(alpha(p.ornament, 95));
        g.setStroke(new BasicStroke(2.5f));
        g.drawRect(outer, outer, w - outer * 2, h - outer * 2);

        g.setColor(alpha(p.ornament, 55));
        g.setStroke(new BasicStroke(1f));
        g.drawRect(inner, inner, w - inner * 2, h - inner * 2);

        renderCorners(g, p, w, h, outer);
        renderAccents(g, p, w, h, outer);
    }

    private static void renderCorners(Graphics2D g, Palette p, int w, int h, int m) {
        g.setColor(alpha(p.ornament, 105));
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int size = (int) (m * PHI);

        int[][] corners = {{m, m, 0}, {w - m, m, 90}, {w - m, h - m, 180}, {m, h - m, 270}};
        for (int[] c : corners) renderCorner(g, c[0], c[1], size, c[2]);
    }

    private static void renderCorner(Graphics2D g, int x, int y, int size, int angle) {
        AffineTransform saved = g.getTransform();
        g.translate(x, y);
        g.rotate(Math.toRadians(angle));

        Path2D path = new Path2D.Float();
        path.moveTo(0, size);
        path.lineTo(0, size * PHI_SQ_INV);
        path.quadTo(0, 0, size * PHI_SQ_INV, 0);
        path.lineTo(size, 0);
        g.draw(path);

        Path2D inner = new Path2D.Float();
        inner.moveTo(size * 0.15, size * PHI_INV);
        inner.quadTo(size * 0.15, size * 0.15, size * PHI_INV, size * 0.15);
        g.draw(inner);

        g.fillOval(-5, -5, 10, 10);
        g.fillOval((int) (size * 0.92), -4, 8, 8);
        g.fillOval(-4, (int) (size * 0.92), 8, 8);

        g.setTransform(saved);
    }

    private static void renderAccents(Graphics2D g, Palette p, int w, int h, int m) {
        g.setColor(alpha(p.ornament, 65));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int len = (int) (m * PHI);

        g.drawLine(w / 2 - len, m, w / 2 + len, m);
        g.fillOval(w / 2 - 5, m - 5, 10, 10);

        g.drawLine(w / 2 - len, h - m, w / 2 + len, h - m);
        g.fillOval(w / 2 - 5, h - m - 5, 10, 10);

        g.drawLine(m, h / 2 - len, m, h / 2 + len);
        g.fillOval(m - 5, h / 2 - 5, 10, 10);

        g.drawLine(w - m, h / 2 - len, w - m, h / 2 + len);
        g.fillOval(w - m - 5, h / 2 - 5, 10, 10);
    }

    private int renderTitle(Graphics2D g, String title, Palette p, int w, int h, int margin) {
        int maxW = w - margin * 2;
        int bottomBound = (int) (h * PHI_INV);

        String text = title.toUpperCase();
        Font font = resolveFont(g, text, maxW, titleSize(title.length()), MAX_TITLE_LINES, true);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();

        List<String> lines = wrapText(text, fm, maxW, MAX_TITLE_LINES);
        int lineH = (int) (fm.getHeight() * 1.12);
        int totalH = lines.size() * lineH;

        int availableH = bottomBound - margin - totalH;
        int startY = margin + (int) (availableH * PHI_SQ_INV) + fm.getAscent();
        startY = Math.max(startY, margin + fm.getAscent());

        float tracking = 0.05f;
        int lastY = startY;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lw = trackedWidth(fm, line, tracking);
            int x = (w - lw) / 2;
            int y = startY + i * lineH;
            if (y + fm.getDescent() > bottomBound) break;
            renderText(g, line, x, y, tracking, p.textMain, true);
            lastY = y;
        }

        return lastY + fm.getDescent() + lineH / 2;
    }

    private static void renderDivider(Graphics2D g, Palette p, int w, int titleEnd, int h) {
        int minY = titleEnd + (int) (h * 0.02);
        int maxY = (int) (h * (1 - PHI_SQ_INV * 0.7));
        int cy = minY + (int) ((maxY - minY) * PHI_SQ_INV);
        int cx = w / 2;
        int lineLen = (int) (w * PHI_SQ_INV * 0.38);

        for (int dir : new int[]{-1, 1}) {
            int sx = cx + dir * 50;
            int ex = cx + dir * (50 + lineLen);
            g.setPaint(new GradientPaint(sx, cy, alpha(p.ornament, 115), ex, cy, alpha(p.ornament, 25)));
            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(sx, cy, ex, cy);
        }

        g.setColor(alpha(p.ornament, 130));
        g.setStroke(new BasicStroke(2f));

        int ds = 14;
        Path2D diamond = new Path2D.Float();
        diamond.moveTo(cx, cy - ds);
        diamond.lineTo(cx + ds, cy);
        diamond.lineTo(cx, cy + ds);
        diamond.lineTo(cx - ds, cy);
        diamond.closePath();
        g.draw(diamond);

        int is = 6;
        Path2D inner = new Path2D.Float();
        inner.moveTo(cx, cy - is);
        inner.lineTo(cx + is, cy);
        inner.lineTo(cx, cy + is);
        inner.lineTo(cx - is, cy);
        inner.closePath();
        g.fill(inner);

        g.fillOval(cx - 50 - 5, cy - 5, 10, 10);
        g.fillOval(cx + 50 - 5, cy - 5, 10, 10);
        g.fillOval(cx - 32 - 4, cy - 4, 8, 8);
        g.fillOval(cx + 32 - 4, cy - 4, 8, 8);
    }

    private void renderAuthor(Graphics2D g, String author, Palette p, int w, int h, int margin) {
        int maxW = w - margin * 2;
        int bottomBound = h - margin;

        String[] authors = author.split(",");
        StringBuilder formattedAuthors = new StringBuilder();
        for (int i = 0; i < authors.length; i++) {
            if (i > 0) formattedAuthors.append("\n").append(authors[i].trim());
            else formattedAuthors.append(authors[i].trim());
        }

        Font authorFont = resolveFont(g, formattedAuthors.toString(), maxW, authorSize(formattedAuthors.length()), MAX_AUTHOR_LINES, false);
        g.setFont(authorFont);
        FontMetrics authorFm = g.getFontMetrics();

        String[] authorLinesArray = formattedAuthors.toString().split("\n");
        List<String> lines = new ArrayList<>();
        for (String line : authorLinesArray) {
            lines.add(line.trim());
        }

        int authorLineH = (int) (authorFm.getHeight() * 1.18);
        int authorTotalH = lines.size() * authorLineH;

        // Remove the "by" prefix for multiple authors
        boolean showByPrefix = lines.size() == 1 && author.length() < 45 && !author.contains(",");

        Font byFont = authorFont.deriveFont(authorFont.getSize() * 0.58f);
        FontMetrics byFm = g.getFontMetrics(byFont);
        String byText = "— by —";
        int byHeight = showByPrefix ? byFm.getHeight() : 0;
        int bySpacing = showByPrefix ? (int) (authorLineH * PHI_SQ_INV * 0.8) : 0;

        int totalBlockH = byHeight + bySpacing + authorTotalH;

        int blockBottom = (int) (h * (1 - PHI_SQ_INV * 0.32));
        blockBottom = Math.min(blockBottom, bottomBound);
        int blockTop = blockBottom - totalBlockH;

        int minTop = (int) (h * PHI_INV) + margin;
        if (blockTop < minTop) blockTop = minTop;

        int currentY = blockTop;

        if (showByPrefix) {
            g.setFont(byFont);
            int byW = byFm.stringWidth(byText);
            int byX = (w - byW) / 2;
            int byY = currentY + byFm.getAscent();

            g.setColor(new Color(0, 0, 0, 35));
            g.drawString(byText, byX + 3, byY + 3);

            g.setColor(alpha(p.ornament, 155));
            g.drawString(byText, byX, byY);

            currentY = byY + byFm.getDescent() + bySpacing;
        }

        g.setFont(authorFont);
        float tracking = 0.08f;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isEmpty()) {  // Only render non-empty lines
                int lw = trackedWidth(authorFm, line, tracking);
                int x = (w - lw) / 2;
                int y = currentY + authorFm.getAscent() + i * authorLineH;
                if (y + authorFm.getDescent() > bottomBound) break;
                renderText(g, line, x, y, tracking, p.textSub, false);
            }
        }
    }

    private int renderSubtitle(Graphics2D g, String subtitle, Palette p, int w, int h, int margin, int titleEnd) {
        int maxW = w - margin * 2;
        int bottomBound = h - margin;

        Font subtitleFont = resolveFont(g, subtitle, maxW, subtitleSize(subtitle.length()), 2, false);
        g.setFont(subtitleFont);
        FontMetrics subtitleFm = g.getFontMetrics();

        List<String> lines = wrapText(subtitle, subtitleFm, maxW, 2);
        int subtitleLineH = (int) (subtitleFm.getHeight() * 1.12);

        // Position subtitle below the title with some spacing
        int spacing = (int) (subtitleLineH * 0.5);
        int startY = titleEnd + spacing + subtitleFm.getAscent();

        g.setFont(subtitleFont);
        float tracking = 0.06f;

        int lastY = startY;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lw = trackedWidth(subtitleFm, line, tracking);
            int x = (w - lw) / 2;
            int y = startY + i * subtitleLineH;
            if (y + subtitleFm.getDescent() > bottomBound) break;
            renderText(g, line, x, y, tracking, p.textSub, false);
            lastY = y;
        }

        return lastY + subtitleFm.getDescent();
    }

    private int subtitleSize(int len) {
        if (len < 12) return 130 * SCALE;
        if (len < 22) return 110 * SCALE;
        if (len < 35) return 90 * SCALE;
        return 75 * SCALE;
    }

    private void renderText(Graphics2D g, String text, int x, int y, float tracking, Color color, boolean isTitle) {
        FontMetrics fm = g.getFontMetrics();
        int tp = (int) (fm.getHeight() * tracking);

        int[][] shadows = isTitle
                ? new int[][]{{8, 8, 14}, {5, 5, 28}, {2, 2, 45}}
                : new int[][]{{5, 5, 18}, {2, 2, 35}};

        for (int[] s : shadows) {
            g.setColor(new Color(0, 0, 0, s[2]));
            drawTracked(g, text, x + s[0], y + s[1], tp);
        }

        g.setColor(color);
        drawTracked(g, text, x, y, tp);

        if (isTitle) {
            g.setColor(alpha(Color.WHITE, 22));
            drawTracked(g, text, x, y - 1, tp);
        }
    }

    private void drawTracked(Graphics2D g, String text, int x, int y, int tracking) {
        FontMetrics fm = g.getFontMetrics();
        int cx = x;
        for (char c : text.toCharArray()) {
            g.drawString(String.valueOf(c), cx, y);
            cx += fm.charWidth(c) + tracking;
        }
    }

    private int trackedWidth(FontMetrics fm, String text, float tracking) {
        int tp = (int) (fm.getHeight() * tracking);
        int w = 0;
        for (char c : text.toCharArray()) w += fm.charWidth(c) + tp;
        return Math.max(0, w - tp);
    }

    private static void renderVignette(Graphics2D g, int w, int h) {
        float r = (float) (Math.sqrt(w * w + h * h) / 2 * 1.12);
        g.setPaint(new RadialGradientPaint(w / 2f, h / 2f, r,
                new float[]{0f, (float) PHI_INV, (float) (PHI_INV + PHI_SQ_INV * 0.25), 0.9f, 1f},
                new Color[]{alpha(Color.BLACK, 0), alpha(Color.BLACK, 0), alpha(Color.BLACK, 15),
                        alpha(Color.BLACK, 50), alpha(Color.BLACK, 85)}));
        g.fillRect(0, 0, w, h);

        g.setPaint(new GradientPaint(0, 0, alpha(Color.WHITE, 14), 0, (int) (h * PHI_SQ_INV), alpha(Color.WHITE, 0)));
        g.fillRect(0, 0, w, (int) (h * PHI_SQ_INV));
    }

    private static void renderEdges(Graphics2D g, int w, int h) {
        g.setColor(alpha(Color.BLACK, 50));
        g.setStroke(new BasicStroke(3f));
        g.drawRect(0, 0, w - 1, h - 1);

        g.setColor(alpha(Color.WHITE, 10));
        g.drawRect(3, 3, w - 7, h - 7);
    }

    private static String sanitize(String input, int max, String fallback) {
        if (input == null || input.isBlank()) return fallback;
        String s = input.trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private void configureGraphics(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.min(255, Math.max(0, a)));
    }

    private static Color darken(Color c, float f) {
        return new Color(Math.max(0, (int) (c.getRed() * (1 - f))),
                Math.max(0, (int) (c.getGreen() * (1 - f))),
                Math.max(0, (int) (c.getBlue() * (1 - f))));
    }

    private Color lighten(Color c, float f) {
        return new Color(Math.min(255, (int) (c.getRed() + (255 - c.getRed()) * f)),
                Math.min(255, (int) (c.getGreen() + (255 - c.getGreen()) * f)),
                Math.min(255, (int) (c.getBlue() + (255 - c.getBlue()) * f)));
    }

    private Font resolveFont(Graphics2D g, String text, int maxW, int startSize, int maxLines, boolean isTitle) {
        String[] serif = {"Palatino Linotype", "Garamond", "Georgia", "Book Antiqua"};
        String[] sans = {"Optima", "Gill Sans", "Helvetica Neue", "Calibri"};
        String[] preferred = isTitle ? serif : sans;
        String fallback = isTitle ? Font.SERIF : Font.SANS_SERIF;
        int style = isTitle ? Font.BOLD : Font.PLAIN;

        String selected = fallback;
        for (String name : preferred) {
            Font test = new Font(name, style, startSize);
            if (test.getFamily().equalsIgnoreCase(name)) {
                selected = name;
                break;
            }
        }

        float tracking = isTitle ? 0.05f : 0.08f;

        for (int size = startSize; size >= MIN_FONT; size -= 2) {
            Font font = new Font(selected, style, size);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            List<String> lines = wrapText(text, fm, maxW, maxLines);
            if (lines.stream().allMatch(l -> trackedWidth(fm, l, tracking) <= maxW)) return font;
        }

        return new Font(selected, style, MIN_FONT);
    }

    private List<String> wrapText(String text, FontMetrics fm, int maxW, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();

        for (String word : WS.split(text.trim())) {
            if (fm.stringWidth(word) > maxW) {
                if (!cur.isEmpty()) {
                    lines.add(cur.toString().trim());
                    cur = new StringBuilder();
                }
                lines.addAll(breakWord(word, fm, maxW));
                continue;
            }

            if (cur.isEmpty()) {
                cur.append(word);
            } else if (fm.stringWidth(cur + " " + word) <= maxW) {
                cur.append(" ").append(word);
            } else {
                lines.add(cur.toString().trim());
                cur = new StringBuilder(word);
            }
        }

        if (!cur.isEmpty()) lines.add(cur.toString().trim());

        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(0, maxLines));
            String last = lines.get(maxLines - 1);
            while (!last.isEmpty() && fm.stringWidth(last + "…") > maxW)
                last = last.substring(0, last.length() - 1).trim();
            lines.set(maxLines - 1, last + "…");
        }

        return lines;
    }

    private List<String> breakWord(String word, FontMetrics fm, int maxW) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();

        for (char c : word.toCharArray()) {
            if (fm.stringWidth(cur.toString() + c) > maxW && !cur.isEmpty()) {
                parts.add(cur.toString());
                cur = new StringBuilder();
            }
            cur.append(c);
        }
        if (!cur.isEmpty()) parts.add(cur.toString());

        return parts;
    }

    private int titleSize(int len) {
        if (len < 8) return 150 * SCALE;
        if (len < 15) return 130 * SCALE;
        if (len < 25) return 110 * SCALE;
        if (len < 40) return 92 * SCALE;
        if (len < 60) return 78 * SCALE;
        if (len < 85) return 66 * SCALE;
        return 56 * SCALE;
    }

    private int authorSize(int len) {
        if (len < 12) return 78 * SCALE;
        if (len < 22) return 68 * SCALE;
        if (len < 35) return 58 * SCALE;
        return 50 * SCALE;
    }

    private BufferedImage downscale(BufferedImage src) {
        BufferedImage dst = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, WIDTH, HEIGHT, null);
            return dst;
        } finally {
            g.dispose();
        }
    }

    private BufferedImage downscaleSquare(BufferedImage src) {
        BufferedImage dst = new BufferedImage(SQUARE_SIZE, SQUARE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, SQUARE_SIZE, SQUARE_SIZE, null);
            return dst;
        } finally {
            g.dispose();
        }
    }

    private int calcSquareMargin(int size) {
        int frameOuter = (int) (size * PHI_SQ_INV * 0.10);
        int frameInner = (int) (frameOuter * PHI);
        return frameInner + (int) (size * 0.04);
    }

    private void renderSquareFrame(Graphics2D g, Palette p, int size) {
        int outer = (int) (size * PHI_SQ_INV * 0.10);
        int inner = (int) (outer * PHI);

        g.setColor(alpha(p.ornament, 28));
        g.setStroke(new BasicStroke(8f));
        g.drawRect(outer - 4, outer - 4, size - (outer - 4) * 2, size - (outer - 4) * 2);

        g.setColor(alpha(p.ornament, 95));
        g.setStroke(new BasicStroke(2.5f));
        g.drawRect(outer, outer, size - outer * 2, size - outer * 2);

        g.setColor(alpha(p.ornament, 55));
        g.setStroke(new BasicStroke(1f));
        g.drawRect(inner, inner, size - inner * 2, size - inner * 2);

        renderSquareCorners(g, p, size, outer);
    }

    private static void renderSquareCorners(Graphics2D g, Palette p, int size, int m) {
        g.setColor(alpha(p.ornament, 105));
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cornerSize = (int) (m * PHI * 0.8);

        int[][] corners = {{m, m, 0}, {size - m, m, 90}, {size - m, size - m, 180}, {m, size - m, 270}};
        for (int[] c : corners) renderCorner(g, c[0], c[1], cornerSize, c[2]);
    }

    private void renderSquareTitle(Graphics2D g, String title, Palette p, int size, int margin) {
        int maxW = size - margin * 2;

        String text = title.toUpperCase();
        Font font = resolveFont(g, text, maxW, squareTitleSize(title.length()), 3, true);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();

        List<String> lines = wrapText(text, fm, maxW, 3);
        int lineH = (int) (fm.getHeight() * 1.12);
        int totalH = lines.size() * lineH;

        // Center title vertically in top portion
        int topZone = (int) (size * 0.55);
        int startY = margin + (topZone - margin - totalH) / 2 + fm.getAscent();
        startY = Math.max(startY, margin + fm.getAscent());

        float tracking = 0.05f;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lw = trackedWidth(fm, line, tracking);
            int x = (size - lw) / 2;
            int y = startY + i * lineH;
            if (y + fm.getDescent() > topZone) break;
            renderText(g, line, x, y, tracking, p.textMain, true);
        }
    }

    private void renderSquareAuthor(Graphics2D g, String author, Palette p, int size, int margin) {
        int maxW = size - margin * 2;

        Font authorFont = resolveFont(g, author, maxW, squareAuthorSize(author.length()), 2, false);
        g.setFont(authorFont);
        FontMetrics authorFm = g.getFontMetrics();

        List<String> lines = wrapText(author, authorFm, maxW, 2);
        int authorLineH = (int) (authorFm.getHeight() * 1.15);
        int totalH = lines.size() * authorLineH;

        // Position author in bottom portion
        int bottomStart = (int) (size * 0.65);
        int bottomEnd = size - margin;
        int availableH = bottomEnd - bottomStart - totalH;
        int startY = bottomStart + availableH / 2 + authorFm.getAscent();

        float tracking = 0.08f;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isEmpty()) {
                int lw = trackedWidth(authorFm, line, tracking);
                int x = (size - lw) / 2;
                int y = startY + i * authorLineH;
                if (y + authorFm.getDescent() > bottomEnd) break;
                renderText(g, line, x, y, tracking, p.textSub, false);
            }
        }
    }

    private int squareTitleSize(int len) {
        if (len < 8) return 130 * SCALE;
        if (len < 15) return 110 * SCALE;
        if (len < 25) return 95 * SCALE;
        if (len < 40) return 80 * SCALE;
        if (len < 60) return 68 * SCALE;
        return 58 * SCALE;
    }

    private int squareAuthorSize(int len) {
        if (len < 15) return 70 * SCALE;
        if (len < 25) return 60 * SCALE;
        if (len < 40) return 52 * SCALE;
        return 45 * SCALE;
    }

    private byte[] encodeJpeg(BufferedImage img) {
        ImageWriter writer = null;
        ImageOutputStream ios = null;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.95f);

            ios = ImageIO.createImageOutputStream(baos);
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("JPEG encoding failed", e);
        } finally {
            if (writer != null) writer.dispose();
            if (ios != null) try { ios.close(); } catch (IOException ignored) {}
        }
    }

    private void cleanup(Graphics2D g, BufferedImage... images) {
        if (g != null) try { g.dispose(); } catch (Exception ignored) {}
        for (BufferedImage img : images)
            if (img != null) try { img.flush(); } catch (Exception ignored) {}
    }
}
