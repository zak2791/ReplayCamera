package com.example.replaycamera;

import android.util.Log;
import android.util.Size;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class ConnectionCommonServer extends Thread {
    static final String LOG_TAG = "myLogs";
    ServerSocket serverSocket;
    private Socket clientSocket; //сокет для общения
    private boolean process = true;
    BufferedOutputStream out = null;
    int port = 6666;
    int currentCamera;
    Size currentResolution;
    int currentFps;
    ConnectionCommonServer(int cam, Size res, int fps){
        currentCamera = cam;
        currentResolution = res;
        currentFps = fps;
    }
    public void stopProcess(){
        process = false;
        try {
            if(out != null) {
                out.close();
                out = null;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "error out.close()   " + e.getMessage() + port);
        }
        try {
            if(clientSocket != null) {
                clientSocket.close();
                clientSocket = null;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "error clientSocket.close()   " + e.getMessage() + port);
        }
        if(serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception e) {
                Log.e(LOG_TAG, "error audio serverSocket.close()  " + e.getMessage() + port);
            }
        }
    }
    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", port), 10);
        }catch(IOException e) {
            Log.e(LOG_TAG, "connection server error bind " + e.getMessage() + port);
            return;
        }
        while(process) {
            try {
                clientSocket = serverSocket.accept();
                clientSocket.setSoTimeout(2000);
                out = new BufferedOutputStream(clientSocket.getOutputStream());
            }catch(IOException e) {
                Log.e(LOG_TAG, "audio server accept error " + e.getMessage() + port);
            }
            try {
                if(out != null) {
                    String data = String.valueOf(currentCamera) + "=" +
                            String.valueOf(currentResolution.getWidth()) + "=" +
                            String.valueOf(currentResolution.getHeight()) + "=" +
                            String.valueOf(currentFps);
                    out.write(data.getBytes());
                    out.flush();
                }
            } catch (IOException e) {
                Log.e(LOG_TAG, "error send conn data " + e.getMessage() + port);
                try {
                    clientSocket.close();
                    break;
                } catch (IOException ex) {
                    Log.e(LOG_TAG, "error close conn client " + e.getMessage() + port);
                    break;
                }
            }
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                Log.e(LOG_TAG, "error clientSocket.close() " + e.getMessage() + port);
            }
        }
    }
}
