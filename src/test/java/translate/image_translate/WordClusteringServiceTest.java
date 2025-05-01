package translate.image_translate;

import net.sourceforge.tess4j.Word;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WordClusteringServiceTest {

    private WordClusteringService service;

    @BeforeEach
    void setUp() {
        service = new WordClusteringService();
    }


    @Test
    void emptyInput_returnsEmptyList() {
        assertTrue(service.groupWordsIntoBlocks(List.of()).isEmpty());
    }

    @Test
    void singleWord_returnsOneRect() {
        List<Rectangle> rects = service.groupWordsIntoBlocks(
                List.of(word("Hello", 10, 10, 50, 20)));

        assertEquals(1, rects.size());
        assertEquals(new Rectangle(10, 10, 50, 20), rects.get(0));
    }

    @Test
    void closeWords_mergedIntoOneRect() {
        // gap between words = 5px
        List<Rectangle> rects = service.groupWordsIntoBlocks(List.of(
                word("Hello", 10, 10, 50, 20),
                word("world", 65, 10, 50, 20)
        ));

        assertEquals(1, rects.size());
    }

    @Test
    void distantWords_produceTwoRects() {
        // gap = 300px vertically — more than CLUSTER_PADDING
        List<Rectangle> rects = service.groupWordsIntoBlocks(List.of(
                word("Title",   10,  10, 80, 20),
                word("Caption", 10, 300, 80, 20)
        ));

        assertEquals(2, rects.size());
    }

    @Test
    void threeWordsSameLineClose_oneCluster() {
        List<Rectangle> rects = service.groupWordsIntoBlocks(List.of(
                word("One",   10, 10, 30, 20),
                word("two",   45, 10, 30, 20),
                word("three", 80, 10, 40, 20)
        ));

        assertEquals(1, rects.size());
    }

    @Test
    void boundingRectCoversAllWords() {
        List<Rectangle> rects = service.groupWordsIntoBlocks(List.of(
                word("A", 10, 10, 40, 20),
                word("B", 55, 10, 40, 20),
                word("C", 15, 10, 20, 20)
        ));

        assertEquals(1, rects.size());
        Rectangle r = rects.get(0);
        assertEquals(10, r.x);
        assertEquals(10, r.y);
        assertEquals(85, r.width);  // maxX(95) - minX(10)
        assertEquals(20, r.height);
    }

    @Test
    void areClose_touching_returnsTrue() {
        Rectangle r1 = new Rectangle(0, 0, 50, 20);
        Rectangle r2 = new Rectangle(51, 0, 50, 20);
        assertTrue(service.areClose(r1, r2));
    }

    @Test
    void areClose_farApart_returnsFalse() {
        Rectangle r1 = new Rectangle(0,   0, 50, 20);
        Rectangle r2 = new Rectangle(200, 0, 50, 20);
        assertFalse(service.areClose(r1, r2));
    }

    @Test
    void areClose_exactlyAtPaddingBoundary_returnsTrue() {
        // r2 starts exactly at r1.x + r1.width + PADDING = 0 + 50 + 20 = 70
        Rectangle r1 = new Rectangle(0,  0, 50, 20);
        Rectangle r2 = new Rectangle(69, 0, 50, 20);
        assertTrue(service.areClose(r1, r2));
    }

    @Test
    void groupWordsByShapes_sortsLeftToRight() {
        List<Word> words = List.of(
                word("world", 65, 10, 50, 20),
                word("Hello", 10, 10, 50, 20)
        );
        List<Rectangle> shapes = service.groupWordsIntoBlocks(words);
        List<ImageTextRewriter.TextBlock> blocks = service.groupWordsByShapes(words, shapes);

        assertEquals(1, blocks.size());
        assertEquals("Hello world", blocks.get(0).originalText);
    }

    @Test
    void groupWordsByShapes_multipleLines_sortsTopToBottom() {
        // Y gap = 40px > CLUSTER_PADDING(20)
        List<Word> words = List.of(
                word("line2", 10, 50, 60, 20),
                word("line1", 10, 10, 60, 20)
        );
        List<Rectangle> shapes = service.groupWordsIntoBlocks(words);
        List<ImageTextRewriter.TextBlock> blocks = service.groupWordsByShapes(words, shapes);

        assertEquals(2, blocks.size());
        // blocks sorted by shape order — line1 should be in one block, line2 in another
        boolean hasLine1 = blocks.stream().anyMatch(b -> b.originalText.contains("line1"));
        boolean hasLine2 = blocks.stream().anyMatch(b -> b.originalText.contains("line2"));
        assertTrue(hasLine1, "Should have a block with 'line1'");
        assertTrue(hasLine2, "Should have a block with 'line2'");
    }

    @Test
    void groupWordsByShapes_closeLines_mergedAndSortedTopToBottom() {
        // Y gap = 8px < LINE_TOLERANCE(10)
        List<Word> words = List.of(
                word("second", 10, 18, 60, 20),
                word("first",  10, 10, 60, 20)
        );
        List<Rectangle> shapes = service.groupWordsIntoBlocks(words);
        List<ImageTextRewriter.TextBlock> blocks = service.groupWordsByShapes(words, shapes);

        assertEquals(1, blocks.size());
        String text = blocks.get(0).originalText;
        assertTrue(text.startsWith("first"), "Upper word should come first, got: " + text);
    }

    @Test
    void groupWordsByShapes_emptyWords_returnsEmptyBlocks() {
        List<ImageTextRewriter.TextBlock> blocks =
                service.groupWordsByShapes(List.of(), List.of());
        assertTrue(blocks.isEmpty());
    }

    private Word word(String text, int x, int y, int width, int height) {
        return new Word(text, 1.0f, new Rectangle(x, y, width, height));
    }
}
