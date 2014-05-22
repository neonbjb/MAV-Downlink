package com.appliedanalog.uav.socketmapper;

/**
 *
 * @author betker
 */
public interface EndpointDataListener {
    public void dataReceived(byte[] data, int len);
}
