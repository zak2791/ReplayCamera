package com.example.replaycamera;

import android.os.Message;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import android.os.Handler;
import android.util.Size;
public class ConnectionServer extends Thread {
    static final String LOG_TAG = "serverLogs";
    ServerSocket serverSocket;
    private Socket clientSocket; //сокет для общения
    private boolean process = true;
    BufferedOutputStream out = null;
    BufferedInputStream in = null;
    int port;
    Handler handler;
    Handler handlertxt;
    Message msg;
    int currentFps;
    Size currentResolution;
    boolean onPause;
    ScheduledExecutorService service;
    boolean sendFlag;
ConnectionServer(int _port, Handler h, Handler ht, int f, Size r){
    port = _port;
    handler = h;
    handlertxt = ht;
    currentFps = f;
    currentResolution = r;

    msg = handlertxt.obtainMessage();
//    msg.obj = "start connect server " + port;
//    handlertxt.sendMessage(msg);
    onPause = false;

    service = Executors.newSingleThreadScheduledExecutor();

    Runnable setFlag = () -> sendFlag = true;

    service.scheduleWithFixedDelay(setFlag, 0, 1, TimeUnit.SECONDS);

}

    public void writePause(){
        onPause = true;
    }
    public void stopProcess(){
        process = false;
        Log.d(LOG_TAG, "stopProcess 1 " + port);
//        try {
//            if(out != null) {
//                out.close();
//                out = null;
//            }
//        } catch (Exception e) {
//            Log.e(LOG_TAG, "error out.close()   " + e.getMessage() + port);
//        }
//        Log.d(LOG_TAG, "stopProcess 2 " + port);
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
            serverSocket.setReuseAddress(true);
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
                handler.sendEmptyMessage(2);    //включаем енкодер при соединении (если он ещё не был включен)
                Log.i(LOG_TAG, "handler.sendEmptyMessage");
                msg = handlertxt.obtainMessage();
                msg.obj = "accept connect server " + port;
                handlertxt.sendMessage(msg);
            }catch(IOException e) {
                Log.e(LOG_TAG, "connection server accept error " + e.getMessage() + port);
            }
            while(process) {
                //Log.i(LOG_TAG, "process");
//                if(onPause){
//                    onPause = false;
//                    try {
//                        out.write("onPause".getBytes());
//                    } catch (IOException e) {
//                        Log.e(LOG_TAG, "onPause write error " + e.getMessage());
//                    }
//                }
                byte[] b = new byte[10];
                //int ret;
                try {
                    int bytesForRead = in.available();

                    //out.write("!".getBytes());
                    if(sendFlag) {
                        out.write("!".getBytes());
                        out.flush();
                        sendFlag = false;
                        msg = handlertxt.obtainMessage();
                        msg.obj = " sendFlag ";
                        handlertxt.sendMessage(msg);
                    }

                   if(bytesForRead > 0) {
                       //ret = in.read(b, 0, bytesForRead);
                       handler.sendEmptyMessage(1);    //выключаем енкодер если пришло любое сообщение
                       //handler.sendEmptyMessageDelayed(1, 0);
                       Log.i(LOG_TAG, "in.available() = " + bytesForRead);
                       break;
                   }
//                    msg.obj = "off encoder " + port;
//                    handlertxt.sendMessage(msg);

                    //Log.i(LOG_TAG, "ret = " + ret + Arrays.toString(b));
                } catch (IOException e) {
                    Log.e(LOG_TAG, "read connection error " + e.getMessage());
//                    msg.obj = "handler.sendEmptyMessage read connection error " + port + " 2";
                    msg = handlertxt.obtainMessage();
                    msg.obj = " connection error " + port;
                    handlertxt.sendMessage(msg);
                    handler.sendEmptyMessage(1);    //выключаем енкодер
                    break;
                }
            }

            try {
                if (in != null) {
                    in.close();
                }
//                if (out != null) {
//                    out.close();
//                }
                if(clientSocket != null) {
                    clientSocket.close();
                    msg = handlertxt.obtainMessage();
                    msg.obj = "close server " + port;
                    handlertxt.sendMessage(msg);
                }
            } catch (IOException e) {
                Log.e(LOG_TAG, "error clientSocket.close() " + e.getMessage() + port);
            }
            Log.i(LOG_TAG, "connection read error " + port);
        }
        try {
            if (in != null) {
                in.close();
            }
//            if (out != null) {
//                out.close();
//            }
            if(clientSocket != null)
                clientSocket.close();
            serverSocket.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "error clientSocket.close() " + e.getMessage() + port);
        }
        Log.i(LOG_TAG, "connection server exit " + port);
    }

}
