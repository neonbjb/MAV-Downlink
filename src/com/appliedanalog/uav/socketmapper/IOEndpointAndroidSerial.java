package com.appliedanalog.uav.socketmapper;

import java.io.IOException;
import java.util.concurrent.Semaphore;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.appliedanalog.uav.socketmapper.EndpointDataListener;
import com.appliedanalog.uav.socketmapper.IOEndpoint;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;

public class IOEndpointAndroidSerial implements IOEndpoint, Runnable {
	final String TAG = "AAUSB";
	final int BAUD_RATE = 115200;
    UsbSerialDriver serialDriver = null;
    Context context;
    Semaphore startSem = new Semaphore(1);
    
    EndpointDataListener endpointConnection;
    EndpointListener endpointListener;
    
    Thread myThread;
    boolean running = false;
    
    public IOEndpointAndroidSerial(Context cont){
    	context = cont;
    }

    final int MAX_ERRORS_BEFORE_DEAD = 5;
    int errCount = 0;
	@Override
	public void writeData(byte[] buf, int sz) {
		if(serialDriver == null){
			Log.e(TAG, "Error: Initialize not called. (wd)");
			return;
		}
		try{
			serialDriver.write(buf, sz);
            if(endpointListener != null) endpointListener.messageSent();
		}catch (IOException e) {
			Log.e(TAG, "Error Sending: " + e.getMessage(), e);
			errCount++;
			if(errCount > MAX_ERRORS_BEFORE_DEAD){
				stop();
			}
		}
	}
	
    public void run(){
    	Log.v(TAG, "AndroidSerial reader started up.");
        byte[] buf = new byte[512];
    	while(running){
            try{
            	int bytesRead = serialDriver.read(buf, 200);
                if(bytesRead <= 0){
                    continue;
                }
                if(endpointConnection == null){
                    System.out.println("Received " + bytesRead + " bytes in the serial port but no listener.");
                    continue;
                }
                endpointConnection.dataReceived(buf, bytesRead);
            }catch(Exception e){
                //e.printStackTrace();
            }
        }
        running = false;
    }

	@Override
	public void setDataListener(EndpointDataListener listener) {
		endpointConnection = listener;
	}

	public void setEndpointListener(EndpointListener listener){
		endpointListener = listener;
	}
	
	@Override
	public void start(){
		try{
			startSem.acquire();
			if(running) return;
			
			// Get UsbManager from Android.
			UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
			
			// Find the first available driver.
			//**TODO: We should probably step through all available USB Devices
			//...but its unlikely to happen on a Phone/tablet running DroidPlanner.
			serialDriver = UsbSerialProber.findFirstDevice(manager);
			
			if (serialDriver == null) {
				Log.e(TAG, "No Devices found");
				return;
			}
			else
			{	
				Log.v(TAG, "Opening using Baud rate " + BAUD_RATE);
				try {
				    serialDriver.open();
				    serialDriver.setParameters(BAUD_RATE, 8, UsbSerialDriver.STOPBITS_1, UsbSerialDriver.PARITY_NONE);
				} catch (IOException e) {
				    Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
					try {
					    serialDriver.close();
					} catch (IOException e2) {
					    // Ignore.
					}
					serialDriver = null;
					return;
				}
			}
			running = true;
			myThread = new Thread(this);
			myThread.start();
			if(endpointListener != null) endpointListener.connected();
		}catch(InterruptedException ie){}finally{
			startSem.release();
		}
	}
	
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
					if(!running){
						Log.v(TAG, "Keep alive attempting to restart serial connection.");
						start();
					}
					try{
						Thread.sleep(1000);
					}catch(Exception e){}
				}
			}
		}, "IOEndpointAndroidSerial Keepalive");
		keepAliveThread.start();
	}
	
	public void stopKeepAlive(){
		keepAliveRunning = false;
		keepAliveThread.interrupt(); // Probably dont need to join here..
	}

	@Override
	public void stop() {
    	if(serialDriver != null){
    		try{
    	    	running = false;
    			serialDriver.close();
    	    	myThread.interrupt();
    	    	myThread.join();
    		}catch(Exception e){}
    		serialDriver = null;
    		if(endpointListener != null) endpointListener.disconnected();
    	}
	}
}
