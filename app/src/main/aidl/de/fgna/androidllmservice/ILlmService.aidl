package de.fgna.androidllmservice;

import android.os.ParcelFileDescriptor;
import de.fgna.androidllmservice.ILlmCallback;

interface ILlmService {
    boolean isModelReady();
    String getActiveModelName();
    void generate(String prompt, ILlmCallback callback);
    void generateWithImage(String prompt, in ParcelFileDescriptor image, ILlmCallback callback);

    String getProviderProfilesJson();
    void configureLanProvider(String baseUrl, String model);
    void generateWithProfile(String profileId, String prompt, ILlmCallback callback);
}
