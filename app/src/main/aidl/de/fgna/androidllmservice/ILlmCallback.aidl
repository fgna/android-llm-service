package de.fgna.androidllmservice;

interface ILlmCallback {
    void onSuccess(String text, long initializationMillis, long generationMillis, boolean coldStart);
    void onError(String code, String message);
}
