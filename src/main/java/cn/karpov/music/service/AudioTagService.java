package cn.karpov.music.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AudioTagService {
    private static final byte[] ID3 = new byte[] {'I', 'D', '3'};
    private static final byte[] FLAC = new byte[] {'f', 'L', 'a', 'C'};

    public byte[] writeTags(byte[] audio, AudioMetadata metadata) {
        if (audio == null || audio.length == 0 || metadata == null || metadata.isEmpty()) {
            return audio;
        }
        String extension = metadata.extension() == null ? "" : metadata.extension().trim().toLowerCase(Locale.ROOT);
        try {
            return switch (extension) {
                case "mp3" -> writeMp3Tags(audio, metadata);
                case "flac" -> writeFlacTags(audio, metadata);
                default -> audio;
            };
        } catch (Exception ex) {
            return audio;
        }
    }

    private byte[] writeMp3Tags(byte[] audio, AudioMetadata metadata) throws IOException {
        List<byte[]> frames = new ArrayList<>();
        addFrame(frames, textFrame("TIT2", metadata.title()));
        addFrame(frames, textFrame("TPE1", metadata.artist()));
        addFrame(frames, textFrame("TALB", metadata.album()));
        addFrame(frames, lyricsFrame(metadata.lyrics()));
        if (frames.isEmpty()) {
            return audio;
        }

        ByteArrayOutputStream frameBody = new ByteArrayOutputStream();
        for (byte[] frame : frames) {
            frameBody.write(frame);
        }

        byte[] framesBytes = frameBody.toByteArray();
        ByteArrayOutputStream tagged = new ByteArrayOutputStream(10 + framesBytes.length + audio.length);
        tagged.write(ID3);
        tagged.write(3);
        tagged.write(0);
        tagged.write(0);
        tagged.write(syncSafeSize(framesBytes.length));
        tagged.write(framesBytes);

        int audioStart = existingId3Length(audio);
        tagged.write(audio, audioStart, audio.length - audioStart);
        return tagged.toByteArray();
    }

    private byte[] textFrame(String frameId, String value) throws IOException {
        if (isBlank(value)) {
            return null;
        }
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(1);
        payload.write(value.trim().getBytes(StandardCharsets.UTF_16));
        return id3Frame(frameId, payload.toByteArray());
    }

    private byte[] lyricsFrame(String lyrics) throws IOException {
        if (isBlank(lyrics)) {
            return null;
        }
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(1);
        payload.write("und".getBytes(StandardCharsets.ISO_8859_1));
        payload.write(0);
        payload.write(0);
        payload.write(lyrics.trim().getBytes(StandardCharsets.UTF_16));
        return id3Frame("USLT", payload.toByteArray());
    }

    private byte[] id3Frame(String frameId, byte[] payload) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream(10 + payload.length);
        frame.write(frameId.getBytes(StandardCharsets.ISO_8859_1));
        frame.write(int32(payload.length));
        frame.write(0);
        frame.write(0);
        frame.write(payload);
        return frame.toByteArray();
    }

    private void addFrame(List<byte[]> frames, byte[] frame) {
        if (frame != null && frame.length > 0) {
            frames.add(frame);
        }
    }

    private int existingId3Length(byte[] audio) {
        if (audio.length < 10 || audio[0] != ID3[0] || audio[1] != ID3[1] || audio[2] != ID3[2]) {
            return 0;
        }
        int size = syncSafeToInt(audio, 6);
        int total = 10 + size;
        if ((audio[5] & 0x10) != 0) {
            total += 10;
        }
        return total <= audio.length ? total : 0;
    }

    private byte[] writeFlacTags(byte[] audio, AudioMetadata metadata) throws IOException {
        int marker = flacMarkerOffset(audio);
        if (marker < 0) {
            return audio;
        }
        int position = marker + FLAC.length;
        List<FlacBlock> blocks = new ArrayList<>();
        while (position + 4 <= audio.length) {
            int header = audio[position] & 0xff;
            boolean last = (header & 0x80) != 0;
            int type = header & 0x7f;
            int length = uint24(audio, position + 1);
            int dataStart = position + 4;
            int dataEnd = dataStart + length;
            if (dataEnd < dataStart || dataEnd > audio.length) {
                return audio;
            }
            blocks.add(new FlacBlock(type, Arrays.copyOfRange(audio, dataStart, dataEnd)));
            position = dataEnd;
            if (last) {
                break;
            }
        }
        if (blocks.isEmpty() || position > audio.length) {
            return audio;
        }

        int vorbisIndex = findVorbisComment(blocks);
        FlacBlock commentBlock = vorbisIndex >= 0 ? blocks.get(vorbisIndex) : null;
        VorbisComment comment = commentBlock == null ? new VorbisComment("MusicDownloader", new LinkedHashMap<>()) : parseVorbis(commentBlock.data());
        applyComment(comment.comments(), "TITLE", metadata.title());
        applyComment(comment.comments(), "ARTIST", metadata.artist());
        applyComment(comment.comments(), "ALBUM", metadata.album());
        applyComment(comment.comments(), "LYRICS", metadata.lyrics());
        byte[] commentData = buildVorbis(comment);
        if (commentData.length > 0xFF_FF_FF) {
            return audio;
        }

        FlacBlock newBlock = new FlacBlock(4, commentData);
        if (vorbisIndex >= 0) {
            blocks.set(vorbisIndex, newBlock);
        } else {
            int insertIndex = blocks.isEmpty() ? 0 : 1;
            blocks.add(Math.min(insertIndex, blocks.size()), newBlock);
        }

        ByteArrayOutputStream tagged = new ByteArrayOutputStream(audio.length + commentData.length + 64);
        tagged.write(audio, 0, marker + FLAC.length);
        for (int index = 0; index < blocks.size(); index++) {
            FlacBlock block = blocks.get(index);
            if (block.data().length > 0xFF_FF_FF) {
                return audio;
            }
            int header = block.type() | (index == blocks.size() - 1 ? 0x80 : 0);
            tagged.write(header);
            tagged.write(uint24(block.data().length));
            tagged.write(block.data());
        }
        tagged.write(audio, position, audio.length - position);
        return tagged.toByteArray();
    }

    private int flacMarkerOffset(byte[] audio) {
        if (startsWith(audio, 0, FLAC)) {
            return 0;
        }
        int id3Length = existingId3Length(audio);
        if (id3Length > 0 && startsWith(audio, id3Length, FLAC)) {
            return id3Length;
        }
        int limit = Math.min(audio.length - FLAC.length, 8192);
        for (int index = 0; index <= limit; index++) {
            if (startsWith(audio, index, FLAC)) {
                return index;
            }
        }
        return -1;
    }

    private boolean startsWith(byte[] source, int offset, byte[] prefix) {
        if (offset < 0 || offset + prefix.length > source.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (source[offset + index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private int findVorbisComment(List<FlacBlock> blocks) {
        for (int index = 0; index < blocks.size(); index++) {
            if (blocks.get(index).type() == 4) {
                return index;
            }
        }
        return -1;
    }

    private VorbisComment parseVorbis(byte[] data) {
        int position = 0;
        try {
            int vendorLength = littleInt(data, position);
            position += 4;
            String vendor = utf8(data, position, vendorLength);
            position += vendorLength;
            int count = littleInt(data, position);
            position += 4;
            LinkedHashMap<String, String> comments = new LinkedHashMap<>();
            for (int index = 0; index < count && position + 4 <= data.length; index++) {
                int length = littleInt(data, position);
                position += 4;
                String value = utf8(data, position, length);
                position += length;
                int equals = value.indexOf('=');
                if (equals > 0) {
                    comments.put(value.substring(0, equals).toUpperCase(Locale.ROOT), value.substring(equals + 1));
                }
            }
            return new VorbisComment(vendor.isBlank() ? "MusicDownloader" : vendor, comments);
        } catch (Exception ex) {
            return new VorbisComment("MusicDownloader", new LinkedHashMap<>());
        }
    }

    private byte[] buildVorbis(VorbisComment comment) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] vendor = comment.vendor().getBytes(StandardCharsets.UTF_8);
        output.write(littleInt(vendor.length));
        output.write(vendor);
        output.write(littleInt(comment.comments().size()));
        for (Map.Entry<String, String> entry : comment.comments().entrySet()) {
            byte[] bytes = (entry.getKey() + "=" + entry.getValue()).getBytes(StandardCharsets.UTF_8);
            output.write(littleInt(bytes.length));
            output.write(bytes);
        }
        return output.toByteArray();
    }

    private void applyComment(Map<String, String> comments, String key, String value) {
        if (!isBlank(value)) {
            comments.put(key, value.trim());
        }
    }

    private int littleInt(byte[] data, int offset) {
        if (offset + 4 > data.length) {
            throw new IllegalArgumentException("Not enough bytes for little-endian integer.");
        }
        return (data[offset] & 0xff)
                | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16)
                | ((data[offset + 3] & 0xff) << 24);
    }

    private byte[] littleInt(int value) {
        return new byte[] {
                (byte) value,
                (byte) (value >>> 8),
                (byte) (value >>> 16),
                (byte) (value >>> 24)
        };
    }

    private String utf8(byte[] data, int offset, int length) {
        if (length < 0 || offset < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid UTF-8 slice.");
        }
        return new String(data, offset, length, StandardCharsets.UTF_8);
    }

    private byte[] int32(int value) {
        return new byte[] {
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
    }

    private int uint24(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 16)
                | ((data[offset + 1] & 0xff) << 8)
                | (data[offset + 2] & 0xff);
    }

    private byte[] uint24(int value) {
        return new byte[] {
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
    }

    private int syncSafeToInt(byte[] data, int offset) {
        return ((data[offset] & 0x7f) << 21)
                | ((data[offset + 1] & 0x7f) << 14)
                | ((data[offset + 2] & 0x7f) << 7)
                | (data[offset + 3] & 0x7f);
    }

    private byte[] syncSafeSize(int value) {
        return new byte[] {
                (byte) ((value >>> 21) & 0x7f),
                (byte) ((value >>> 14) & 0x7f),
                (byte) ((value >>> 7) & 0x7f),
                (byte) (value & 0x7f)
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record AudioMetadata(String extension, String title, String artist, String album, String lyrics) {
        private boolean isEmpty() {
            return isBlank(title) && isBlank(artist) && isBlank(album) && isBlank(lyrics);
        }

        private boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }

    private record FlacBlock(int type, byte[] data) {
    }

    private record VorbisComment(String vendor, LinkedHashMap<String, String> comments) {
    }
}
