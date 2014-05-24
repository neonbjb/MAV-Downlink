package com.appliedanalog.uav.socketmapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.appliedanalog.uav.socketmapper.IOEndpoint;
import com.hoho.android.usbserial.driver.CommonUsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;

public class IOEndpointAndroidSerial extends IOEndpoint implements Runnable {
	final String TAG = "MavDownlink";
	final int BAUD_RATE = 115200;
	CommonUsbSerialDriver serialDriver = null;
	String serialDriverUsbName = null;
	
    Context context;
    Semaphore startSem = new Semaphore(1);
    
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
				stopEndpoint();
			}
		}
	}
	
	long timeLastBytesReceived = 0;
    public void run(){
    	Log.v(TAG, "AndroidSerial reader started up.");
        byte[] buf = new byte[512];
    	while(running){
            try{
            	int bytesRead = serialDriver.read(buf, 200);
                if(bytesRead <= 0){
                	long sinceLastRxed = System.currentTimeMillis() - timeLastBytesReceived;
                	if(timeLastBytesReceived != 0 && sinceLastRxed > 1000){
                		//start checking to see if the driver is still around..
                		Log.v(TAG, "Haven't received anything from the device in awhile.. checking to see if it is still attached.");
                		if(!checkUsbDriverStillPresent()){
                			Log.v(TAG, "USB serial device appears to have been detached. Stopping serial endpoint.");
                			stopEndpoint();
                			break;
                		}
                		timeLastBytesReceived = System.currentTimeMillis();
                	}
                    continue;
                }
                timeLastBytesReceived = System.currentTimeMillis();
                if(endpointConnection == null){
                    System.out.println("Received " + bytesRead + " bytes in the serial port but no listener.");
                    continue;
                }
                endpointConnection.dataReceived(buf, bytesRead);
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        running = false;
    }
	
	/**
	 * Probes available USB devices and sets serialDriver and serialDriverUsbName if available.
	 */
	protected void bindToUsbDevice(){
		UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
		HashMap<String, UsbDevice> deviceMap = manager.getDeviceList();
		
		// Find the first available driver.
		final UsbSerialDriver driver = UsbSerialProber.findFirstDevice(manager);
		serialDriver = (CommonUsbSerialDriver)driver;
		serialDriverUsbName = null;
		if(serialDriver == null){
			return;
		}
		UsbDevice device = serialDriver.getDevice();
		
		//Reverse engineer the USB name for presence testing in the future.
		for(String key : deviceMap.keySet()){
			if(deviceMap.get(key).equals(device)){
				serialDriverUsbName = key;
				break;
			}
		}
		if(serialDriverUsbName == null){
			Log.e(TAG, "Could not find the USB name from the serial driver we just got... what?");
		}
	}
	
	protected boolean checkUsbDriverStillPresent(){
		if(serialDriverUsbName == null){
			Log.e(TAG, "Trying to check if the USB serial driver is still in the device list but we arent bound to a driver..");
			return false;
		}
		UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
		HashMap<String, UsbDevice> deviceMap = manager.getDeviceList();
		return deviceMap.containsKey(serialDriverUsbName);
	}
	
	@Override
	public void start(){
		try{
			startSem.acquire();
			if(running) return;
			
			bindToUsbDevice();
			
			if (serialDriver == null) {
				Log.e(TAG, "No USB serial devices found");
				return;
			}
			else
			{	
				Log.v(TAG, "Opening using Baud rate " + BAUD_RATE);
				try {
				    serialDriver.open();
				    UsbDevice dev = serialDriver.getDevice();
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

	@Override
	public void stopEndpoint() {
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
	
	@Override
	public boolean isActive(){
		return running;
	}
}
