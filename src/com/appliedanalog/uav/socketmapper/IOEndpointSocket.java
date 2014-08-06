/*
	MAV Downlink - A MAVLink Interface App for Android Smartphones
	Copyright (C) 2014 James Betker, Applied Analog LLC
	
	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.
	
	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
	GNU General Public License for more details.
	
	You should have received a copy of the GNU General Public License
	along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.appliedanalog.uav.socketmapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import android.util.Log;

/**
 * Implements an IO endpoint using a network socket.
 */
public class IOEndpointSocket extends IOEndpoint implements Runnable{
    Socket socket;
    boolean running;
    Thread myThread;
    
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
    
    String boundIp = null;
    int boundPort = -1;
    public void bind(String ip, int port){
    	boundIp = ip;
    	boundPort = port;
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
        stopEndpoint();
    }

    @Override
    public void start() {
    	// This is tricky because the IOEndpointSocket has two methods for starting up. It can use the bound socket or
    	// the bound IP address. The socket takes precedence.
    	if(socket == null && boundIp != null){
    		//attempt reconnect using the ip address if available.
    		try{
    			Log.v(TAG, "No socket bound. Attepting to connect with given IP address.");
    			socket = new Socket(boundIp, boundPort);
    		}catch(Exception e){
    			Log.e(TAG, "Failed to start up socket endpoint due to socket error: " + e.getMessage());
    		}
    	}
        socketEndpointStart();
    }
    
    void socketEndpointStart(){
        if(running || socket == null) return;
        running = true;
        myThread = new Thread(this);
        myThread.start();
        if(endpointListener != null) endpointListener.connected();
    }

    @Override
    public void stopEndpoint() {
    	if(!running) return;
        running = false;
        if(socket != null){
	        try{
	            socket.close();
	            socket = null;
	            if(myThread.isAlive()){
	            	myThread.interrupt();
		            myThread.join();
	            }
	        }catch(Exception e){
	            e.printStackTrace();
	        }
        }
        if(endpointListener != null) endpointListener.disconnected();
    }
    
    public void changePort(int newPort){
    	boundPort = newPort;
    	if(running){
        	stopEndpoint();
        	start();
    	}
    }
	
	@Override
	public boolean isActive(){
		return running;
	}
}
