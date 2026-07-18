package com.example.mindmap.service.asr;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Locale;

final class AudioFileNormalizer {
    private static final long TIMEOUT_US = 10_000L;

    private AudioFileNormalizer() {
    }

    static DecodedAudioFile prepareForDoubao(File inputFile) throws IOException {
        String extension = extensionOf(inputFile);
        if ("wav".equals(extension) || "mp3".equals(extension) || "ogg".equals(extension)) {
            return new DecodedAudioFile(inputFile, extension, false);
        }
        File wavFile = File.createTempFile("doubao_asr_", ".wav", inputFile.getParentFile());
        decodeToMonoWav(inputFile, wavFile);
        return new DecodedAudioFile(wavFile, "wav", true);
    }

    private static void decodeToMonoWav(File inputFile, File wavFile) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        RandomAccessFile output = null;
        int sampleRate = 16_000;
        int channelCount = 1;
        long pcmBytesWritten = 0L;
        try {
            extractor.setDataSource(inputFile.getAbsolutePath());
            int trackIndex = selectAudioTrack(extractor);
            if (trackIndex < 0) {
                throw new IOException("The media file does not contain an audio track.");
            }
            extractor.selectTrack(trackIndex);
            MediaFormat format = extractor.getTrackFormat(trackIndex);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null) {
                throw new IOException("Unable to identify the audio codec.");
            }
            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            }
            if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            }

            output = new RandomAccessFile(wavFile, "rw");
            output.setLength(0L);
            writeWavHeader(output, sampleRate, 1, 0L);

            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            while (!outputDone) {
                if (!inputDone) {
                    int inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US);
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputIndex);
                        if (inputBuffer == null) {
                            throw new IOException("Unable to read decoder input buffer.");
                        }
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                int outputIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US);
                if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = decoder.getOutputBuffer(outputIndex);
                    if (outputBuffer != null && info.size > 0) {
                        outputBuffer.position(info.offset);
                        outputBuffer.limit(info.offset + info.size);
                        byte[] monoPcm = toMonoPcm(outputBuffer.slice(), channelCount);
                        output.write(monoPcm);
                        pcmBytesWritten += monoPcm.length;
                    }
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    decoder.releaseOutputBuffer(outputIndex, false);
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outputFormat = decoder.getOutputFormat();
                    if (outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    }
                }
            }

            output.seek(0L);
            writeWavHeader(output, sampleRate, 1, pcmBytesWritten);
        } finally {
            if (output != null) {
                output.close();
            }
            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }
            extractor.release();
        }
    }

    private static int selectAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }

    private static byte[] toMonoPcm(ByteBuffer pcmBuffer, int channelCount) {
        pcmBuffer.order(ByteOrder.LITTLE_ENDIAN);
        if (channelCount <= 1) {
            byte[] mono = new byte[pcmBuffer.remaining()];
            pcmBuffer.get(mono);
            return mono;
        }

        ShortBuffer samples = pcmBuffer.asShortBuffer();
        int frameCount = samples.remaining() / channelCount;
        ByteBuffer mono = ByteBuffer.allocate(frameCount * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int frame = 0; frame < frameCount; frame++) {
            int mixed = 0;
            for (int channel = 0; channel < channelCount; channel++) {
                mixed += samples.get();
            }
            mono.putShort((short) (mixed / channelCount));
        }
        return mono.array();
    }

    private static void writeWavHeader(RandomAccessFile output, int sampleRate, int channelCount, long pcmDataLength)
            throws IOException {
        int bitsPerSample = 16;
        long byteRate = (long) sampleRate * channelCount * bitsPerSample / 8;
        int blockAlign = channelCount * bitsPerSample / 8;
        output.writeBytes("RIFF");
        writeLittleEndianInt(output, 36L + pcmDataLength);
        output.writeBytes("WAVE");
        output.writeBytes("fmt ");
        writeLittleEndianInt(output, 16);
        writeLittleEndianShort(output, 1);
        writeLittleEndianShort(output, channelCount);
        writeLittleEndianInt(output, sampleRate);
        writeLittleEndianInt(output, byteRate);
        writeLittleEndianShort(output, blockAlign);
        writeLittleEndianShort(output, bitsPerSample);
        output.writeBytes("data");
        writeLittleEndianInt(output, pcmDataLength);
    }

    private static void writeLittleEndianShort(RandomAccessFile output, int value) throws IOException {
        output.write(value & 0xff);
        output.write((value >> 8) & 0xff);
    }

    private static void writeLittleEndianInt(RandomAccessFile output, long value) throws IOException {
        output.write((int) (value & 0xff));
        output.write((int) ((value >> 8) & 0xff));
        output.write((int) ((value >> 16) & 0xff));
        output.write((int) ((value >> 24) & 0xff));
    }

    private static String extensionOf(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < name.length() - 1) {
            return name.substring(dotIndex + 1);
        }
        return "";
    }
}
