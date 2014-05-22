package com.appliedanalog.uav.socketmapper;

public interface EndpointListener {
	public void messageSent();
	public void connected();
	public void disconnected();
}
