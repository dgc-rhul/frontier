/*******************************************************************************
 * Copyright (c) 2013 Raul Castro Fernandez (Ra).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *    Ra - Design and initial implementation
 ******************************************************************************/
package uk.co.imperial.lsds.seep.comm.serialization;

public interface DataTupleI {

	/**
	 * For maximum performance we can provide direct access methods, otherwise we will always do a map lookup first
	 */
	
	public Object getValue(String attribute);
	public String getString(String attribute);
	public String[] getStringArray(String attribute);
	public Character getChar(String attribute);
	public Byte getByte(String attribute);
	public byte[] getByteArray(String attribute);
	public Integer getInt(String attribute);
	public int[] getIntArray(String attribute);
	public Short getShort(String attribute);
	public Long getLong(String attribute);
	public Float getFloat(String attribute);
	public Double getDouble(String attribute);
	public double[] getDoubleArray(String attribute);
	public boolean getBoolean(String attribute);
	
}
