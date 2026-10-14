package com.truckcar.notifications.channel;

import com.truckcar.notifications.channel.voice.VoiceSynthesisResult;

public interface VoiceSynthesizer {

    /**
     * Synthesizes text to voice audio, caches if possible, stores in S3 and generates a signed URL.
     *
     * @param text Text to synthesize into speech
     * @param language Language code (e.g. es-CO)
     * @return Result containing presigned URL and S3 details
     */
    VoiceSynthesisResult synthesizeAndUpload(String text, String language);
}
