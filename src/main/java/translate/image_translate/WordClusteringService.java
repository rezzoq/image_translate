package translate.image_translate;

import net.sourceforge.tess4j.Word;

import java.awt.Rectangle;
import java.util.*;

public class WordClusteringService {

    static final int CLUSTER_PADDING = 20;
    static final int LINE_TOLERANCE  = 10;

    /**
     * Pass 1: cluster words by proximity, return one bounding Rectangle per cluster.
     */
    public List<Rectangle> groupWordsIntoBlocks(List<Word> words) {
        List<Set<Word>> clusters = new ArrayList<>();

        for (Word word : words) {
            Rectangle rect  = word.getBoundingBox();
            boolean   added = false;

            outer:
            for (Set<Word> cluster : clusters) {
                for (Word clustered : cluster) {
                    if (areClose(rect, clustered.getBoundingBox())) {
                        cluster.add(word);
                        added = true;
                        break outer;
                    }
                }
            }

            if (!added) {
                Set<Word> newCluster = new HashSet<>();
                newCluster.add(word);
                clusters.add(newCluster);
            }
        }

        List<Rectangle> result = new ArrayList<>();
        for (Set<Word> cluster : clusters) {
            result.add(boundingRect(new ArrayList<>(cluster)));
        }
        return result;
    }

    /**
     * Pass 2: assign words to cluster rects, sort reading-order, assemble text.
     * Returns list of TextBlocks with original text (translated field left empty).
     */
    public List<ImageTextRewriter.TextBlock> groupWordsByShapes(
            List<Word> words, List<Rectangle> shapes) {

        List<ImageTextRewriter.TextBlock> blocks = new ArrayList<>();

        for (Rectangle shape : shapes) {
            List<Word> inside = new ArrayList<>(words.stream()
                    .filter(w -> shape.intersects(w.getBoundingBox()))
                    .toList());

            inside.sort(Comparator
                    .comparingInt((Word w) -> w.getBoundingBox().y)
                    .thenComparingInt(w -> w.getBoundingBox().x));

            List<List<Word>> lines = new ArrayList<>();
            for (Word word : inside) {
                int     wordY  = word.getBoundingBox().y;
                boolean placed = false;

                for (List<Word> line : lines) {
                    if (Math.abs(wordY - line.get(0).getBoundingBox().y) < LINE_TOLERANCE) {
                        line.add(word);
                        placed = true;
                        break;
                    }
                }
                if (!placed) {
                    List<Word> newLine = new ArrayList<>();
                    newLine.add(word);
                    lines.add(newLine);
                }
            }

            StringBuilder text = new StringBuilder();
            for (List<Word> line : lines) {
                line.sort(Comparator.comparingInt(w -> w.getBoundingBox().x));
                for (Word w : line) text.append(w.getText()).append(" ");
            }

            if (!inside.isEmpty()) {
                blocks.add(new ImageTextRewriter.TextBlock(
                        text.toString().trim(), "",
                        shape.x, shape.y, shape.width, shape.height));
            }
        }
        return blocks;
    }

    boolean areClose(Rectangle r1, Rectangle r2) {
        Rectangle expanded = new Rectangle(
                r1.x      - CLUSTER_PADDING,
                r1.y      - CLUSTER_PADDING,
                r1.width  + 2 * CLUSTER_PADDING,
                r1.height + 2 * CLUSTER_PADDING);
        return expanded.intersects(r2);
    }

    Rectangle boundingRect(List<Word> words) {
        int minX = words.stream().mapToInt(w -> w.getBoundingBox().x).min().orElse(0);
        int minY = words.stream().mapToInt(w -> w.getBoundingBox().y).min().orElse(0);
        int maxX = words.stream().mapToInt(w -> w.getBoundingBox().x + w.getBoundingBox().width).max().orElse(0);
        int maxY = words.stream().mapToInt(w -> w.getBoundingBox().y + w.getBoundingBox().height).max().orElse(0);
        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }
}
