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
package uk.co.imperial.lsds.seep.comm.serialization.controlhelpers;

public class Ack {

	private int opId;
	private long ts;
	
	public Ack(){}
	
	public Ack(int nodeId, long ts){
		this.opId = nodeId;
		this.ts = ts;
	}
	
	public int getOpId() {
		return opId;
	}
	public void setOpId(int nodeId) {
		this.opId = nodeId;
	}
	public long getTs() {
		return ts;
	}
	public void setTs(long ts) {
		this.ts = ts;
	}
}
