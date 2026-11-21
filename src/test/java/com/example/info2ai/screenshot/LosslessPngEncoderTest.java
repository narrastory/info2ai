package com.example.info2ai.screenshot;

import org.junit.Test;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import static org.junit.Assert.*;

public class LosslessPngEncoderTest {
    @Test public void preservesChannelsTransparentColorsAndTopToBottomRows() throws Exception {
        int[] abgr = {0xff0000ff, 0x8000ff00, 0x00ff0000, 0x01332211, 0xfecdab89, 0x7f563412};
        int[] argb = {0xffff0000, 0x8000ff00, 0x000000ff, 0x01112233, 0xfe89abcd, 0x7f123456};
        var decoded = ImageIO.read(new ByteArrayInputStream(LosslessPngEncoder.encode(2, 3, abgr)));
        assertNotNull(decoded);
        assertEquals(2, decoded.getWidth());
        assertEquals(3, decoded.getHeight());
        assertArrayEquals(argb, decoded.getRGB(0, 0, 2, 3, null, 0, 2));
    }

    @Test public void roundTripsOddAndScreenshotSizesWithoutDroppingPixels() throws Exception {
        int[] abgr = {0x00123456, 0xff0102fe, 0x7f987654, 0x01abcdef};
        int[] argb = {0x00563412, 0xfffe0201, 0x7f547698, 0x01efcdab};
        for (int[] size : new int[][]{{1, 1}, {1, 9}, {7, 1}, {3, 5}, {480, 270}, {960, 540}, {1920, 1080}}) {
            int width = size[0], height = size[1];
            int[] pixels = new int[width * height];
            int[] expected = new int[pixels.length];
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = abgr[i % abgr.length];
                expected[i] = argb[i % argb.length];
            }
            byte[] png = LosslessPngEncoder.encode(width, height, pixels);
            var decoded = ImageIO.read(new ByteArrayInputStream(png));
            assertEquals(width, decoded.getWidth());
            assertEquals(height, decoded.getHeight());
            assertArrayEquals(expected, decoded.getRGB(0, 0, width, height, null, 0, width));
        }
    }

    @Test public void writesValidChunksCrcAndBoundedIncompressiblePayload() throws Exception {
        int width = 257, height = 263;
        int[] pixels = new int[width * height];
        var random = new Random(741);
        for (int i = 0; i < pixels.length; i++) pixels[i] = random.nextInt();
        byte[] png = LosslessPngEncoder.encode(width, height, pixels);
        assertTrue(png.length < (long) width * height * 4 + height + 4096);
        try (var input = new DataInputStream(new ByteArrayInputStream(png))) {
            assertEquals(0x89504e470d0a1a0aL, input.readLong());
            int chunks = 0, dataChunks = 0;
            String previous = "";
            while (input.available() > 0) {
                int length = input.readInt();
                assertTrue(length >= 0 && length <= 65536);
                byte[] type = input.readNBytes(4);
                byte[] data = input.readNBytes(length);
                String name = new String(type, StandardCharsets.US_ASCII);
                var crc = new CRC32(); crc.update(type); crc.update(data);
                assertEquals(crc.getValue(), Integer.toUnsignedLong(input.readInt()));
                if (chunks == 0) {
                    assertEquals("IHDR", name);
                    assertArrayEquals(new byte[]{0, 0, 1, 1, 0, 0, 1, 7, 8, 6, 0, 0, 0}, data);
                } else if (name.equals("IDAT")) {
                    assertTrue(previous.equals("IHDR") || previous.equals("IDAT"));
                    dataChunks++;
                } else {
                    assertEquals("IEND", name);
                    assertEquals(0, length);
                    assertEquals(0, input.available());
                }
                previous = name; chunks++;
            }
            assertEquals("IEND", previous);
            assertTrue(dataChunks > 1);
        }
        var decoded = ImageIO.read(new ByteArrayInputStream(png));
        for (int i = 0; i < pixels.length; i++) {
            int value = pixels[i];
            int expected = (value & 0xff00ff00) | ((value & 0xff) << 16) | ((value >>> 16) & 0xff);
            assertEquals(expected, decoded.getRGB(i % width, i / width));
        }
    }

    @Test public void completedPayloadDoesNotAliasPixelsOrLaterEncoding() throws Exception {
        int[] pixels = {0x7f563412};
        byte[] first = LosslessPngEncoder.encode(1, 1, pixels);
        byte[] saved = first.clone();
        pixels[0] = 0xffffffff;
        byte[] second = LosslessPngEncoder.encode(1, 1, pixels);
        second[0] = 0;
        assertArrayEquals(saved, first);
        assertEquals(0x7f123456, ImageIO.read(new ByteArrayInputStream(first)).getRGB(0, 0));
    }

    @Test public void rejectsInvalidOrOversizedDimensionsBeforePixelAccess() {
        for (int[] size : new int[][]{{0, 1}, {1, 0}, {-1, 2}, {Integer.MAX_VALUE, 2}, {4096, 4097}}) {
            assertThrows(IllegalArgumentException.class, () -> LosslessPngEncoder.validateDimensions(size[0], size[1]));
        }
        LosslessPngEncoder.validateDimensions(3840, 4320);
        assertThrows(IllegalArgumentException.class, () -> LosslessPngEncoder.encode(2, 2, new int[3]));
        assertThrows(IllegalArgumentException.class, () -> LosslessPngEncoder.encode(1, 1, new int[2]));
    }

    @Test public void releasesCompressorAfterSuccess() throws Exception {
        var compressor = new TrackingDeflater(null);
        byte[] png = LosslessPngEncoder.encode(1, 1, new int[]{0xff563412}, compressor);
        assertEquals(0xff123456, ImageIO.read(new ByteArrayInputStream(png)).getRGB(0, 0));
        assertEquals(1, compressor.ends);
    }

    @Test public void encodeFailureKeepsOriginalCauseAndReleasesCompressor() {
        var cause = new IllegalStateException("encode failure");
        var compressor = new TrackingDeflater(cause);
        assertSame(cause, assertThrows(IllegalStateException.class,
            () -> LosslessPngEncoder.encode(1, 1, new int[]{0xff563412}, compressor)));
        assertEquals(1, compressor.ends);
        assertThrows(NullPointerException.class, compressor::getBytesRead);
    }

    private static final class TrackingDeflater extends Deflater {
        private final RuntimeException failure;
        private int ends;
        TrackingDeflater(RuntimeException failure) { super(Deflater.BEST_SPEED); this.failure = failure; }
        @Override public int deflate(byte[] output, int offset, int length) {
            if (failure != null) throw failure;
            return super.deflate(output, offset, length);
        }
        @Override public void end() { ends++; super.end(); }
    }
}
