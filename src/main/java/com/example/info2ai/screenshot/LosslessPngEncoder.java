package com.example.info2ai.screenshot;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/** 同步编码从上到下的 ABGR 像素；固定 Sub 过滤和快速 deflate，不改变像素。 */
public final class LosslessPngEncoder {
    private static final int MAX_PIXELS = 16 * 1024 * 1024;
    private static final int CHUNK_BYTES = 64 * 1024;
    private static final byte[] IHDR = "IHDR".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] IDAT = "IDAT".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] IEND = "IEND".getBytes(StandardCharsets.US_ASCII);

    private LosslessPngEncoder() {}

    /** 在 native 像素副本/缩放目标分配前检查，乘法使用 long 避免溢出。 */
    public static void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
            throw new IllegalArgumentException("PNG dimensions exceed the pixel limit");
        }
    }

    /** 输入只在调用期间借用且不修改；返回值不持有输入或压缩器的引用。 */
    public static byte[] encode(int width, int height, int[] abgr) throws IOException {
        validateDimensions(width, height);
        return encode(width, height, abgr, new Deflater(Deflater.BEST_SPEED));
    }

    /** 此层接管压缩器所有权，包括校验或编码失败时的释放。 */
    static byte[] encode(int width, int height, int[] abgr, Deflater compressor) throws IOException {
        try {
            validateDimensions(width, height);
            Objects.requireNonNull(abgr);
            if (abgr.length != width * height) throw new IllegalArgumentException("PNG pixel count mismatch");
            byte[] row = new byte[width * 4 + 1];
            byte[] compressed = new byte[CHUNK_BYTES];
            // 不构造完整过滤后图像；内存中只保留一行、一个 IDAT 块和最终输出。
            try (var bytes = new ByteArrayOutputStream(CHUNK_BYTES);
                 var output = new DataOutputStream(bytes)) {
                output.writeLong(0x89504e470d0a1a0aL);
                byte[] header = ByteBuffer.allocate(13).putInt(width).putInt(height)
                    .put((byte) 8).put((byte) 6).put((byte) 0).put((byte) 0).put((byte) 0).array();
                writeChunk(output, IHDR, header, header.length);
                int used = 0;
                for (int y = 0; y < height; y++) {
                    row[0] = 1;
                    int previous = 0;
                    for (int x = 0; x < width; x++) {
                        int pixel = abgr[y * width + x];
                        int offset = 1 + x * 4;
                        row[offset] = (byte) (pixel - previous);
                        row[offset + 1] = (byte) ((pixel >>> 8) - (previous >>> 8));
                        row[offset + 2] = (byte) ((pixel >>> 16) - (previous >>> 16));
                        row[offset + 3] = (byte) ((pixel >>> 24) - (previous >>> 24));
                        previous = pixel;
                    }
                    compressor.setInput(row);
                    while (!compressor.needsInput()) {
                        used += compressor.deflate(compressed, used, compressed.length - used);
                        if (used == compressed.length) {
                            writeChunk(output, IDAT, compressed, used);
                            used = 0;
                        }
                    }
                }
                compressor.finish();
                while (!compressor.finished()) {
                    used += compressor.deflate(compressed, used, compressed.length - used);
                    if (used == compressed.length) {
                        writeChunk(output, IDAT, compressed, used);
                        used = 0;
                    }
                }
                if (used != 0) writeChunk(output, IDAT, compressed, used);
                writeChunk(output, IEND, compressed, 0);
                return bytes.toByteArray();
            }
        } finally {
            compressor.end();
        }
    }

    private static void writeChunk(DataOutputStream output, byte[] type, byte[] data, int length) throws IOException {
        var crc = new CRC32();
        crc.update(type);
        crc.update(data, 0, length);
        output.writeInt(length);
        output.write(type);
        output.write(data, 0, length);
        output.writeInt((int) crc.getValue());
    }
}
