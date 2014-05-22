package com.appliedanalog.uav.socketmapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Implements an IO endpoint using a network socket.
 */
public class IOEndpointSocket implements IOEndpoint, Runnable{
    Socket socket;
    boolean running;
    Thread myThread;
    EndpointDataListener listener;
    
    public IOEndpointSocket(Socket sock){
        socket = sock;
        running = false;
    }
    
    /**
     * Late initialization offered to deriving classes so a server interface can be implemented.
     */
    protected IOEndpointSocket(){
        socket = null;
        running = false;
    }
    
    /**
     * Called by superclasses to bind this framework to a newly established socket
     * link.
     * @param sock TCP socket link.
     */
    protected void bind(Socket sock){
        if(running) return;
        socket = sock;
        socketEndpointStart();
    }
    
    public boolean connected(){
        return socket != null && socket.isConnected();
    }
    
    @Override
    public void writeData(byte[] data, int len) {
        if(socket == null) return;
        try{
            OutputStream os = socket.getOutputStream();
            os.write(data, 0, len);
            os.flush();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    public void run(){
        try{
            while(running){
                InputStream sockIn = socket.getInputStream();
                byte[] data = new byte[255];
                int len = sockIn.read(data);
                if(len < 0){
                    break;
                }
                if(listener != null){
                    listener.dataReceived(data, len);
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        running = false;
        socket = null;
    }

    @Override
    public void setDataListener(EndpointDataListener edl) {
        listener = edl;
    }

    @Override
    public void start() {
        socketEndpointStart();
    }
    
    void socketEndpointStart(){
        if(running) return;
        running = true;
        myThread = new Thread(this);
        myThread.start();
    }

    @Override
    public void stop() {
        if(!running || socket == null) return;
        running = false;
        myThread.interrupt();
        try{
            myThread.join();
            socket.close();
            socket = null;
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
}
