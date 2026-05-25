package cn.karpov.music.staticui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class StaticFrontendTest {
    private static final Path FRONTEND_DIR = Path.of("frontend");

    @Test
    void indexKeepsDownloaderDomContract() throws IOException {
        String html = Files.readString(FRONTEND_DIR.resolve("index.html"), StandardCharsets.UTF_8);

        assertThat(html).contains("type=\"module\"", "/src/main.js");
        assertThat(html).contains("id=\"searchForm\"");
        assertThat(html).contains("id=\"platform\"");
        assertThat(html).contains("id=\"platformChoices\"");
        assertThat(html).contains("id=\"keyword\"");
        assertThat(html).contains("id=\"resultList\"");
        assertThat(html).contains("id=\"detailView\"");
        assertThat(html).contains("id=\"downloadInfoBtn\"");
        assertThat(html).contains("id=\"downloadBtn\"");
        assertThat(html).doesNotContain("UNSEEN", "Selected Projects", "Enter without audio");
    }

    @Test
    void appKeepsBackendApiRoutes() throws IOException {
        String script = Files.readString(FRONTEND_DIR.resolve("src/main.js"), StandardCharsets.UTF_8);
        List<String> routes = List.of(
                "/api/platforms",
                "/api/search?platform=",
                "/api/song/${encodeURIComponent(platform)}/${id}",
                "/api/song/${encodeURIComponent(platform)}/${id}/lyric",
                "/api/song/${encodeURIComponent(platform)}/${id}/download-info?quality=",
                "/api/song/${encodeURIComponent(platform)}/${id}/download?quality="
        );

        assertThat(script).contains(routes);
    }

    @Test
    void appRendersDownloadFileFormat() throws IOException {
        String script = Files.readString(FRONTEND_DIR.resolve("src/main.js"), StandardCharsets.UTF_8);

        assertThat(script).contains("fileFormat", "data.format || qualityLabel(data.quality)");
    }

    @Test
    void stylesUseNativeCursorForDownloaderControls() throws IOException {
        String styles = Files.readString(FRONTEND_DIR.resolve("src/styles.css"), StandardCharsets.UTF_8);

        assertThat(styles).doesNotContain("cursor: none");
        assertThat(styles).contains("cursor: pointer", "cursor: text");
    }

    @Test
    void viteProjectDefinesProxyAndScripts() throws IOException {
        String packageJson = Files.readString(FRONTEND_DIR.resolve("package.json"), StandardCharsets.UTF_8);
        String viteConfig = Files.readString(FRONTEND_DIR.resolve("vite.config.js"), StandardCharsets.UTF_8);

        assertThat(packageJson).contains("\"dev\": \"vite\"", "\"build\": \"vite build\"", "\"preview\": \"vite preview\"");
        assertThat(viteConfig).contains("\"/api\"", "target: \"http://localhost:8080\"");
    }
}
