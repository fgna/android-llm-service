package de.fgna.androidllmservice;

import de.fgna.androidllmservice.ILlmCallback;

interface ILlmService {
    boolean isModelReady();
    String getActiveModelName();
    void generate(String prompt, ILlmCallback callback);
}
