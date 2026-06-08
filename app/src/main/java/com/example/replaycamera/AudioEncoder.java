package com.example.replaycamera;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

public class AudioEncoder {
    private static final int SRC_SAMPLE_RATE = 44100;
    private static final int SRC_CHANNELS = 1;
    private static final int PCM_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BITRATE = 128000; // 128 kbps

    private AudioRecord audioRecord;
    private MediaCodec encoder;
    private boolean isRunning = false;


    public void startRecording(OutputStream output) throws IOException {
        Log.i("444", "0");
        int minBuffer = AudioRecord.getMinBufferSize(SRC_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, PCM_ENCODING);

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SRC_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, PCM_ENCODING, minBuffer * 2);

        encoder = MediaCodec.createEncoderByType("audio/mp4a-latm");
        MediaFormat format = MediaFormat.createAudioFormat("audio/mp4a-latm", SRC_SAMPLE_RATE, SRC_CHANNELS);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();

        isRunning = true;
        Log.i("444", "0");
        audioRecord.startRecording();
        Log.i("444", "1");
        // Thread: capture + feed to encoder
        new Thread(() -> {
            ByteBuffer[] inputBuffers = encoder.getInputBuffers();
            ByteBuffer[] outputBuffers = encoder.getOutputBuffers();
            byte[] pcmBuffer = new byte[2048];

            while (isRunning) {
                int read = audioRecord.read(pcmBuffer, 0, pcmBuffer.length);
                Log.i("444", "read = " + read);
                if (read > 0) {
                    int inputIndex = encoder.dequeueInputBuffer(10000);
                    if (inputIndex >= 0) {
                        ByteBuffer inBuf = inputBuffers[inputIndex];
                        inBuf.clear();
                        inBuf.put(pcmBuffer, 0, read);
                        long presentationTimeUs = System.nanoTime() / 1000;
                        encoder.queueInputBuffer(inputIndex, 0, read, presentationTimeUs, 0);
                    }

                    // Drain encoder output
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    int outputIndex;
                    do {
                        outputIndex = encoder.dequeueOutputBuffer(info, 0);
                        if (outputIndex >= 0) {
                            ByteBuffer outBuf = outputBuffers[outputIndex];
                            byte[] aacData = new byte[info.size];
                            outBuf.position(info.offset);
                            outBuf.limit(info.offset + info.size);
                            outBuf.get(aacData);
                            // Write to file/stream (ADTS header if necessary)
                            try {
                                output.write(aacData);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            encoder.releaseOutputBuffer(outputIndex, false);
                        } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            // handle format change if needed
                        }
                    } while (outputIndex >= 0);
                }
            }

            // flush and release
            encoder.stop();
            encoder.release();
            audioRecord.stop();
            audioRecord.release();
        }).start();
    }

    public void stopRecording() {
        isRunning = false;
    }
}

class AudioServer2 extends Thread {

    ServerSocket serverSocket;
    private Socket clientSocket; //сокет для общения
    byte[] buf = new byte[7];
    //private boolean isClosed = false;
    private boolean process = true;
    BufferedInputStream in;
    int countNoData = 0;
    BufferedOutputStream out = null;
    boolean isData = false;
    byte[] data = null;
    int port;

    AudioServer2(int _port) {
        port = _port;
    }

    public void sendData(ByteBuffer buffer) {
        //Log.e("AudioEncoder2", "sendData();   " );
        data = new byte[buffer.limit()];
        buffer.get(data);
        isData = true;
    }

    public void stopProcess() {

        process = false;
        Log.d("AudioEncoder2", "stopProcess 1 ");
        try {
            if (out != null) {
                out.close();
                out = null;
            }
        } catch (Exception e) {
            Log.e("AudioEncoder2", "error out.close()   " + e.getMessage());
        }
        Log.d("AudioEncoder2", "stopProcess 2 ");
        try {
            if (clientSocket != null) {
                clientSocket.close();
                clientSocket = null;
            }
        } catch (Exception e) {
            Log.e("AudioEncoder2", "error clientSocket.close()   " + e.getMessage());
        }
        Log.d("AudioEncoder2", "stopProcess 3 ");
        if (serverSocket != null) {
            try {
                serverSocket.close();
                Log.e("AudioEncoder2", "audio server stop   ");
            } catch (Exception e) {
                Log.e("AudioEncoder2", "error audio serverSocket.close()  " + e.getMessage());
            }
        }
        Log.d("AudioEncoder2", "stopProcess 4 ");
    }

    @Override
    public void run() {
        try {
            //Log.i(LOG_TAG, "audio server create ");
            serverSocket = new ServerSocket();
            //serverSocket.setReuseAddress(true);
            //serverSocket.setSoTimeout(10);
            serverSocket.bind(new InetSocketAddress("127.0.0.1", port), 10);
            Log.i("AudioEncoder2", "audio server bind ");
        } catch (IOException e) {
            Log.e("AudioEncoder2", "audio server error create bind " + e.getMessage() + port);
            return;
        }
//            }catch (SocketTimeoutException e){
//                Log.e(LOG_TAG, "audio SocketTimeoutException " + e.getMessage());
//            }
        while (process) {
            try {
                //Log.i(LOG_TAG, "start accept videoServer ");
                clientSocket = serverSocket.accept();
                //clientSocket.setSoTimeout(10);
                Log.i("AudioEncoder2", "accept audio server ");
                //in = new BufferedInputStream(clientSocket.getInputStream());
                out = new BufferedOutputStream(clientSocket.getOutputStream());

            } catch (IOException e) {
                Log.e("AudioEncoder2", "audio server accept error " + e.getMessage());
            }
            //Log.i(LOG_TAG, "SocketServerThread accept ");


            while (process) {
                if (isData) {
                    try {
                        if (out != null) {
                            out.write(data);
                            out.flush();
                            Log.i("AudioEncoder", "send audio data " + data.length);
                        }
                        //Log.i(LOG_TAG, "send audio data " + data.length);
                    } catch (IOException e) {
                        Log.e("AudioEncoder2", "error send audio data " + e.getMessage());
                        //handler.sendEmptyMessage(1);
                    } finally {
                        data = null;
                        isData = false;
                    }
                }
            }

        }
        Log.i("AudioEncoder2", "audio server exit ");
    }

}
