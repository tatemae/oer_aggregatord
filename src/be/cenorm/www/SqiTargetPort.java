/**
 * SqiTargetPort.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis #axisVersion# #today# WSDL2Java emitter.
 */

package be.cenorm.www;

public interface SqiTargetPort extends java.rmi.Remote {
    public void setQueryLanguage(java.lang.String targetSessionID, java.lang.String queryLanguageID) throws java.rmi.RemoteException, be.cenorm.www.SqiFault;
    public void setMaxQueryResults(java.lang.String targetSessionID, int maxQueryResults) throws java.rmi.RemoteException, be.cenorm.www.SqiFault;
    public void setMaxDuration(java.lang.String targetSessionID, int maxDuration) throws java.rmi.RemoteException, be.cenorm.www.SqiFault;
    public void setResultsFormat(java.lang.String targetSessionID, java.lang.String resultsFormat) throws java.rmi.RemoteException, be.cenorm.www.SqiFault;
    public void setResultsSetSize(java.lang.String targetSessionID, int resultsSetSize) throws java.rmi.RemoteException, be.cenorm.www.SqiFault;
    public java.lang.String synchronousQuery(java.lang.String targetSessionID, java.lang.String queryStatement, int startResult) throws java.rmi.RemoteException, be.cenorm.www.SqiFault;
    public int getTotalResultsCount(java.lang.String targetSessionID, java.lang.String queryStatement) throws java.rmi.RemoteException, be.cenorm.www.SqiFault;
    public void setSourceLocation(java.lang.String targetSessionID, java.lang.String sourceLocation) throws java.rmi.RemoteException, be.cenorm.www.SqiFault;
    public void asynchronousQuery(java.lang.String targetSessionID, java.lang.String queryStatement, java.lang.String queryID) throws java.rmi.RemoteException, be.cenorm.www.SqiFault;
}
