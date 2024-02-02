package com.example.testtypelessstttypeless;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;



public class WebsocketManager {
    private AudioRecord audioRecord;
    private WebSocket webSocket;
    private OkHttpClient client;
    private String webSocketURL;
    private String language;
    private String[] hotwords;
    private boolean manualPunctuation;
    private String domain;
    private String endUserId;
    private static final int SAMPLE_RATE = 16000; // Sample rate in Hz
    private int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    private boolean isRecording = false;
    private MessageHandler messageHandler;

    public interface MessageHandler {
        void handleMessage(String message);
    }

    public byte[] addWavHeader(byte[] rawData, int sampleRate, int channels, int bitsPerSample) {
        int headerSize = 44;
        int totalDataLen = rawData.length + headerSize - 8;
        int audioDataLength = rawData.length;
        byte[] header = new byte[headerSize];

        // RIFF header
        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        // WAVE
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        // 'fmt ' chunk
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        // 16 bytes for PCM format
        header[16] = 16;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        // Audio format 1 = PCM
        header[20] = 1;
        header[21] = 0;
        // Number of channels
        header[22] = (byte) channels;
        header[23] = 0;
        // Sample rate
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        // Byte rate
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        // Block align
        header[32] = (byte) (channels * bitsPerSample / 8);
        header[33] = 0;
        // Bits per sample
        header[34] = (byte) bitsPerSample;
        header[35] = 0;
        // 'data' chunk
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (audioDataLength & 0xff);
        header[41] = (byte) ((audioDataLength >> 8) & 0xff);
        header[42] = (byte) ((audioDataLength >> 16) & 0xff);
        header[43] = (byte) ((audioDataLength >> 24) & 0xff);

        // Combine header and rawData into one array
        byte[] wavData = new byte[header.length + rawData.length];
        System.arraycopy(header, 0, wavData, 0, header.length);
        System.arraycopy(rawData, 0, wavData, header.length, rawData.length);

        return wavData;
    }


    public WebsocketManager(String webSocketURL, String language, String[] hotwords, boolean manualPunctuation, String domain, String endUserId, MessageHandler messageHandler) {
        this.webSocketURL = webSocketURL;
        this.language = language;
        this.hotwords = hotwords;
        this.manualPunctuation = manualPunctuation;
        this.domain = domain;
        this.endUserId = endUserId;
        this.messageHandler = messageHandler;
        client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
    }

    public void start() {
        Request request = new Request.Builder().url(webSocketURL).build();
        Log.d("WebSocket", "Pressed start");
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d("WebSocket", "Launched onOpen");
                super.onOpen(webSocket, response);
                try {
                    // Prepare the initial configuration data as a JSON object
                    JSONObject configData = new JSONObject();
                    configData.put("language", WebsocketManager.this.language);

                    // Joining hotwords array to a comma-separated string
                    StringBuilder hotwordsCombined = new StringBuilder();
                    for (String hotword : WebsocketManager.this.hotwords) {
                        if (hotwordsCombined.length() > 0) hotwordsCombined.append(",");
                        hotwordsCombined.append(hotword);
                    }
                    configData.put("hotwords", hotwordsCombined.toString());
                    configData.put("manual_punctuation", WebsocketManager.this.manualPunctuation);
                    configData.put("end_user_id", WebsocketManager.this.endUserId);
                    configData.put("domain", WebsocketManager.this.domain);

                    // Send the JSON string via WebSocket
                    webSocket.send(configData.toString());
                } catch (Exception e) {
                    e.printStackTrace(); // Handle JSON construction or sending error
                }

                // Start recording and streaming audio data
                startRecording();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                // Handle messages
                super.onMessage(webSocket, text);
                if (messageHandler != null) {
                    messageHandler.handleMessage(text);
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                // Handle closing
                Log.d("WebSocket", "Pressed stop");
                stopRecording();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                super.onFailure(webSocket, t, response);
                Log.e("WebSocket", "Error: " + t.getMessage(), t);
            }
        });

        // client.dispatcher().executorService().shutdown();
    }

    private void startRecording() {
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        audioRecord.startRecording();
        Log.d("WebSocket", "Started recording");
        isRecording = true;
        new Thread(() -> {
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                return;
            }
            int bytesPerSecond = SAMPLE_RATE * 2; // 16kHz * 16 bits (2 bytes per sample)
            byte[] oneSecondBuffer = new byte[bytesPerSecond];
            int bytesReadTotal = 0;
            audioRecord.startRecording();
            isRecording = true;
            while (isRecording) {
                byte[] audioBuffer = new byte[bufferSize];
                int readResult = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                if (readResult > 0) {
                    // Ensure we don't overflow oneSecondBuffer
                    int spaceLeft = oneSecondBuffer.length - bytesReadTotal;
                    int bytesToCopy = Math.min(spaceLeft, readResult);
                    System.arraycopy(audioBuffer, 0, oneSecondBuffer, bytesReadTotal, bytesToCopy);
                    bytesReadTotal += bytesToCopy;

                    // If oneSecondBuffer is full, process and stream it
                    if (bytesReadTotal == oneSecondBuffer.length) {
                        // Add WAV header
                        byte[] wavData = addWavHeader(oneSecondBuffer, 16000, 1, 16);
                        // Convert the audio buffer to Base64
                        String base64Audio = Base64.encodeToString(wavData, 0, bytesReadTotal, Base64.NO_WRAP);

                        // Construct the JSON payload
                        JSONObject payload = new JSONObject();
                        try {
                            payload.put("audio", base64Audio);
                            payload.put("uid", "1234567890"); // Assuming a static UID for example

                            // Convert JSON object to string and send it via WebSocket
                            webSocket.send(payload.toString());
                            Log.d("WebSocket", "Send message");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        bytesReadTotal = 0; // Reset for the next second of audio data
                    }
                }
            }
        }).start();
    }

    public void stop() {
        stopRecording();
        if (webSocket != null) {
            webSocket.close(1000, "Closing");
            webSocket = null;
        }
    }

    private void stopRecording() {
        if (audioRecord != null) {
            isRecording = false;
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e){
                Log.d("WebSocket", "Stop error");
                e.printStackTrace();
            }
            audioRecord = null;
        }
    }
}
