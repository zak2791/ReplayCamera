package com.example.replaycamera;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;

public class VideoAudioServer extends Thread{
    static final String LOG_TAG_SERVER = "serverLogs";
    ServerSocket serverSocket;
    private Socket clientSocket; //сокет для общения
    private boolean process = true;
    BufferedOutputStream out = null;
    BufferedInputStream in = null;
    int port;

    Message msg;
    Handler handler;
    BlockingQueue<byte[]> queue;
    VideoAudioServer(int _port, BlockingQueue<byte[]> _queue, Handler h){
        port = _port;
        queue = _queue;
        handler = h;
        msg = handler.obtainMessage();
        Log.d(LOG_TAG_SERVER, "start server " + port);msg = handler.obtainMessage();
        msg.obj = " start server " + port;
        handler.sendMessage(msg);

    }
    public void stopProcess(){
        process = false;
        Log.d(LOG_TAG_SERVER, "stopProcess 1 " + port);
        try {
            if(out != null) {
                out.close();
                out = null;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG_SERVER, "error out.close()   " + e.getMessage() + port);
        }
        //Log.d(LOG_TAG_SERVER, "stopProcess 2 " + port);
        try {
            if(clientSocket != null) {
                clientSocket.close();
                clientSocket = null;
//                msg = handler.obtainMessage();
//                msg.obj = "clientSocket.close() " + port;
//                handler.sendMessage(msg);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG_SERVER, "error clientSocket.close()   " + e.getMessage() + port);
        }
        //Log.d(LOG_TAG_SERVER, "stopProcess 3 " + port);
        if(serverSocket != null) {
            try {
                serverSocket.close();
                Log.e(LOG_TAG_SERVER, "audio server stop   "  + port);
            } catch (Exception e) {
                Log.e(LOG_TAG_SERVER, "error audio serverSocket.close()  " + e.getMessage() + port);
            }
        }
        Log.d(LOG_TAG_SERVER, "stopProcess 4 " + port);
    }
    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress("127.0.0.1", port), 10);
            Log.i(LOG_TAG_SERVER, "server bind " + port);
            msg = handler.obtainMessage();
            msg.obj = " server bind " + port;
            handler.sendMessage(msg);
        }catch(IOException e) {
            Log.e(LOG_TAG_SERVER, "server error bind " + e.getMessage() + port);
            return;
        }
        while(process) {
            try {
                clientSocket = serverSocket.accept();
                Log.i(LOG_TAG_SERVER, "accept connect server " + port);

//                msg.obj = "accept connect server " + port;
//                handler.sendMessage(msg);

                in = new BufferedInputStream(clientSocket.getInputStream());
                out = new BufferedOutputStream(clientSocket.getOutputStream());
            }catch(IOException e) {
                Log.e(LOG_TAG_SERVER, " accept error " + e.getMessage() + port);
                msg = handler.obtainMessage();
                msg.obj = " accept error " + e.getMessage() + port;
                handler.sendMessage(msg);
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
                        Log.e(LOG_TAG_SERVER, "error send video data " + e.getMessage());
//                        msg.obj = "error send video data " + e.getMessage();
//                        handler.sendMessage(msg);
                        try {
                            clientSocket.close();
                        } catch (IOException ex) {
                            Log.e(LOG_TAG_SERVER, "error close clieny socket " + e.getMessage());
                        }
                        break;
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
            if(clientSocket != null) {
                clientSocket.close();
                msg = handler.obtainMessage();
                msg.obj = "clientSocket.close() 2 " + port;
                handler.sendMessage(msg);
            }
            serverSocket.close();
            msg = handler.obtainMessage();
            msg.obj = "serverSocket.close()  " + port;
            handler.sendMessage(msg);
        } catch (IOException e) {
            Log.e(LOG_TAG_SERVER, "error video clientSocket.close() " + e.getMessage() + port);
        }
        Log.i(LOG_TAG_SERVER, "video server exit " + port);
    }
}
