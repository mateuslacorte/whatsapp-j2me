package com.whatsapp.voice;

import com.whatsapp.protocol.Jid;

import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.control.RecordControl;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Mobile Media API (MMAPI / JSR-135) audio recorder and player.
 * Supports capturing voice audio from BlackBerry microphone and playing
 * received voice audio clips / streams.
 */
public class VoiceAudioEngine {

    public interface RecordListener {
        void onRecordFinished(byte[] audioData);
        void onRecordError(String message);
    }

    private Player recordPlayer;
    private RecordControl recordControl;
    private ByteArrayOutputStream recordStream;
    private Player audioPlayer;
    private volatile boolean recording = false;

    private static VoiceAudioEngine instance;

    private VoiceAudioEngine() {}

    public static synchronized VoiceAudioEngine getInstance() {
        if (instance == null) {
            instance = new VoiceAudioEngine();
        }
        return instance;
    }

    public synchronized boolean isRecording() {
        return recording;
    }

    /**
     * Start recording microphone input via JSR-135 capture locator.
     * On BlackBerry devices: "capture://audio?encoding=audio/amr" or default "capture://audio"
     */
    public synchronized void startRecording(final RecordListener listener) {
        if (recording) return;
        recording = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    String locator = "capture://audio";
                    try {
                        recordPlayer = Manager.createPlayer("capture://audio?encoding=audio/amr");
                    } catch (Exception e) {
                        recordPlayer = Manager.createPlayer(locator);
                    }
                    recordPlayer.realize();
                    recordControl = (RecordControl) recordPlayer.getControl("RecordControl");
                    if (recordControl == null) {
                        recordControl = (RecordControl) recordPlayer.getControl("javax.microedition.media.control.RecordControl");
                    }

                    recordStream = new ByteArrayOutputStream();
                    recordControl.setRecordStream(recordStream);
                    recordControl.startRecord();
                    recordPlayer.start();
                } catch (Exception e) {
                    recording = false;
                    if (listener != null) {
                        listener.onRecordError("Record failed: " + e.getMessage());
                    }
                }
            }
        }).start();
    }

    /**
     * Stop recording and return the recorded byte stream.
     */
    public synchronized void stopRecording(final RecordListener listener) {
        if (!recording) return;
        recording = false;
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (recordControl != null) {
                        recordControl.commit();
                    }
                    if (recordPlayer != null) {
                        recordPlayer.stop();
                        recordPlayer.close();
                    }
                    byte[] data = recordStream != null ? recordStream.toByteArray() : new byte[0];
                    if (listener != null) {
                        listener.onRecordFinished(data);
                    }
                } catch (Exception e) {
                    if (listener != null) {
                        listener.onRecordError("Stop record failed: " + e.getMessage());
                    }
                } finally {
                    recordPlayer = null;
                    recordControl = null;
                    recordStream = null;
                }
            }
        }).start();
    }

    /**
     * Playback audio bytes through device speaker.
     */
    public synchronized void playAudio(final byte[] audioData, final String mimeType) {
        if (audioData == null || audioData.length == 0) return;
        new Thread(new Runnable() {
            public void run() {
                try {
                    stopAudio();
                    String mime = mimeType != null ? mimeType : "audio/amr";
                    audioPlayer = Manager.createPlayer(new ByteArrayInputStream(audioData), mime);
                    audioPlayer.realize();
                    audioPlayer.prefetch();
                    audioPlayer.start();
                } catch (Exception e) {
                    // Try basic wav or default
                    try {
                        audioPlayer = Manager.createPlayer(new ByteArrayInputStream(audioData), "audio/x-wav");
                        audioPlayer.start();
                    } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    public synchronized void stopAudio() {
        try {
            if (audioPlayer != null) {
                audioPlayer.stop();
                audioPlayer.close();
            }
        } catch (Exception ignored) {}
        audioPlayer = null;
    }

    /**
     * Play telephone ringtone tone using Manager.playTone.
     */
    public static void playCallRingTone() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    Manager.playTone(60, 400, 100);
                    Thread.sleep(150);
                    Manager.playTone(64, 400, 100);
                } catch (Exception ignored) {}
            }
        }).start();
    }
}
