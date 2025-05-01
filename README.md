# image-translate

> JavaFX desktop app — select an image, get it back with all text translated in-place.

OCR (Tesseract) extracts text with bounding boxes → proximity clustering groups words into logical blocks → LibreTranslate translates each block → translated text is rendered back onto the image at the original location, with adaptive background color matching.

---

## Supported Use Cases

| Use case | Support |
|---|---|
| Documents and articles (light background, dark text) | ✅ |
| Photos with text (signs, menus, labels) | ✅ |
| Dark-background screenshots | ⚠️ partial — auto-inversion helps, complex layouts may vary |
| UI screenshots with icons | ❌ icons are misread as characters by Tesseract |

---

## How It Works

```
┌─────────────────────────────────────────────────────────┐
│                  JavaFX Desktop UI                       │
│  Load image → select source/target language → Translate  │
└───────────────────┬─────────────────────────────────────┘
                    │
         ┌──────────▼──────────┐
         │   TranslateController│
         │                      │
         │  prepareForOcr():    │
         │  · grayscale + 2× upscale                       │
         │  · if avg brightness < 128 → invert image       │
         │    (dark UI → light text on dark = bad for OCR, │
         │     invert = dark text on light = good for OCR) │
         └──────────┬──────────┘
                    │
         ┌──────────▼──────────┐
         │  Tesseract LSTM OCR  │
         │  → List<Word> with   │
         │    bounding boxes    │
         └──────────┬──────────┘
                    │
         ┌──────────▼──────────┐
         │  WordClusteringService                          │
         │                      │
         │  Proximity clustering algorithm:                │
         │  · Expand each word bbox by 20px padding        │
         │  · Intersects any word in cluster → merge       │
         │  · One bounding rect per cluster                │
         │  · Sort words: top→bottom, left→right           │
         └──────────┬──────────┘
                    │
         ┌──────────▼──────────┐
         │  LibreTranslate API  │
         │  POST /translate     │
         │  (self-hosted,       │
         │   no key needed)     │
         └──────────┬──────────┘
                    │
         ┌──────────▼──────────┐
         │  ImageTextRewriter   │
         │  · Sample avg bg color of each block            │
         │  · Fill block with sampled color                │
         │  · Pick contrast text color (WCAG luminance)    │
         │  · Auto-scale font to fit block dimensions      │
         │  · Word-wrap + centre-align                     │
         │  · Save as originalName_TRANSLATE.png           │
         └─────────────────────┘
```

---

## Tech Stack

| Layer | Technologies |
|---|---|
| UI | Java 17, JavaFX 21, FXML |
| OCR | Tesseract 5 (via Tess4J), LSTM engine |
| Translation | LibreTranslate REST API (self-hosted) |
| HTTP | Java built-in `HttpClient` + Jackson |
| Image processing | Java AWT / `BufferedImage` |
| Configuration | `app.properties` |
| Tests | JUnit 5 (13 unit tests) |
| Build | Maven 3 |

---

## Quick Start

### Prerequisites
- Java 17+, Maven 3
- [LibreTranslate](https://github.com/LibreTranslate/LibreTranslate) running locally:

```bash
docker run -p 5000:5000 libretranslate/libretranslate --load-only en,ru
```

Wait ~2 min for language models to download on first run.

### Run

```bash
git clone https://github.com/rezzoq/image-translate.git
cd image-translate
mvn javafx:run
```

### Configuration

Edit `src/main/resources/app.properties` to change paths or LibreTranslate URL:

```properties
# Path to Tesseract language data (bundled by default)
tessdata.path=src/main/resources/tessdata

# LibreTranslate instance URL
libretranslate.url=http://localhost:5000/translate
```

---

## Project Structure

```
image-translate/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/
    │   │   ├── module-info.java
    │   │   └── translate/image_translate/
    │   │       ├── TranslateApp.java              # JavaFX entry point
    │   │       ├── TranslateController.java        # UI logic + OCR preprocessing
    │   │       ├── WordClusteringService.java      # proximity clustering algorithm
    │   │       └── ImageTextRewriter.java          # text rendering + color detection
    │   └── resources/
    │       ├── app.properties                      # configuration
    │       ├── tessdata/                           # bundled OCR language data
    │       │   ├── eng.traineddata
    │       │   └── rus.traineddata
    │       └── translate/image_translate/
    │           └── translate.fxml                  # UI layout
    └── test/java/translate/image_translate/
        └── WordClusteringServiceTest.java          # 13 unit tests
```

---

## Supported Languages

| Tesseract code | Language |
|---|---|
| `eng` | English |
| `rus` | Russian |
| `deu` | German |
| `fra` | French |
| `spa` | Spanish |
| `ita` | Italian |
| `por` | Portuguese |
| `chi_sim` | Chinese (Simplified) |

To add more languages: download the `.traineddata` file from [tesseract-ocr/tessdata](https://github.com/tesseract-ocr/tessdata), drop it into `src/main/resources/tessdata/`, add the code to the ChoiceBox in `TranslateController.java`, and add the LibreTranslate mapping in `toLibreCode()`.

---

## Key Design Decisions

**Why proximity clustering instead of Tesseract's built-in block detection?**
Tesseract's block segmentation works well for clean documents but struggles with varied layouts — posters, screenshots, mixed-font pages. The custom `WordClusteringService` groups words purely by spatial proximity (20px padding threshold), which is layout-agnostic and testable independently of the OCR engine.

**Why adaptive background color sampling?**
Images have varied backgrounds — white documents, dark UIs, coloured posters. Sampling the average color of each text region and computing a WCAG-contrast text color means translated text is always readable regardless of the original image's color scheme.

**Why auto-invert dark images before OCR?**
Tesseract's LSTM model was trained on dark text on light backgrounds. When average image brightness falls below 128, the preprocessor inverts the image so Tesseract sees the text correctly — improving recognition accuracy on dark-mode screenshots and night photos without any manual configuration.

**Why self-hosted LibreTranslate?**
No API key, no usage limits, no data sent to third-party servers. The translation runs fully offline once the language models are downloaded via Docker.

**Why `app.properties` instead of hardcoded paths?**
The tessdata path differs between development (`src/main/resources/tessdata`) and system Tesseract installations (`/usr/share/tessdata` on Linux). Externalising configuration lets users switch without recompiling.
