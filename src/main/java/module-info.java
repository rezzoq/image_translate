module translate.image_translate {
    requires javafx.controls;
    requires javafx.fxml;
    requires tess4j;
    requires java.desktop;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;

    opens translate.image_translate to javafx.fxml;
    exports translate.image_translate;
}
