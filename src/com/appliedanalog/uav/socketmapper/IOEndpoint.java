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

import android.util.Log;

/**
 * Abstract class for any interface capable of producing IO that can be bridged
 * with another such interface.
 * @author betker
 */
public abstract class IOEndpoint {
	final String TAG = "IOEndpoint";
	protected EndpointDataListener endpointConnection;
    protected EndpointListener endpointListener;
    boolean keepaliveRunning = false;
    final long KEEPALIVE_CHECK_INTERVAL = 1000;
    
    /**
     * Applies a listener to this endpoint that will be notified of all received
     * data. Endpoints are responsible for maintaining their own internal receivers
     * and must notify the outside application of data events using this data listener.
     * @param edl Data listener to use to report input. 
     */
	public void setDataListener(EndpointDataListener listener) {
		endpointConnection = listener;
	}

	public void setEndpointListener(EndpointListener listener){
		endpointListener = listener;
	}
    
    /**
     * Write data out of the interface.
     * @param data Data
     * @param len Length of data to actually send in bytes.
     */
    public abstract void writeData(byte[] data, int len);
    

    
    /**
     * Notifies the endpoint to begin listening for data and producing data.
     */
    public abstract void start();
    
    /**
     * Notifies the endpoint to stop listening for data, close IO connections and 
     * halt any threads that are used to support it. IOEndpoints will not be started
     * after they are stopped.
     */
    public abstract void stopEndpoint();
    
    /**
     * Returns whether or not the Endpoint is ready to send and receive IO.
     * @return
     */
    public abstract boolean isActive();
    
	/**
	 * Special method that attempts to start this adapter but also spawns a thread that will continuously 
	 * re-attempt to connect if initial connection fails or ever goes dead until it is specifically stopped.
	 */
	Thread keepAliveThread = null;
	boolean keepAliveRunning = false;
	public void startKeepAlive(){
		Log.v(TAG, "startKeepAlive()");
		keepAliveThread = new Thread(new Runnable(){
			public void run(){
				keepAliveRunning = true;
				while(keepAliveRunning){					
					if(!isActive()){
						Log.v(TAG, "Keep alive attempting to restart connection for " + toString());
						start();
					}
					try{
						Thread.sleep(KEEPALIVE_CHECK_INTERVAL);
					}catch(Exception e){}
				}
			}
		}, "IOEndpoint Keepalive");
		keepAliveThread.start();
	}
	
	/**
	 * Stops the keepalive thread.
	 */
	public void stopKeepAlive(){
		if(!keepAliveRunning) return;
		keepAliveRunning = false;
		keepAliveThread.interrupt(); // Probably dont need to join here..
	}
    
    public void stop(){
    	stopKeepAlive();
    	stopEndpoint();
    }
}
