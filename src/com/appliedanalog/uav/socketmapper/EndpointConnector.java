package com.appliedanalog.uav.socketmapper;

/**
 * Bridges two IO endpoints so all output from one goes to the input of the other,
 * and vice versa.
 * @author betker
 */
public class EndpointConnector{
    IOEndpoint endpoint1, endpoint2;
    EndpointIOConnector connector1, connector2;
    
    /**
     * Initializes this connector and turns on the bridge.
     * @param endpoint1 The first IO endpoint.
     * @param endpoint2 The second IO endpoint.
     */
    public EndpointConnector(IOEndpoint endpoint1, IOEndpoint endpoint2){
        this.endpoint1 = endpoint1;
        this.endpoint2 = endpoint2;
        
        connector1 = new EndpointIOConnector(endpoint2);
        endpoint1.setDataListener(connector1);
        connector2 = new EndpointIOConnector(endpoint1);
        endpoint2.setDataListener(connector2);
    }
    
    /**
     * This class performs the actual bridging by receiving data from one endpoint
     * and sending it to the other. There are two used in this connector.
     */
    class EndpointIOConnector implements EndpointDataListener{
        IOEndpoint otherEndpoint;
        boolean enabled;
        EndpointIOConnector(IOEndpoint other){
            otherEndpoint = other;
            enabled = true;
        }
        @Override
        public void dataReceived(byte[] data, int len) {
            if(enabled){
                otherEndpoint.writeData(data, len);
            }
        }
    }
    
    /**
     * Turns on the IO connection so that data flows from endpoint 1 to endpoint 2 and
     * vice versa.
     */
    public void bridge(){
        connector1.enabled = true;
        connector2.enabled = true;
    }
    
    /**
     * Disables the IO connection so that data is thrown out.
     */
    public void unbridge(){
        connector1.enabled = false;
        connector2.enabled = false;
    }
}
