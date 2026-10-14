package com.truckcar.notifications.channel.voice;

import lombok.Builder;

@Builder
public record VoiceSynthesisResult(
        boolean success,
        String audioUrl,
        String s3Key,
        boolean fromCache,
        String errorReason
) {
    public static VoiceSynthesisResult ok(String audioUrl, String s3Key, boolean fromCache) {
        return VoiceSynthesisResult.builder()
                .success(true)
                .audioUrl(audioUrl)
                .s3Key(s3Key)
                .fromCache(fromCache)
                .build();
    }

    public static VoiceSynthesisResult failure(String errorReason) {
        return VoiceSynthesisResult.builder()
                .success(false)
                .errorReason(errorReason)
                .build();
    }
}
