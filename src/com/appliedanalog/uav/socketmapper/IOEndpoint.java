package com.appliedanalog.uav.socketmapper;

/**
 * Interface for any interface capable of producing IO that can be bridged
 * with another such interface.
 * @author betker
 */
public interface IOEndpoint {
    /**
     * Write data out of the interface.
     * @param data Data
     * @param len Length of data to actually send in bytes.
     */
    public void writeData(byte[] data, int len);
    
    /**
     * Applies a listener to this endpoint that will be notified of all received
     * data. Endpoints are responsible for maintaining their own internal receivers
     * and must notify the outside application of data events using this data listener.
     * @param edl Data listener to use to report input. 
     */
    public void setDataListener(EndpointDataListener edl);
    
    /**
     * Notifies the endpoint to begin listening for data and producing data.
     */
    public void start();
    
    /**
     * Notifies the endpoint to stop listening for data, close IO connections and 
     * halt any threads that are used to support it. IOEndpoints will not be started
     * after they are stopped.
     */
    public void stop();
}
