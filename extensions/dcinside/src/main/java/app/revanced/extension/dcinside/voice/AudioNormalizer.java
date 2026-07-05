package app.revanced.extension.dcinside.voice;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * v1 conversion: normalize a picked audio file to the MPEG-4/AAC content the recorder produces,
 * using platform APIs only (the app's bundled media3 Transformer is R8-obfuscated and not
 * cleanly reusable).
 *
 * Any container carrying an AAC track (.m4a / .mp4 / .aac / .3gp / …) is remuxed into an MPEG-4
 * container with no re-encode. Non-AAC input (mp3/wav/ogg/flac/…) is rejected; transcoding those
 * to AAC is deferred to v1.1.
 */
final class AudioNormalizer {
    private AudioNormalizer() {}

    private static final int FALLBACK_BUFFER = 256 * 1024;

    /** @return true if {@code dst} now holds an MPEG-4/AAC clip; false if the source is not AAC or on failure. */
    static boolean toM4a(Context context, Uri src, File dst) {
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean success = false;
        try {
            extractor.setDataSource(context, src, null);

            int audioTrack = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    if (MediaFormat.MIMETYPE_AUDIO_AAC.equals(mime)) {
                        audioTrack = i;
                        format = f;
                    }
                    break; // first audio track decides; non-AAC -> reject
                }
            }
            if (audioTrack < 0 || format == null) return false;

            extractor.selectTrack(audioTrack);

            int bufferSize = FALLBACK_BUFFER;
            if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                bufferSize = Math.max(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
            }
            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

            muxer = new MediaMuxer(dst.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int outTrack = muxer.addTrack(format);
            muxer.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (true) {
                int size = extractor.readSampleData(buffer, 0);
                if (size < 0) break;
                info.offset = 0;
                info.size = size;
                info.presentationTimeUs = extractor.getSampleTime();
                info.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME; // AAC frames are independently decodable
                muxer.writeSampleData(outTrack, buffer, info);
                extractor.advance();
            }
            muxer.stop();
            success = true;
        } catch (Throwable t) {
            success = false;
        } finally {
            try {
                if (muxer != null) muxer.release();
            } catch (Throwable ignored) {
            }
            try {
                extractor.release();
            } catch (Throwable ignored) {
            }
            if (!success) {
                try {
                    if (dst.exists()) dst.delete();
                } catch (Throwable ignored) {
                }
            }
        }
        return success;
    }
}
