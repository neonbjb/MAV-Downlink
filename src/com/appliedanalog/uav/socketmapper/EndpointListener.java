package com.appliedanalog.uav.socketmapper;

public interface EndpointListener {
	public void externalMessageReceived();
	public void connected();
	public void disconnected();
}
