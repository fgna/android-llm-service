package de.fgna.androidllmservice;

interface ILlmResultCallback {
    void onSuccess(
        String text,
        long initializationMillis,
        long generationMillis,
        boolean coldStart,
        String providerId,
        String modelName
    );
    void onError(String code, String message);
}
