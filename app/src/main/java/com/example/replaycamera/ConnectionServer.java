package com.example.replaycamera;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import android.os.Handler;
import android.util.Size;


public class ConnectionServer extends Thread {


    static final String LOG_TAG = "myLogs";
    ServerSocket serverSocket;
    private Socket clientSocket; //сокет для общения
    private boolean process = true;
    BufferedOutputStream out = null;
    BufferedInputStream in = null;
    boolean isData = false;
    byte[] data = null;
    int port;
    Handler handler;
    int currentFps;
    Size currentResolution;
//    ConnectionServer(int _port){
//        port = _port;
//    }
ConnectionServer(int _port, Handler h, int f, Size r){
    port = _port;
    handler = h;
    currentFps = f;
    currentResolution = r;
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
                //Log.i(LOG_TAG, "start accept videoServer ");
                clientSocket = serverSocket.accept();
                //clientSocket.setSoTimeout(5000);
                Log.i(LOG_TAG, "accept connect server " + port);
                in = new BufferedInputStream(clientSocket.getInputStream());
                out = new BufferedOutputStream(clientSocket.getOutputStream());
                //handler.sendEmptyMessage(2);    //включаем енкодер
            }catch(IOException e) {
                Log.e(LOG_TAG, "connection server accept error " + e.getMessage() + port);
            }
            while(process) {
                Log.i(LOG_TAG, "process");
                byte[] b = new byte[10];
                int ret;
                try {
//                        int av = in.available();
//                        Log.i(LOG_TAG, "av = " + av);
//                        if(av > 0)
                    //while(in.available() < 1);
                    Log.i(LOG_TAG, "in.available() = " + in.available());
                    ret = in.read(b, 0, 10);

                    Log.i(LOG_TAG, "ret = " + ret + Arrays.toString(b));
                } catch (IOException e) {
                    Log.e(LOG_TAG, "read connection error " + e.getMessage());
                    break;
                }
//                    String sB = Arrays.toString(b);
                Log.i(LOG_TAG, "ret = " + b.toString());
//                    if(ret != 10)
//                        break;
                if(Arrays.equals(b, "parameters".getBytes())){
                    Log.i(LOG_TAG, "Arrays.toString(b).equals(connect)");
                    try {
                        if(out != null) {
                            String data = String.valueOf(currentResolution.getWidth()) + "=" +
                                    String.valueOf(currentResolution.getHeight()) + "=" +
                                    String.valueOf(currentFps);
                            out.write(data.getBytes());
                            out.flush();
                            handler.sendEmptyMessage(2);    //включаем енкодер
                        }
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "error send conn data " + e.getMessage());
                        break;
                    }
                }
                else{
                    try {
                        if(out != null) {
                            out.write("ok".getBytes());
                            out.flush();
                        }
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "error send conn data " + e.getMessage());
                        break;
                    }
                }
            }
            handler.sendEmptyMessage(1);    //выключаем енкодер
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
                clientSocket.close();
            } catch (IOException e) {
                Log.e(LOG_TAG, "error clientSocket.close() " + e.getMessage() + port);
            }
            Log.i(LOG_TAG, "connection read error " + port);
        }
        try {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
            clientSocket.close();
            serverSocket.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "error clientSocket.close() " + e.getMessage() + port);
        }
        Log.i(LOG_TAG, "connection server exit " + port);
    }

}
