/**
 * MerlotTargetServiceBindingLocator.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis #axisVersion# #today# WSDL2Java emitter.
 */

package be.cenorm.www;

public class MerlotTargetServiceBindingLocator extends org.apache.axis.client.Service implements be.cenorm.www.MerlotTargetServiceBinding {

    public MerlotTargetServiceBindingLocator() {
    }


    public MerlotTargetServiceBindingLocator(org.apache.axis.EngineConfiguration config) {
        super(config);
    }

    public MerlotTargetServiceBindingLocator(java.lang.String wsdlLoc, javax.xml.namespace.QName sName) throws javax.xml.rpc.ServiceException {
        super(wsdlLoc, sName);
    }

    // Use to get a proxy class for MerlotTargetServiceBinding
    private java.lang.String MerlotTargetServiceBinding_address = "http://ariadne.cs.kuleuven.be/SqiInterop/services/MerlotTargetService";

    public java.lang.String getMerlotTargetServiceBindingAddress() {
        return MerlotTargetServiceBinding_address;
    }

    // The WSDD service name defaults to the port name.
    private java.lang.String MerlotTargetServiceBindingWSDDServiceName = "MerlotTargetServiceBinding";

    public java.lang.String getMerlotTargetServiceBindingWSDDServiceName() {
        return MerlotTargetServiceBindingWSDDServiceName;
    }

    public void setMerlotTargetServiceBindingWSDDServiceName(java.lang.String name) {
        MerlotTargetServiceBindingWSDDServiceName = name;
    }

    public be.cenorm.www.SqiTargetPort getMerlotTargetServiceBinding() throws javax.xml.rpc.ServiceException {
       java.net.URL endpoint;
        try {
            endpoint = new java.net.URL(MerlotTargetServiceBinding_address);
        }
        catch (java.net.MalformedURLException e) {
            throw new javax.xml.rpc.ServiceException(e);
        }
        return getMerlotTargetServiceBinding(endpoint);
    }

    public be.cenorm.www.SqiTargetPort getMerlotTargetServiceBinding(java.net.URL portAddress) throws javax.xml.rpc.ServiceException {
        try {
            be.cenorm.www.SqiTargetBindingStub _stub = new be.cenorm.www.SqiTargetBindingStub(portAddress, this);
            _stub.setPortName(getMerlotTargetServiceBindingWSDDServiceName());
            return _stub;
        }
        catch (org.apache.axis.AxisFault e) {
            return null;
        }
    }

    public void setMerlotTargetServiceBindingEndpointAddress(java.lang.String address) {
        MerlotTargetServiceBinding_address = address;
    }

    /**
     * For the given interface, get the stub implementation.
     * If this service has no port for the given interface,
     * then ServiceException is thrown.
     */
    public java.rmi.Remote getPort(Class serviceEndpointInterface) throws javax.xml.rpc.ServiceException {
        try {
            if (be.cenorm.www.SqiTargetPort.class.isAssignableFrom(serviceEndpointInterface)) {
                be.cenorm.www.SqiTargetBindingStub _stub = new be.cenorm.www.SqiTargetBindingStub(new java.net.URL(MerlotTargetServiceBinding_address), this);
                _stub.setPortName(getMerlotTargetServiceBindingWSDDServiceName());
                return _stub;
            }
        }
        catch (java.lang.Throwable t) {
            throw new javax.xml.rpc.ServiceException(t);
        }
        throw new javax.xml.rpc.ServiceException("There is no stub implementation for the interface:  " + (serviceEndpointInterface == null ? "null" : serviceEndpointInterface.getName()));
    }

    /**
     * For the given interface, get the stub implementation.
     * If this service has no port for the given interface,
     * then ServiceException is thrown.
     */
    public java.rmi.Remote getPort(javax.xml.namespace.QName portName, Class serviceEndpointInterface) throws javax.xml.rpc.ServiceException {
        if (portName == null) {
            return getPort(serviceEndpointInterface);
        }
        java.lang.String inputPortName = portName.getLocalPart();
        if ("MerlotTargetServiceBinding".equals(inputPortName)) {
            return getMerlotTargetServiceBinding();
        }
        else  {
            java.rmi.Remote _stub = getPort(serviceEndpointInterface);
            ((org.apache.axis.client.Stub) _stub).setPortName(portName);
            return _stub;
        }
    }

    public javax.xml.namespace.QName getServiceName() {
        return new javax.xml.namespace.QName("urn:www.cenorm.be/isss/ltws/wsdl/SQIv1p0", "MerlotTargetServiceBinding");
    }

    private java.util.HashSet ports = null;

    public java.util.Iterator getPorts() {
        if (ports == null) {
            ports = new java.util.HashSet();
            ports.add(new javax.xml.namespace.QName("urn:www.cenorm.be/isss/ltws/wsdl/SQIv1p0", "MerlotTargetServiceBinding"));
        }
        return ports.iterator();
    }

    /**
    * Set the endpoint address for the specified port name.
    */
    public void setEndpointAddress(java.lang.String portName, java.lang.String address) throws javax.xml.rpc.ServiceException {
        
if ("MerlotTargetServiceBinding".equals(portName)) {
            setMerlotTargetServiceBindingEndpointAddress(address);
        }
        else 
{ // Unknown Port Name
            throw new javax.xml.rpc.ServiceException(" Cannot set Endpoint Address for Unknown Port" + portName);
        }
    }

    /**
    * Set the endpoint address for the specified port name.
    */
    public void setEndpointAddress(javax.xml.namespace.QName portName, java.lang.String address) throws javax.xml.rpc.ServiceException {
        setEndpointAddress(portName.getLocalPart(), address);
    }

}
