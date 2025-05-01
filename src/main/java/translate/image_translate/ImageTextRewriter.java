package translate.image_translate;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ImageTextRewriter {

    static class TextBlock {
        String originalText;
        String translatedText;
        int x, y, width, height;

        public TextBlock(String originalText, String translatedText, int x, int y, int width, int height) {
            this.originalText   = originalText;
            this.translatedText = translatedText;
            this.x = x; this.y = y; this.width = width; this.height = height;
        }
    }

    public static void drawTextBlocksDebug(String inputImagePath, BufferedImage image, List<Rectangle> blocks)
            throws IOException {
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.setStroke(new BasicStroke(2));
        for (Rectangle block : blocks) g.drawRect(block.x, block.y, block.width, block.height);
        g.dispose();
        ImageIO.write(image, "png", new File(inputImagePath + "_DEBUG.png"));
    }

    public static void drawTranslatedText(String inputImagePath, BufferedImage image, List<TextBlock> blocks)
            throws IOException {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("Arial", Font.PLAIN, 16));

        for (TextBlock block : blocks) {
            Color bgColor   = sampleBackgroundColor(image, block.x, block.y, block.width, block.height);
            Color textColor = contrastColor(bgColor);

            g.setColor(bgColor);
            g.fillRect(block.x, block.y, block.width, block.height);

            g.setColor(textColor);
            drawMultilineText(g, block.translatedText, block.x, block.y, block.width, block.height);
        }

        g.dispose();
        ImageIO.write(image, "png", new File(inputImagePath + "_TRANSLATE.png"));
        System.out.println("Saved: " + inputImagePath + "_TRANSLATE.png");
    }

    private static Color sampleBackgroundColor(BufferedImage img, int x, int y, int w, int h) {
        int x1 = Math.max(0, x);
        int y1 = Math.max(0, y);
        int x2 = Math.min(img.getWidth()  - 1, x + w);
        int y2 = Math.min(img.getHeight() - 1, y + h);

        int stepX = Math.max(1, (x2 - x1) / 10);
        int stepY = Math.max(1, (y2 - y1) / 10);

        long r = 0, g = 0, b = 0, count = 0;
        for (int px = x1; px < x2; px += stepX) {
            for (int py = y1; py < y2; py += stepY) {
                Color c = new Color(img.getRGB(px, py));
                r += c.getRed();
                g += c.getGreen();
                b += c.getBlue();
                count++;
            }
        }
        if (count == 0) return Color.WHITE;
        return new Color((int)(r / count), (int)(g / count), (int)(b / count));
    }

    private static Color contrastColor(Color bg) {
        double luminance = 0.2126 * linearize(bg.getRed())
                         + 0.7152 * linearize(bg.getGreen())
                         + 0.0722 * linearize(bg.getBlue());
        return luminance > 0.179 ? Color.BLACK : Color.WHITE;
    }

    private static double linearize(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    // ── text layout ───────────────────────────────────────────────────────────

    private static void drawMultilineText(Graphics2D g, String text, int x, int y, int width, int height) {
        int fontSize = getMaxFittingFontSizeForMultiline(g, text, width, height);
        g.setFont(g.getFont().deriveFont((float) fontSize));

        FontMetrics  metrics    = g.getFontMetrics();
        List<String> lines      = wrapText(text, metrics, width);
        int          lineHeight = metrics.getHeight();
        int          totalH     = lineHeight * lines.size();
        int          startY     = y + (height - totalH) / 2 + metrics.getAscent();

        for (int i = 0; i < lines.size(); i++) {
            String line   = lines.get(i);
            int    lineW  = metrics.stringWidth(line);
            int    startX = x + (width - lineW) / 2;
            g.drawString(line, startX, startY + i * lineHeight);
        }
    }

    private static int getMaxFittingFontSizeForMultiline(Graphics2D g, String text, int width, int height) {
        Font baseFont = g.getFont();
        for (int fontSize = 100; fontSize > 1; fontSize--) {
            FontMetrics  metrics = g.getFontMetrics(baseFont.deriveFont((float) fontSize));
            List<String> lines   = wrapText(text, metrics, width);
            if (metrics.getHeight() * lines.size() <= height) return fontSize;
        }
        return 1;
    }

    private static List<String> wrapText(String text, FontMetrics metrics, int maxWidth) {
        List<String>  lines = new ArrayList<>();
        String[]      words = text.split(" ");
        StringBuilder line  = new StringBuilder();

        for (String word : words) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (metrics.stringWidth(test) > maxWidth) {
                if (!line.isEmpty()) lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private static int getTextWidth(Graphics2D g, String text) {
        FontMetrics metrics = g.getFontMetrics();
        return text.length() == 2 ? Math.max(metrics.stringWidth(text), 20) : metrics.stringWidth(text);
    }
}
