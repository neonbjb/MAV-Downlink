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

package com.appliedanalog.uav.mavdownlink;

import com.appliedanalog.uav.mavdownlink.R;
import com.appliedanalog.uav.socketmapper.EndpointConnector;
import com.appliedanalog.uav.socketmapper.EndpointListener;
import com.appliedanalog.uav.socketmapper.IOEndpointAndroidSerial;
import com.appliedanalog.uav.socketmapper.IOEndpointSocket;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceActivity;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	final String TAG = "MavDownlink";
	Activity me;
	Handler uiHandler;
	EditText tServerIP;
	TextView lNumberMessages;
	ImageView iMavStatus;
	ImageView iDownlinkStatus;
	Button bStart;
	boolean downlinkActive = false;
	Intent startIntent;
	WakeLock screenWakelock = null;
	WakeLock cpuWakelock = null;
	
	int connectedPort = 9999; //The port that we are connected to.
	
	//TODO: Move all of these to settings.
	// When the screen wakelock is used, this is necessary to prevent unintentional turn-offs.
	final boolean onlyDisableOnLongPress = true;
	final int wakelockType = PowerManager.SCREEN_DIM_WAKE_LOCK;
	
	//Shared preferences keys
	final String USER_DFL_IP = "UserDefaultIP";
	final String TCP_PORT = "tcp_port";
	final String BAUD_RATE = "baud_rate";
	final String SCREEN_KEEPALIVE = "keep_screen_on";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.v(TAG, "onCreate()");
		setContentView(R.layout.activity_main);
		me = this;
		
		startIntent = this.getIntent();
		
		// Setup UI variables
		tServerIP = (EditText)findViewById(R.id.tServerIP);
		bStart = (Button)findViewById(R.id.bEnableMapper);
		bStart.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0) {
				String ip = tServerIP.getText().toString();
				
				//save to shared prefs
				SharedPreferences.Editor ed = me.getPreferences(0).edit();
				ed.putString(USER_DFL_IP, ip);
				ed.commit();
				
				toggleMapper(ip, false);
				Log.v(TAG, "Start downlink - regular click.");
			}
		});
		bStart.setOnLongClickListener(new OnLongClickListener(){
			@Override
			public boolean onLongClick(View arg0) {
				String ip = tServerIP.getText().toString();
				toggleMapper(ip, true);
				return true;
			}
		});
		lNumberMessages = (TextView)findViewById(R.id.tMessageCount);
		iMavStatus = (ImageView)findViewById(R.id.iMavLinkStatus);
		iDownlinkStatus = (ImageView)findViewById(R.id.iDownlinkStatus);
		
		// Load default text field values
		SharedPreferences prefs = this.getPreferences(0);
		tServerIP.setText(prefs.getString(USER_DFL_IP, ""));
		
		// Initialize wake locks
		PowerManager powerManager = (PowerManager)getSystemService(POWER_SERVICE);
		screenWakelock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "MAVDownlinkScreenWakelock");
		cpuWakelock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MAVDownlinkCPUWakelock");
		
		uiHandler = new UIHandler(Looper.getMainLooper());
		
		initializeLinkComponents();
	}
	
	@Override
	public void onStart(){
		super.onStart();
		Log.v(TAG, "onStart()");
		
		if(!startIntent.getAction().equals(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED)){
			alert("Please launch this application by plugging the APM into the phone and selecting "
					+ "'MAV Downlink' in the dialog that appears. The downlink cannot be used when launched manually.");
			bStart.setEnabled(false);
		}else{
			bStart.setEnabled(true);
		}
	}
	
	@Override
	protected void onDestroy(){
		super.onDestroy();
		Log.v(TAG, "onDestroy()");
		if(downlinkActive){
			stopEndpoints();
		}
		releaseWakelocks();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	/**
	 * Handles actions on the UI thread
	 */
	final int SET_IMG_GREEN = 1;
	final int SET_IMG_RED = 2;
	final int SET_NUM_MESSAGES = 3;
	final int SHOW_SHORT_TOAST = 4;
	final int SET_DOWNLINK_ACTIVE_STATE = 5;
	class UIHandler extends Handler{
		public UIHandler(Looper looper){
			super(looper);
		}
		
		@Override
		public void handleMessage(Message msg){
			switch(msg.what){
			case SET_IMG_GREEN:
			case SET_IMG_RED:
				ImageView view = (ImageView)msg.obj;
				int imgResource = R.drawable.green;
				if(msg.what == SET_IMG_RED){
					imgResource = R.drawable.red;
				}
				view.setImageResource(imgResource);
				break;
			case SET_NUM_MESSAGES:
				lNumberMessages.setText(msg.obj.toString());
				break;
			case SHOW_SHORT_TOAST:
				Toast toast = Toast.makeText(me, msg.obj.toString(), Toast.LENGTH_SHORT);
				toast.show();
				break;
			case SET_DOWNLINK_ACTIVE_STATE:
				bStart.setText(msg.obj.toString());
				break;
			}
		}
	}
	
	// Custom intent types
	final int PREFS_CHANGED = 15;
	
	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
		SharedPreferences prefs = getPreferences(0);
		
		if(requestCode == PREFS_CHANGED)
		{
			int newPort = getPrefsPort();
			if(downlinkActive && socketEndpoint != null && connectedPort != newPort){
				socketEndpoint.changePort(getPrefsPort());
			}
			if(Integer.toString(serialEndpoint.getBaudRate()).equals(prefs.getString(BAUD_RATE, "115200"))){
				Log.v(TAG, "Baud rate changed, exiting application.");
				System.exit(0);
			}
			//Refresh wakelock
			releaseWakelocks();
			acquireProperWakelock();
		}
	}
		
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.action_settings:
        	Intent i = new Intent(this, Settings.class);
			startActivityForResult(i, PREFS_CHANGED);
        	return true;
        default:
            return super.onOptionsItemSelected(item);
        }
	}
	
	//These functions all use the UI handler and thus can be called from outside
	//the UI thread context.
	void sendUIMessage(int what, Object obj){
		Message msg = uiHandler.obtainMessage(what, obj);
		msg.sendToTarget();
	}
	void setGreenLight(ImageView img){
		sendUIMessage(SET_IMG_GREEN, img);
	}
	void setRedLight(ImageView img){
		sendUIMessage(SET_IMG_RED, img);
	}
	void setNumberMessages(int count){
		sendUIMessage(SET_NUM_MESSAGES, Integer.toString(count));
	}
	void shortToast(String msg){
		sendUIMessage(SHOW_SHORT_TOAST, msg);
	}
	void setDownlinkActiveState(boolean active){
		sendUIMessage(SET_DOWNLINK_ACTIVE_STATE, active ? "Stop Downlink" : "Start Downlink");
		downlinkActive = active;
		
		// Manage the wakelock.
		if(downlinkActive){
			acquireProperWakelock();
		}else{
			releaseWakelocks();
		}
	}
	
	IOEndpointAndroidSerial serialEndpoint;
	IOEndpointSocket socketEndpoint;
	EndpointConnector connector;
	int number_messages = 0;
	
	class MavEndpointListener implements EndpointListener{
		@Override
		public void messageSent() {
			number_messages++;
			if(number_messages % 5 == 0){
				setNumberMessages(number_messages);
			}
		}

		@Override
		public void connected() {
			setGreenLight(iMavStatus);
		}

		@Override
		public void disconnected() {
			setRedLight(iMavStatus);
		}
	}
	
	class DownlinkEndpointListener implements EndpointListener{
		@Override
		public void messageSent() {
			number_messages++;
			if(number_messages % 5 == 0){
				setNumberMessages(number_messages);
			}
		}

		@Override
		public void connected() {
			setGreenLight(iDownlinkStatus);
			setDownlinkActiveState(true);
		}

		@Override
		public void disconnected() {
			setRedLight(iDownlinkStatus);
		}
	}
	
	void initializeLinkComponents(){
		SharedPreferences prefs = getPreferences(0);
		int baud = Integer.parseInt(prefs.getString(BAUD_RATE, "115200"));
		serialEndpoint = new IOEndpointAndroidSerial(this, baud);
		serialEndpoint.setEndpointListener(new MavEndpointListener());
		socketEndpoint = new IOEndpointSocket();
		socketEndpoint.setEndpointListener(new DownlinkEndpointListener());
		
		connector = new EndpointConnector(serialEndpoint, socketEndpoint);
	}
	
	void startEndpoints(String host, int port){
		Log.v(TAG, "Toggle downlink ON");
        try{
        	Log.v(TAG, "Booting up socket/downlink endpoint..");
			if(!socketEndpoint.connected()){
				//We'll need to re-connect.
				if(socketEndpoint.connected()){
					shortToast("Reconnecting..");
					socketEndpoint.stop();
				}
    			socketEndpoint.bind(host, port);
    			socketEndpoint.startKeepAlive();
			}else{
				Log.v(TAG, "Socket already connected to this host, re-opening bridge.");
			}
			
			Log.v(TAG, "Booting up FTDI endpoint..");
			serialEndpoint.startKeepAlive();
			
    		connector.bridge();
    		Log.v(TAG, "Connections bridged");
    		shortToast("Downlink connection established.");
			setDownlinkActiveState(true);
        }catch(Exception e){
            e.printStackTrace();
        }
	}
	
	void stopEndpoints(){
		Log.v(TAG, "Toggle downlink OFF");
		connector.unbridge();
		Log.v(TAG, "Shutting down FTDI endpoint..");
		serialEndpoint.stop();
		Log.v(TAG, "Shutting down socket endpoint..");
		socketEndpoint.stop();
		Log.v(TAG, "Should be shut down now.");
		setDownlinkActiveState(false);
	}
	
	int getPrefsPort(){
		SharedPreferences prefs = getPreferences(0);
		int port = 9999;
		try{
			port = Integer.parseInt(prefs.getString(TCP_PORT, "9999"));
		}catch(Exception e){
			e.printStackTrace();
		}
		return port;
	}

	void acquireProperWakelock(){
		SharedPreferences prefs = getPreferences(0);
		if(prefs.getBoolean(SCREEN_KEEPALIVE, true)){
			Log.v(TAG, "Acquiring screen wakelock.");
			screenWakelock.acquire();
		}else{
			Log.v(TAG, "Acquiring CPU wakelock.");
			cpuWakelock.acquire();
		}
	}
	
	void releaseWakelocks(){
		Log.v(TAG, "Releasing wakelocks.");
		screenWakelock.release();
		cpuWakelock.release();
	}
	
	void toggleMapper(final String host, final boolean longPress){
		if(downlinkActive && onlyDisableOnLongPress && !longPress){
			alert("You must longpress the button to disable the downlink.");
			return;
		}
		final int port = getPrefsPort();
		
		(new Thread(){
			public void run(){
    			number_messages = 0;
				if(!downlinkActive){
					connectedPort = port;
					startEndpoints(host, port);
				}else if(!onlyDisableOnLongPress || longPress){
					stopEndpoints();
				}
			}
		}).start();
	}
	
	String _alert_text; //vulnerable to threading..
    void alert(String msg){
    	_alert_text = msg;
    	showDialog(DIALOG_ALERT);
    }
    final int DIALOG_ALERT = 0;
    protected Dialog onCreateDialog(int id){
    	Dialog dialog = null;
    	switch(id){
    	case DIALOG_ALERT:
        	AlertDialog.Builder builder = new AlertDialog.Builder(this);
        	builder.setMessage(_alert_text)
        		   .setPositiveButton("OK", new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog, int which) { }
    			});
        	dialog = builder.create();
        	break;
    	}
    	return dialog;
    }
}
