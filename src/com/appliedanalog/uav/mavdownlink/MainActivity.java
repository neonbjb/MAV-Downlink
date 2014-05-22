package com.appliedanalog.uav.mavdownlink;

import java.net.Socket;

import com.appliedanalog.uav.mavdownlink.R;
import com.appliedanalog.uav.socketmapper.EndpointConnector;
import com.appliedanalog.uav.socketmapper.EndpointListener;
import com.appliedanalog.uav.socketmapper.IOEndpointAndroidSerial;
import com.appliedanalog.uav.socketmapper.IOEndpointSocket;

import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends Activity {
	final String TAG = "MavDownlink";
	EditText tServerIP;
	EditText tServerPort;
	ToggleButton bStart;
	boolean serverEnabled = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// Setup UI variables
		tServerIP = (EditText)findViewById(R.id.tServerIP);
		tServerPort = (EditText)findViewById(R.id.tServerPort);
		bStart = (ToggleButton)findViewById(R.id.bEnableMapper);
		bStart.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0) {
				String ip = tServerIP.getText().toString();
				String port = tServerPort.getText().toString();
				toggleMapper(ip, port);
			}
		});
		
		tServerIP.setText("neonbjb.noip.me");
		tServerPort.setText("9999");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	IOEndpointAndroidSerial serialEndpoint;
	IOEndpointSocket socketEndpoint;
	EndpointConnector connector;
	String last_host, last_port;
	Context me;
	
	class MavEndpointListener implements EndpointListener{
		@Override
		public void externalMessageReceived() {
			
		}

		@Override
		public void connected() {
			
		}

		@Override
		public void disconnected() {
			
		}
	}
	
	class DownlinkEndpointListener implements EndpointListener{
		@Override
		public void externalMessageReceived() {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void connected() {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void disconnected() {
			// TODO Auto-generated method stub
			
		}
	}
	
	void toggleMapper(final String host, final String port){
		me = this;
		(new Thread(){
			public void run(){
				serverEnabled = !serverEnabled;
				if(serverEnabled){
			        try{
			    		if(serialEndpoint == null){
			    			Log.v(TAG, "Connecting to " + host + " on port " + port);
			    			serialEndpoint = new IOEndpointAndroidSerial(me);
			    			socketEndpoint = new IOEndpointSocket(new Socket(host, Integer.parseInt(port)));
			    			connector = new EndpointConnector(serialEndpoint, socketEndpoint);
			    			serialEndpoint.start();
			    			socketEndpoint.start();
			    		}else if(socketEndpoint == null || !socketEndpoint.connected() || !host.equals(last_host) || !port.equals(last_port)){
			    			Log.v(TAG, "New IP address specified or old client disconnected. Reconnecting to " + host + " on " + port);
			    			socketEndpoint.stop();
			    			Log.v(TAG, "Old socket powered down. Reconnecting....");
			    			socketEndpoint = new IOEndpointSocket(new Socket(host, Integer.parseInt(port)));
			    			connector.unbridge();
			    			connector = new EndpointConnector(serialEndpoint, socketEndpoint);
			    			socketEndpoint.start();
			    		}
			    		
			    		connector.bridge();
			    		Log.v(TAG, "Connections bridged");
			    		last_host = host;
			    		last_port = port;
			        }catch(Exception e){
			            e.printStackTrace();
			        }
				}else{
					if(connector != null){
						connector.unbridge();
						socketEndpoint.stop();
					}
				}
			}
		}).start();
	}

}
