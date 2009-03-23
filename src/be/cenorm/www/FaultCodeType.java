/**
 * FaultCodeType.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis #axisVersion# #today# WSDL2Java emitter.
 */

package be.cenorm.www;

public class FaultCodeType implements java.io.Serializable {
    private java.lang.String _value_;
    private static java.util.HashMap _table_ = new java.util.HashMap();

    // Constructor
    protected FaultCodeType(java.lang.String value) {
        _value_ = value;
        _table_.put(_value_,this);
    }

    public static final java.lang.String _SQI_00000 = "SQI_00000";
    public static final java.lang.String _SQI_00001 = "SQI_00001";
    public static final java.lang.String _SQI_00002 = "SQI_00002";
    public static final java.lang.String _SQI_00003 = "SQI_00003";
    public static final java.lang.String _SQI_00004 = "SQI_00004";
    public static final java.lang.String _SQI_00005 = "SQI_00005";
    public static final java.lang.String _SQI_00006 = "SQI_00006";
    public static final java.lang.String _SQI_00007 = "SQI_00007";
    public static final java.lang.String _SQI_00008 = "SQI_00008";
    public static final java.lang.String _SQI_00009 = "SQI_00009";
    public static final java.lang.String _SQI_00010 = "SQI_00010";
    public static final java.lang.String _SQI_00011 = "SQI_00011";
    public static final java.lang.String _SQI_00012 = "SQI_00012";
    public static final java.lang.String _SQI_00013 = "SQI_00013";
    public static final java.lang.String _SQI_00014 = "SQI_00014";
    public static final java.lang.String _SQI_00015 = "SQI_00015";
    public static final java.lang.String _SQI_00016 = "SQI_00016";
    public static final FaultCodeType SQI_00000 = new FaultCodeType(_SQI_00000);
    public static final FaultCodeType SQI_00001 = new FaultCodeType(_SQI_00001);
    public static final FaultCodeType SQI_00002 = new FaultCodeType(_SQI_00002);
    public static final FaultCodeType SQI_00003 = new FaultCodeType(_SQI_00003);
    public static final FaultCodeType SQI_00004 = new FaultCodeType(_SQI_00004);
    public static final FaultCodeType SQI_00005 = new FaultCodeType(_SQI_00005);
    public static final FaultCodeType SQI_00006 = new FaultCodeType(_SQI_00006);
    public static final FaultCodeType SQI_00007 = new FaultCodeType(_SQI_00007);
    public static final FaultCodeType SQI_00008 = new FaultCodeType(_SQI_00008);
    public static final FaultCodeType SQI_00009 = new FaultCodeType(_SQI_00009);
    public static final FaultCodeType SQI_00010 = new FaultCodeType(_SQI_00010);
    public static final FaultCodeType SQI_00011 = new FaultCodeType(_SQI_00011);
    public static final FaultCodeType SQI_00012 = new FaultCodeType(_SQI_00012);
    public static final FaultCodeType SQI_00013 = new FaultCodeType(_SQI_00013);
    public static final FaultCodeType SQI_00014 = new FaultCodeType(_SQI_00014);
    public static final FaultCodeType SQI_00015 = new FaultCodeType(_SQI_00015);
    public static final FaultCodeType SQI_00016 = new FaultCodeType(_SQI_00016);
    public java.lang.String getValue() { return _value_;}
    public static FaultCodeType fromValue(java.lang.String value)
          throws java.lang.IllegalArgumentException {
        FaultCodeType enumeration = (FaultCodeType)
            _table_.get(value);
        if (enumeration==null) throw new java.lang.IllegalArgumentException();
        return enumeration;
    }
    public static FaultCodeType fromString(java.lang.String value)
          throws java.lang.IllegalArgumentException {
        return fromValue(value);
    }
    public boolean equals(java.lang.Object obj) {return (obj == this);}
    public int hashCode() { return toString().hashCode();}
    public java.lang.String toString() { return _value_;}
    public java.lang.Object readResolve() throws java.io.ObjectStreamException { return fromValue(_value_);}
    public static org.apache.axis.encoding.Serializer getSerializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new org.apache.axis.encoding.ser.EnumSerializer(
            _javaType, _xmlType);
    }
    public static org.apache.axis.encoding.Deserializer getDeserializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new org.apache.axis.encoding.ser.EnumDeserializer(
            _javaType, _xmlType);
    }
    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(FaultCodeType.class);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("urn:www.cenorm.be/isss/ltws/wsdl/SQIv1p0", "faultCodeType"));
    }
    /**
     * Return type metadata object
     */
    public static org.apache.axis.description.TypeDesc getTypeDesc() {
        return typeDesc;
    }

}
