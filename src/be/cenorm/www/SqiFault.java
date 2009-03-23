/**
 * SqiFault.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis #axisVersion# #today# WSDL2Java emitter.
 */

package be.cenorm.www;

public class SqiFault  extends org.apache.axis.AxisFault  implements java.io.Serializable {
    private be.cenorm.www.FaultCodeType sqiFaultCode;

    private java.lang.String message1;

    public SqiFault() {
    }

    public SqiFault(
           be.cenorm.www.FaultCodeType sqiFaultCode,
           java.lang.String message1) {
        this.sqiFaultCode = sqiFaultCode;
        this.message1 = message1;
    }


    /**
     * Gets the sqiFaultCode value for this SqiFault.
     * 
     * @return sqiFaultCode
     */
    public be.cenorm.www.FaultCodeType getSqiFaultCode() {
        return sqiFaultCode;
    }


    /**
     * Sets the sqiFaultCode value for this SqiFault.
     * 
     * @param sqiFaultCode
     */
    public void setSqiFaultCode(be.cenorm.www.FaultCodeType sqiFaultCode) {
        this.sqiFaultCode = sqiFaultCode;
    }


    /**
     * Gets the message1 value for this SqiFault.
     * 
     * @return message1
     */
    public java.lang.String getMessage1() {
        return message1;
    }


    /**
     * Sets the message1 value for this SqiFault.
     * 
     * @param message1
     */
    public void setMessage1(java.lang.String message1) {
        this.message1 = message1;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof SqiFault)) return false;
        SqiFault other = (SqiFault) obj;
        if (obj == null) return false;
        if (this == obj) return true;
        if (__equalsCalc != null) {
            return (__equalsCalc == obj);
        }
        __equalsCalc = obj;
        boolean _equals;
        _equals = true && 
            ((this.sqiFaultCode==null && other.getSqiFaultCode()==null) || 
             (this.sqiFaultCode!=null &&
              this.sqiFaultCode.equals(other.getSqiFaultCode()))) &&
            ((this.message1==null && other.getMessage1()==null) || 
             (this.message1!=null &&
              this.message1.equals(other.getMessage1())));
        __equalsCalc = null;
        return _equals;
    }

    private boolean __hashCodeCalc = false;
    public synchronized int hashCode() {
        if (__hashCodeCalc) {
            return 0;
        }
        __hashCodeCalc = true;
        int _hashCode = 1;
        if (getSqiFaultCode() != null) {
            _hashCode += getSqiFaultCode().hashCode();
        }
        if (getMessage1() != null) {
            _hashCode += getMessage1().hashCode();
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(SqiFault.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("urn:www.cenorm.be/isss/ltws/wsdl/SQIv1p0", ">SqiFault"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("sqiFaultCode");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:www.cenorm.be/isss/ltws/wsdl/SQIv1p0", "sqiFaultCode"));
        elemField.setXmlType(new javax.xml.namespace.QName("urn:www.cenorm.be/isss/ltws/wsdl/SQIv1p0", "faultCodeType"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("message1");
        elemField.setXmlName(new javax.xml.namespace.QName("urn:www.cenorm.be/isss/ltws/wsdl/SQIv1p0", "message"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
    }

    /**
     * Return type metadata object
     */
    public static org.apache.axis.description.TypeDesc getTypeDesc() {
        return typeDesc;
    }

    /**
     * Get Custom Serializer
     */
    public static org.apache.axis.encoding.Serializer getSerializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new  org.apache.axis.encoding.ser.BeanSerializer(
            _javaType, _xmlType, typeDesc);
    }

    /**
     * Get Custom Deserializer
     */
    public static org.apache.axis.encoding.Deserializer getDeserializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new  org.apache.axis.encoding.ser.BeanDeserializer(
            _javaType, _xmlType, typeDesc);
    }


    /**
     * Writes the exception data to the faultDetails
     */
    public void writeDetails(javax.xml.namespace.QName qname, org.apache.axis.encoding.SerializationContext context) throws java.io.IOException {
        context.serialize(qname, null, this);
    }
}
