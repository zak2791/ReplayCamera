package com.example.replaycamera;

import android.util.Log;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;

public class VideoAudioServer extends Thread{
    static final String LOG_TAG = "myLogs";
    ServerSocket serverSocket;
    private Socket clientSocket; //сокет для общения
    private boolean process = true;
    BufferedOutputStream out = null;
    BufferedInputStream in = null;
    int port;
    BlockingQueue<byte[]> queue;
    VideoAudioServer(int _port, BlockingQueue<byte[]> _queue){
        port = _port;
        queue = _queue;
    }
    public void stopProcess(){
        process = false;
        Log.d(LOG_TAG, "stopProcess 1 " + port);
        try {
            if(out != null) {
                out.close();
                out = null;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "error out.close()   " + e.getMessage() + port);
        }
        Log.d(LOG_TAG, "stopProcess 2 " + port);
        try {
            if(clientSocket != null) {
                clientSocket.close();
                clientSocket = null;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "error clientSocket.close()   " + e.getMessage() + port);
        }
        Log.d(LOG_TAG, "stopProcess 3 " + port);
        if(serverSocket != null) {
            try {
                serverSocket.close();
                Log.e(LOG_TAG, "audio server stop   "  + port);
            } catch (Exception e) {
                Log.e(LOG_TAG, "error audio serverSocket.close()  " + e.getMessage() + port);
            }
        }
        Log.d(LOG_TAG, "stopProcess 4 " + port);
    }
    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", port), 10);
            Log.i(LOG_TAG, "connection server bind " + port);
        }catch(IOException e) {
            Log.e(LOG_TAG, "connection server error bind " + e.getMessage() + port);
            return;
        }
        while(process) {
            try {
                clientSocket = serverSocket.accept();
                Log.i(LOG_TAG, "accept connect server " + port);
                in = new BufferedInputStream(clientSocket.getInputStream());
                out = new BufferedOutputStream(clientSocket.getOutputStream());
            }catch(IOException e) {
                Log.e(LOG_TAG, "connection video server accept error " + e.getMessage() + port);
            }
            while(process) {
                if(!queue.isEmpty()) {
                    try {
                        if (out != null) {
                            out.write(queue.take());
                            out.flush();
                        }
                    } catch (IOException | InterruptedException e) {
                        queue.clear();
                        Log.e(LOG_TAG, "error send video data " + e.getMessage());
                    }
                }
            }
        }
        try {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
            if(clientSocket != null)
                clientSocket.close();
            serverSocket.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "error video clientSocket.close() " + e.getMessage() + port);
        }
        Log.i(LOG_TAG, "video server exit " + port);
    }
}
