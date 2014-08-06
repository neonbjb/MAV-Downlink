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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Offers an IOEndpoint as a TCP server.
 */
public class IOEndpointServer extends IOEndpointSocket{
    ServerSocket server;
    Thread serverThread;
    boolean serverRunning = false;
    ServerEndpointConnectionListener connectionListener;
    
    public IOEndpointServer(int port) throws IOException{
        server = new ServerSocket(port);
    }
    
    public void setConnectionListener(ServerEndpointConnectionListener secl){
        connectionListener = secl;
    }
    
    /**
     * In a bit of tricksy undertaking, this class overrides start() and uses it to start
     * up a TCP server. When a connection is established, the underlying IOEndpointSocket
     * class is initialized and start is called on it.
     */
    @Override
    public void start() {
        serverRunning = true;
        serverThread = new Thread(){
            public void run(){
                while(serverRunning){
                    if(connected()){
                        // Only one connection at a time supported, wait a try again later.
                        try{
                            Thread.sleep(100);
                        }catch(Exception e){}
                    }else{
                        try{
                            connectionListener.clientDisconnected();
                            Socket sock = server.accept();
                            System.out.println("Client connected.");
                            if(connectionListener != null){
                                connectionListener.clientConnected();
                            }
                            // Bind the underlying IOEndpointSocket to this guy.
                            bind(sock);
                        }catch(Exception e){
                            e.printStackTrace();
                        }
                    }
                }
            }
        };
        serverThread.start();
    }

    @Override
    public void stop() {
        super.stop();
        serverRunning = false;
        try{
            serverThread.interrupt();
            serverThread.join();
            server.close();
            server = null;
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
}
