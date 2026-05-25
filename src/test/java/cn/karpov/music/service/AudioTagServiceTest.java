package cn.karpov.music.service;

import static org.assertj.core.api.Assertions.assertThat;

import cn.karpov.music.service.AudioTagService.AudioMetadata;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AudioTagServiceTest {
    private final AudioTagService service = new AudioTagService();

    @Test
    void writesId3FramesToMp3Bytes() {
        byte[] audio = new byte[] {(byte) 0xff, (byte) 0xfb, 0x11, 0x22};

        byte[] tagged = service.writeTags(audio, new AudioMetadata("mp3", "Sunny", "Jay", "Album", "[00:01]Lyric"));

        assertThat(asLatin1(tagged)).contains("ID3", "TIT2", "TPE1", "TALB", "USLT");
        assertThat(tagged).endsWith(audio);
    }

    @Test
    void writesVorbisCommentsToFlacBytes() throws IOException {
        byte[] flac = minimalFlac();

        byte[] tagged = service.writeTags(flac, new AudioMetadata("flac", "Sunny", "Jay", "Album", "[00:01]Lyric"));

        assertThat(asUtf8(tagged)).contains("TITLE=Sunny", "ARTIST=Jay", "ALBUM=Album", "LYRICS=[00:01]Lyric");
        assertThat(tagged).startsWith(new byte[] {'f', 'L', 'a', 'C'});
    }

    @Test
    void leavesUnsupportedFormatsUnchanged() {
        byte[] audio = new byte[] {1, 2, 3};

        byte[] tagged = service.writeTags(audio, new AudioMetadata("m4a", "Sunny", "Jay", "Album", "Lyric"));

        assertThat(tagged).isSameAs(audio);
    }

    private byte[] minimalFlac() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(new byte[] {'f', 'L', 'a', 'C'});
        output.write(0);
        output.write(new byte[] {0, 0, 34});
        output.write(new byte[34]);
        output.write(0x80 | 1);
        output.write(new byte[] {0, 0, 0});
        output.write(new byte[] {0x12, 0x34});
        return output.toByteArray();
    }

    private String asLatin1(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private String asUtf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
