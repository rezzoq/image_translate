package translate.image_translate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

import javafx.scene.image.Image;

public class TranslateController {

    private static final String TESSDATA_PATH;
    private static final String LIBRE_TRANSLATE_URL;

    static {
        Properties props = new Properties();
        try (InputStream in = TranslateController.class
                .getResourceAsStream("/app.properties")) {
            if (in != null) props.load(in);
        } catch (IOException e) {
            System.err.println("Could not load app.properties: " + e.getMessage());
        }
        TESSDATA_PATH       = props.getProperty("tessdata.path",      "src/main/resources/tessdata");
        LIBRE_TRANSLATE_URL = props.getProperty("libretranslate.url", "http://localhost:5000/translate");
    }

    private static final HttpClient           HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper         MAPPER      = new ObjectMapper();
    private final        WordClusteringService clustering  = new WordClusteringService();

    @FXML Button            btnLoad;
    @FXML Button            btnTranslate;
    @FXML ChoiceBox<String> comboBoxPicLanguage;
    @FXML ChoiceBox<String> comboBoxResultLanguage;
    @FXML ImageView         imgOpen;
    @FXML javafx.scene.control.Label previewLabel;

    String start  = "en";
    String result = "en";
    private Path       path;
    private List<Word> words  = new ArrayList<>();
    private List<ImageTextRewriter.TextBlock> blocks = new ArrayList<>();

    @FXML
    public void initialize() {
        setBtnLoad();
        setCombobox();
        setBtnTranslate();
    }

    private void setBtnTranslate() {
        btnTranslate.setOnAction(event -> {
            try {
                BufferedImage img = processImage(prepareForOcr(
                        ImageIO.read(new File(path.toString()))));
                extractWords(img);

                List<Rectangle> shapes = clustering.groupWordsIntoBlocks(words);

                blocks = clustering.groupWordsByShapes(words, shapes).stream()
                        .map(b -> new ImageTextRewriter.TextBlock(
                                b.originalText,
                                translateText(b.originalText, start, result),
                                b.x, b.y, b.width, b.height))
                        .toList();

                ImageTextRewriter.drawTranslatedText(path.toString(), img, blocks);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void setCombobox() {
        comboBoxPicLanguage.getItems().addAll("eng", "rus", "deu", "fra", "spa", "ita", "por", "chi_sim");
        comboBoxResultLanguage.getItems().addAll("eng", "rus", "deu", "fra", "spa", "ita", "por", "chi_sim");
        comboBoxPicLanguage.getSelectionModel().select(0);
        comboBoxResultLanguage.getSelectionModel().select(0);

        comboBoxResultLanguage.setOnAction(e ->
                result = toLibreCode(comboBoxResultLanguage.getSelectionModel().getSelectedItem()));
        comboBoxPicLanguage.setOnAction(e ->
                start = toLibreCode(comboBoxPicLanguage.getSelectionModel().getSelectedItem()));
    }

    private String toLibreCode(String tesseractCode) {
        return switch (tesseractCode.toLowerCase()) {
            case "eng"     -> "en";
            case "rus"     -> "ru";
            case "deu"     -> "de";
            case "fra"     -> "fr";
            case "spa"     -> "es";
            case "ita"     -> "it";
            case "por"     -> "pt";
            case "chi_sim" -> "zh";
            default -> tesseractCode.substring(0, Math.min(2, tesseractCode.length()));
        };
    }

    private void setBtnLoad() {
        btnLoad.setOnAction(event -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Open image");
            fc.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.png"));
            fc.setInitialDirectory(new File("."));

            File selected = fc.showOpenDialog((Stage) btnLoad.getScene().getWindow());
            if (selected == null) return;

            path = Path.of(selected.getPath());
            btnTranslate.setDisable(false);

            try {
                imgOpen.setImage(new Image(new FileInputStream(selected)));
                imgOpen.setPreserveRatio(true);
                previewLabel.setVisible(false);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public BufferedImage processImage(BufferedImage image) {
        BufferedImage gray = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = gray.getGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return gray;
    }

    public static BufferedImage prepareForOcr(BufferedImage original) {
        int w = original.getWidth()  * 2;
        int h = original.getHeight() * 2;
        java.awt.Image tmp = original.getScaledInstance(w, h, java.awt.Image.SCALE_REPLICATE);
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = scaled.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();

        // if image is dark (light text on dark bg) — invert it.
        if (averageBrightness(scaled) < 128) {
            invertImage(scaled);
        }

        return scaled;
    }

    private static double averageBrightness(BufferedImage img) {
        long sum   = 0;
        int  count = 0;
        int  step  = 4;
        for (int x = 0; x < img.getWidth(); x += step) {
            for (int y = 0; y < img.getHeight(); y += step) {
                sum += new Color(img.getRGB(x, y)).getRed(); // grayscale: R=G=B
                count++;
            }
        }
        return count > 0 ? (double) sum / count : 128;
    }

    private static void invertImage(BufferedImage img) {
        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                Color c = new Color(img.getRGB(x, y));
                img.setRGB(x, y, new Color(255 - c.getRed(), 255 - c.getGreen(), 255 - c.getBlue()).getRGB());
            }
        }
    }

    private void extractWords(BufferedImage image) throws Exception {
        Tesseract tess = new Tesseract();
        tess.setDatapath(TESSDATA_PATH);
        tess.setPageSegMode(6);
        tess.setOcrEngineMode(1);
        tess.setLanguage(comboBoxPicLanguage.getSelectionModel().getSelectedItem());
        words = tess.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD);
    }

    public String translateText(String text, String sourceLang, String targetLang) {
        try {
            String body = MAPPER.writeValueAsString(Map.of(
                    "q",      text,
                    "source", sourceLang,
                    "target", targetLang,
                    "format", "text"
            ));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(LIBRE_TRANSLATE_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            JsonNode json = MAPPER.readTree(
                    HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString()).body());
            return json.path("translatedText").asText(text);
        } catch (Exception e) {
            System.err.println("Translation failed: " + e.getMessage());
            return text;
        }
    }
}
