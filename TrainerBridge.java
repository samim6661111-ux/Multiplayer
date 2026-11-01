package com.gtavsource.android;
final class TrainerBridge {
    interface Endpoint {
        String catalog(int kind);
        void setEnabled(boolean on);
        void setVisible(boolean on);
        String snapshot();
        int submit(String action, String text, int id, int epoch, float[] values);
    }
    static final Endpoint NATIVE = null;
    static String error(int code) { return null; }
}
