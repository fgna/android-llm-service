package de.fgna.androidllmservice;

import android.os.ParcelFileDescriptor;
import de.fgna.androidllmservice.ILlmCallback;
import de.fgna.androidllmservice.ILlmResultCallback;

interface ILlmService {
    boolean isModelReady();
    String getActiveModelName();
    void generate(String prompt, ILlmCallback callback);
    void generateWithImage(String prompt, in ParcelFileDescriptor image, ILlmCallback callback);

    String getProviderProfilesJson();
    void configureLanProvider(String baseUrl, String model);
    void generateWithProfile(String profileId, String prompt, ILlmCallback callback);
    void generateWithRequest(String requestId, String profileId, String prompt, ILlmCallback callback);
    boolean cancelRequest(String requestId);

    void generateWithRequestMetadata(
        String requestId,
        String profileId,
        String prompt,
        ILlmResultCallback callback
    );
}
