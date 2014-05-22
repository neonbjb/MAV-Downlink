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
    EndpointDataListener endpointConnection;
    EndpointListener endpointListener;
    
    public IOEndpointSocket(){
        socket = null;
        running = false;
    }
    
    /**
     * Called to bind this framework to a newly established socket
     * link.
     * @param sock TCP socket link.
     */
    public void bind(Socket sock){
        if(running) return;
        socket = sock;
    }
    
    public boolean connected(){
        return socket != null && socket.isConnected();
    }

    @Override
    public void setDataListener(EndpointDataListener edl) {
        endpointConnection = edl;
    }
    
    public void setEndpointListener(EndpointListener listener){
    	endpointListener = listener;
    }
    
    @Override
    public void writeData(byte[] data, int len) {
        if(socket == null) return;
        try{
            OutputStream os = socket.getOutputStream();
            os.write(data, 0, len);
            os.flush();
        	if(endpointListener != null) endpointListener.messageSent();
        }catch(Exception e){
        	if(running){ // If we're not running, then this is probably just a timing problem.
        		e.printStackTrace();
        	}
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
                if(endpointConnection != null){
                    endpointConnection.dataReceived(data, len);
                }
            }
        }catch(Exception e){
            if(running){ // Suppress error output when !running - as the error is most likely from a purposeful interrupt.
            	e.printStackTrace();
            }
        }
        running = false;
        socket = null;
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
        if(endpointListener != null) endpointListener.connected();
    }

    @Override
    public void stop() {
    	if(!running) return;
        running = false;
        if(socket != null){
	        try{
	            socket.close();
	            myThread.interrupt();
	            myThread.join();
	            socket = null;
	        }catch(Exception e){
	            e.printStackTrace();
	        }
        }
        if(endpointListener != null) endpointListener.disconnected();
    }
}
