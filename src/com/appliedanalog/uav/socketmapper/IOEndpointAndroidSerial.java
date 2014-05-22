package com.appliedanalog.uav.socketmapper;

import java.io.IOException;

import android.content.Context;
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
    EndpointDataListener endpointListener;
    
    Thread myThread;
    boolean running = false;
    
    public IOEndpointAndroidSerial(Context cont){
    	context = cont;
    }

	@Override
	public void writeData(byte[] buf, int sz) {
		if(serialDriver == null){
			Log.e(TAG, "Error: Initialize not called. (wd)");
			return;
		}
		try{
			serialDriver.write(buf, sz);
		}catch (IOException e) {
			Log.e(TAG, "Error Sending: " + e.getMessage(), e);
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
                if(endpointListener == null){
                    System.out.println("Received " + bytesRead + " bytes in the serial port but no listener.");
                    continue;
                }
                endpointListener.dataReceived(buf, bytesRead);
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        running = false;
    }

	@Override
	public void setDataListener(EndpointDataListener listener) {
		endpointListener = listener;
	}

	@Override
	public void start() {
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
	}

	@Override
	public void stop() {
    	if(serialDriver != null){
    		try{
    	    	running = false;
    	    	myThread.interrupt();
    	    	myThread.join();
    			serialDriver.close();
    		}catch(Exception e){}
    		serialDriver = null;
    	}
	}

}
