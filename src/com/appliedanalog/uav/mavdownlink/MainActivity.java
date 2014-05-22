package com.appliedanalog.uav.mavdownlink;

import java.net.Socket;

import com.appliedanalog.uav.mavdownlink.R;
import com.appliedanalog.uav.socketmapper.EndpointConnector;
import com.appliedanalog.uav.socketmapper.EndpointListener;
import com.appliedanalog.uav.socketmapper.IOEndpointAndroidSerial;
import com.appliedanalog.uav.socketmapper.IOEndpointSocket;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class MainActivity extends Activity {
	final String TAG = "MavDownlink";
	Context me;
	Handler uiHandler;
	EditText tServerIP;
	EditText tServerPort;
	TextView lNumberMessages;
	ImageView iMavStatus;
	ImageView iDownlinkStatus;
	Button bStart;
	boolean downlinkActive = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		me = this;
		
		// Setup UI variables
		tServerIP = (EditText)findViewById(R.id.tServerIP);
		tServerPort = (EditText)findViewById(R.id.tServerPort);
		bStart = (Button)findViewById(R.id.bEnableMapper);
		bStart.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0) {
				String ip = tServerIP.getText().toString();
				String port = tServerPort.getText().toString();
				toggleMapper(ip, port);
			}
		});
		lNumberMessages = (TextView)findViewById(R.id.tMessageCount);
		iMavStatus = (ImageView)findViewById(R.id.iMavLinkStatus);
		iDownlinkStatus = (ImageView)findViewById(R.id.iDownlinkStatus);
		
		tServerIP.setText("neonbjb.noip.me");
		tServerPort.setText("9999");
		uiHandler = new UIHandler(Looper.getMainLooper());
		
		initializeLinkComponents();
	}
	
	@Override
	protected void onDestroy(){
		super.onDestroy();
		connector.unbridge();
		socketEndpoint.stop();
		serialEndpoint.stopKeepAlive();
		serialEndpoint.stop();
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
	}
	
	IOEndpointAndroidSerial serialEndpoint;
	IOEndpointSocket socketEndpoint;
	EndpointConnector connector;
	String last_host, last_port;
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
			setDownlinkActiveState(false);
		}
	}
	
	void initializeLinkComponents(){
		serialEndpoint = new IOEndpointAndroidSerial(this);
		serialEndpoint.setEndpointListener(new MavEndpointListener());
		serialEndpoint.startKeepAlive();
		socketEndpoint = new IOEndpointSocket();
		socketEndpoint.setEndpointListener(new DownlinkEndpointListener());
		
		connector = new EndpointConnector(serialEndpoint, socketEndpoint);
	}
	
	void toggleMapper(final String host, final String port){
		(new Thread(){
			public void run(){
    			number_messages = 0;
				if(!downlinkActive){
			        try{
		    			if(!socketEndpoint.connected() || !host.equals(last_host) || !port.equals(last_port)){
		    				//We'll need to re-connect.
		    				if(socketEndpoint.connected()){
		    					shortToast("Reconnecting..");
		    					socketEndpoint.stop();
		    				}
		    				last_host = null;
		    				last_port = null;

			    			Log.v(TAG, "Connecting to " + host + " on port " + port);
			    			Socket sock = null;
			    			try{
			    				sock = new Socket(host, Integer.parseInt(port));
			    			}catch(Exception e){}
			    			if(sock != null && sock.isConnected()){
				    			socketEndpoint.bind(sock);
				    			socketEndpoint.start();
			    				last_host = host;
			    				last_port = port;
			    			}else{
			    				Log.v(TAG, "Error connecting.");
			    				shortToast("Error connecting to MAV downlink server.");
			    			}
		    			}else{
		    				Log.v(TAG, "Socket already connected to this host, re-opening bridge.");
		    			}
		    			
			    		connector.bridge();
			    		Log.v(TAG, "Connections bridged");
			    		shortToast("Downlink connection established.");
			    		last_host = host;
			    		last_port = port;
			        }catch(Exception e){
			            e.printStackTrace();
			        }
				}else{
					connector.unbridge();
					Log.v(TAG, "Shutting down socket endpoint..");
					socketEndpoint.stop();
					Log.v(TAG, "Should be shut down now.");
				}
			}
		}).start();
	}
}
