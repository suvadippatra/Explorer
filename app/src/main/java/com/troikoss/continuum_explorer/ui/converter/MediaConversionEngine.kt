package com.troikoss.continuum_explorer.ui.converter

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 100% On-Device Hardware-Accelerated Audio & Video Conversion Engine.
 * Uses Android's MediaExtractor, MediaMuxer, MediaCodec, and MediaMetadataRetriever
 * for zero-cloud audio extraction, PCM RIFF WAV decoding, AAC/M4A encoding, MP4 remuxing,
 * and video frame snapshot capture across Android 8 (API 26) through Android 15 (API 35).
 */
object MediaConversionEngine {

    suspend fun convertMedia(
        sourceFile: File,
        targetExtension: String,
        outputFile: File,
        tuning: ConversionTuningConfig,
        onProgress: (Float, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dstExt = targetExtension.lowercase().trimStart('.')
            onProgress(0.10f, "Inspecting media tracks in ${sourceFile.name}...")

            return@withContext when (dstExt) {
                "png", "jpg", "jpeg", "webp" -> {
                    extractVideoFrameToImage(sourceFile, dstExt, outputFile, tuning, onProgress)
                }
                "wav" -> {
                    decodeMediaAudioToPcmWav(sourceFile, outputFile, tuning, onProgress)
                }
                "m4a", "aac", "mp3" -> {
                    extractOrTranscodeToAudio(sourceFile, dstExt, outputFile, tuning, onProgress)
                }
                "mp4" -> {
                    remuxVideoToMp4(sourceFile, outputFile, tuning.stripAudioFromVideo, onProgress)
                }
                else -> Result.failure(
                    UnsupportedOperationException("Unsupported media target format: .$dstExt")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extracts a high-resolution video frame at `tuning.videoFrameTimestampSec` and encodes to PNG/JPG/WEBP.
     */
    private fun extractVideoFrameToImage(
        sourceFile: File,
        dstExt: String,
        outputFile: File,
        tuning: ConversionTuningConfig,
        onProgress: (Float, String) -> Unit
    ): Result<File> {
        val retriever = MediaMetadataRetriever()
        var frame: Bitmap? = null
        return try {
            onProgress(0.30f, "Seeking to ${tuning.videoFrameTimestampSec}s in video...")
            retriever.setDataSource(sourceFile.absolutePath)
            val timeUs = tuning.videoFrameTimestampSec.coerceAtLeast(0) * 1_000_000L
            frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L)
                ?: return Result.failure(IllegalArgumentException("Unable to extract video frame from ${sourceFile.name}"))

            onProgress(0.75f, "Encoding video frame to ${dstExt.uppercase()}...")
            FileOutputStream(outputFile).use { fos ->
                val q = tuning.imageQuality.coerceIn(10, 100)
                when (dstExt) {
                    "png" -> frame.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    "jpg", "jpeg" -> frame.compress(Bitmap.CompressFormat.JPEG, q, fos)
                    "webp" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            frame.compress(Bitmap.CompressFormat.WEBP_LOSSY, q, fos)
                        } else {
                            @Suppress("DEPRECATION")
                            frame.compress(Bitmap.CompressFormat.WEBP, q, fos)
                        }
                    }
                }
            }
            onProgress(1.0f, "Saved frame to ${outputFile.name}")
            Result.success(outputFile)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
            try { if (frame != null && !frame.isRecycled) frame.recycle() } catch (_: Exception) {}
        }
    }

    /**
     * Decodes any Android-supported audio/video file's audio track into a standard 16-bit PCM RIFF WAVE (.wav) file.
     */
    private fun decodeMediaAudioToPcmWav(
        sourceFile: File,
        outputFile: File,
        tuning: ConversionTuningConfig,
        onProgress: (Float, String) -> Unit
    ): Result<File> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(sourceFile.absolutePath)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                return Result.failure(IllegalArgumentException("No audio track found in '${sourceFile.name}'."))
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L)
            } else 1L

            var sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else tuning.audioSampleRateHz

            var channels = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            onProgress(0.25f, "Decoding audio stream to 16-bit PCM...")

            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.setLength(0)
                // Reserve 44-byte RIFF WAVE header
                raf.write(ByteArray(44))

                var totalPcmBytes = 0L
                val bufferInfo = MediaCodec.BufferInfo()
                var isInputEOS = false
                var isOutputEOS = false

                while (!isOutputEOS) {
                    if (!isInputEOS) {
                        val inIndex = codec.dequeueInputBuffer(10_000)
                        if (inIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inIndex)
                            if (inputBuffer != null) {
                                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                if (sampleSize < 0) {
                                    codec.queueInputBuffer(
                                        inIndex, 0, 0, 0L,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    isInputEOS = true
                                } else {
                                    val pts = extractor.sampleTime
                                    codec.queueInputBuffer(inIndex, 0, sampleSize, pts, 0)
                                    extractor.advance()
                                    val prog = (0.25f + 0.65f * (pts.toFloat() / durationUs.toFloat())).coerceIn(0.25f, 0.92f)
                                    onProgress(prog, "Decoding PCM audio (${(prog * 100).toInt()}%)...")
                                }
                            }
                        }
                    }

                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outIndex >= 0 -> {
                            val outBuffer = codec.getOutputBuffer(outIndex)
                            if (outBuffer != null && bufferInfo.size > 0) {
                                outBuffer.position(bufferInfo.offset)
                                outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                val chunk = ByteArray(bufferInfo.size)
                                outBuffer.get(chunk)

                                val toWrite = if (tuning.audioMonoChannel && channels == 2) {
                                    downmixStereo16BitToMono(chunk)
                                } else {
                                    chunk
                                }
                                raf.write(toWrite)
                                totalPcmBytes += toWrite.size
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                isOutputEOS = true
                            }
                        }
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFmt = codec.outputFormat
                            if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                sampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                channels = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                        }
                    }
                }

                val finalChannels = if (tuning.audioMonoChannel && channels == 2) 1 else channels.coerceIn(1, 2)
                raf.seek(0)
                raf.write(buildWavHeader(totalPcmBytes, sampleRate, finalChannels, 16))
            }

            onProgress(1.0f, "Saved PCM WAV to ${outputFile.name}")
            Result.success(outputFile)
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun downmixStereo16BitToMono(stereoBytes: ByteArray): ByteArray {
        val samplePairs = stereoBytes.size / 4
        val monoBytes = ByteArray(samplePairs * 2)
        val srcBuf = ByteBuffer.wrap(stereoBytes).order(ByteOrder.LITTLE_ENDIAN)
        val dstBuf = ByteBuffer.wrap(monoBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until samplePairs) {
            val left = srcBuf.short.toInt()
            val right = srcBuf.short.toInt()
            val mixed = ((left + right) / 2).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            dstBuf.putShort(mixed)
        }
        return monoBytes
    }

    private fun buildWavHeader(
        pcmDataLength: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        val totalDataLen = pcmDataLength + 36L
        val buf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(totalDataLen.toInt())
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16) // PCM chunk size
        buf.putShort(1.toShort()) // AudioFormat 1 = PCM
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort(blockAlign.toShort())
        buf.putShort(bitsPerSample.toShort())
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(pcmDataLength.toInt())
        return buf.array()
    }

    /**
     * Extracts AAC/M4A audio directly when compatible or transcodes PCM/MP3/FLAC/Vorbis to AAC M4A.
     */
    @SuppressLint("WrongConstant")
    private fun extractOrTranscodeToAudio(
        sourceFile: File,
        dstExt: String,
        outputFile: File,
        tuning: ConversionTuningConfig,
        onProgress: (Float, String) -> Unit
    ): Result<File> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(sourceFile.absolutePath)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrackIndex == -1 || audioFormat == null) {
                return Result.failure(IllegalArgumentException("No audio track found in '${sourceFile.name}'."))
            }

            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: ""
            // Fast lossless demux if already AAC and target is M4A/AAC/MP3
            if (mime == MediaFormat.MIMETYPE_AUDIO_AAC) {
                onProgress(0.35f, "Fast-demuxing AAC audio stream...")
                extractor.selectTrack(audioTrackIndex)
                val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                return try {
                    val dstTrack = muxer.addTrack(audioFormat)
                    muxer.start()
                    val buf = ByteBuffer.allocate(256 * 1024)
                    val info = MediaCodec.BufferInfo()
                    while (true) {
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) break
                        info.offset = 0
                        info.size = size
                        info.presentationTimeUs = extractor.sampleTime
                        info.flags = extractor.sampleFlags
                        muxer.writeSampleData(dstTrack, buf, info)
                        extractor.advance()
                    }
                    muxer.stop()
                    onProgress(1.0f, "Extracted audio to ${outputFile.name}")
                    Result.success(outputFile)
                } finally {
                    try { muxer.release() } catch (_: Exception) {}
                }
            }
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }

        // Otherwise decode to temporary PCM and encode to AAC inside MP4/M4A container
        val tempWav = File(outputFile.parentFile, ".tmp_${ System.currentTimeMillis() }.wav")
        return try {
            val wavRes = decodeMediaAudioToPcmWav(sourceFile, tempWav, tuning) { p, msg ->
                onProgress(p * 0.5f, msg)
            }
            if (wavRes.isFailure) return wavRes
            encodeWavToAacM4a(tempWav, outputFile) { p, msg ->
                onProgress(0.5f + p * 0.5f, msg)
            }
        } finally {
            try { if (tempWav.exists()) tempWav.delete() } catch (_: Exception) {}
        }
    }

    private fun encodeWavToAacM4a(
        wavFile: File,
        outputFile: File,
        onProgress: (Float, String) -> Unit
    ): Result<File> {
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        return try {
            RandomAccessFile(wavFile, "r").use { raf ->
                if (raf.length() <= 44) {
                    return Result.failure(IllegalArgumentException("Decoded PCM stream is empty."))
                }
                val header = ByteArray(44)
                raf.readFully(header)
                val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val channels = bb.getShort(22).toInt().coerceIn(1, 2)
                val sampleRate = bb.getInt(24).coerceIn(8000, 48000)
                val totalPcmBytes = (raf.length() - 44L).coerceAtLeast(1L)

                val format = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_AAC,
                    sampleRate,
                    channels
                ).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                }

                encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                encoder.start()

                muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                var muxerTrackIndex = -1
                var muxerStarted = false

                val bufferInfo = MediaCodec.BufferInfo()
                val readChunk = ByteArray(8192)
                val bytesPerSecond = (sampleRate * channels * 2).toDouble()
                var bytesReadTotal = 0L
                var isInputEOS = false
                var isOutputEOS = false

                while (!isOutputEOS) {
                    if (!isInputEOS) {
                        val inIdx = encoder.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val inBuf = encoder.getInputBuffer(inIdx)
                            if (inBuf != null) {
                                inBuf.clear()
                                val count = raf.read(readChunk, 0, minOf(readChunk.size, inBuf.remaining()))
                                if (count <= 0) {
                                    val ptsUs = ((bytesReadTotal / bytesPerSecond) * 1_000_000.0).toLong()
                                    encoder.queueInputBuffer(
                                        inIdx, 0, 0, ptsUs,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    isInputEOS = true
                                } else {
                                    inBuf.put(readChunk, 0, count)
                                    val ptsUs = ((bytesReadTotal / bytesPerSecond) * 1_000_000.0).toLong()
                                    encoder.queueInputBuffer(inIdx, 0, count, ptsUs, 0)
                                    bytesReadTotal += count
                                    val prog = (bytesReadTotal.toFloat() / totalPcmBytes.toFloat()).coerceIn(0f, 1f)
                                    onProgress(prog, "Encoding AAC audio (${(prog * 100).toInt()}%)...")
                                }
                            }
                        }
                    }

                    val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outIdx >= 0 -> {
                            val outBuf = encoder.getOutputBuffer(outIdx)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                bufferInfo.size = 0
                            }
                            if (outBuf != null && bufferInfo.size > 0 && muxerStarted) {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(muxerTrackIndex, outBuf, bufferInfo)
                            }
                            encoder.releaseOutputBuffer(outIdx, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                isOutputEOS = true
                            }
                        }
                    }
                }
                if (muxerStarted) muxer.stop()
            }
            onProgress(1.0f, "Encoded M4A/AAC audio to ${outputFile.name}")
            Result.success(outputFile)
        } finally {
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    /**
     * Remuxes a video container into MP4 (with optional audio stripping / muting).
     */
    @SuppressLint("WrongConstant")
    private fun remuxVideoToMp4(
        sourceFile: File,
        outputFile: File,
        stripAudio: Boolean,
        onProgress: (Float, String) -> Unit
    ): Result<File> {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(sourceFile.absolutePath)
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackMap = mutableMapOf<Int, Int>()
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                val isVideo = mime.startsWith("video/")
                val isAudio = mime.startsWith("audio/") && !stripAudio
                if (isVideo || isAudio) {
                    try {
                        val dstTrack = muxer.addTrack(fmt)
                        extractor.selectTrack(i)
                        trackMap[i] = dstTrack
                    } catch (_: Exception) {
                        extractor.unselectTrack(i)
                    }
                }
            }

            if (trackMap.isEmpty()) {
                return Result.failure(IllegalArgumentException("No MP4-compatible tracks found in ${sourceFile.name}"))
            }

            onProgress(0.35f, if (stripAudio) "Remuxing muted MP4 stream..." else "Remuxing MP4 container...")
            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val trackIdx = extractor.sampleTrackIndex
                if (trackIdx < 0) break
                val dstTrack = trackMap[trackIdx]
                if (dstTrack != null) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = extractor.sampleTime
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(dstTrack, buffer, info)
                }
                extractor.advance()
            }
            muxer.stop()
            onProgress(1.0f, "Remuxed MP4 to ${outputFile.name}")
            Result.success(outputFile)
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }
}
