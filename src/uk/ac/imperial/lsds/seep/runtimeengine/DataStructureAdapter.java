/*******************************************************************************
 * Copyright (c) 2013 Imperial College London.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Raul Castro Fernandez - initial design and implementation
 ******************************************************************************/
package uk.ac.imperial.lsds.seep.runtimeengine;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import uk.ac.imperial.lsds.seep.infrastructure.NodeManager;
import uk.ac.imperial.lsds.seep.operator.Operator;
import uk.ac.imperial.lsds.seep.operator.QuerySpecificationI.InputDataIngestionMode;

public class DataStructureAdapter {
	
	private Map<Integer, DataStructureI> dsoMap = new HashMap<Integer, DataStructureI>();
	private DataStructureI uniqueDso = null;
	
	public DataStructureAdapter(){
		
	}
	
	public DataStructureI getUniqueDso(){
		return uniqueDso;
	}
	
	public DataStructureI getDataStructureIForOp(int opId){
		if(dsoMap.containsKey(opId)){
			return dsoMap.get(opId);
		}
		else{
			NodeManager.nLogger.severe("-> ERROR. No adapter for given opId, not possible to forward data to operator.");
			return null;
		}
	}
	
	public int getNumberOfModes(){
		return dsoMap.size();
	}
	
	public Map<Integer, DataStructureI> getInputDataIngestionModeMap(){
		return dsoMap;
	}
	
	public void setDSOForOp(int opId, DataStructureI dso){
		dsoMap.put(opId, dso);
	}
	
	public void setUp(Map<Integer, InputDataIngestionMode> iimMap, int numUpstreams){
		// Differentiate between cases with only one inputdatamode and more than one (for performance reasons)
		if(iimMap.size() > 1){
			NodeManager.nLogger.info("-> Setting up multiple inputDataIngestionModes");
			// For processing one event per iteration, the queue is the best abstraction
			for(Entry<Integer, InputDataIngestionMode> entry : iimMap.entrySet()){
				if(entry.getValue().equals(Operator.InputDataIngestionMode.ONE_AT_A_TIME)){
					InputQueue iq = new InputQueue();
					dsoMap.put(entry.getKey(), iq);
					NodeManager.nLogger.info("-> Ingest with InputQueue from "+entry.getKey());
				}
				else if(entry.getValue().equals(Operator.InputDataIngestionMode.UPSTREAM_SYNC_BARRIER)){
					Barrier b = new Barrier(numUpstreams);
					dsoMap.put(entry.getKey(), b);
					NodeManager.nLogger.info("-> Ingest with Sync-Barrier from "+entry.getKey());
				}
			}
		}
		else if(iimMap.size() == 1){
			NodeManager.nLogger.info("-> Setting up a unique InputDataIngestionMode");
			for(Entry<Integer, InputDataIngestionMode> entry : iimMap.entrySet()){
				if(entry.getValue().equals(Operator.InputDataIngestionMode.ONE_AT_A_TIME)){
					InputQueue iq = new InputQueue();
					uniqueDso = iq;
					NodeManager.nLogger.info("-> Ingest with InputQueue from "+entry.getKey());
				}
				else if(entry.getValue().equals(Operator.InputDataIngestionMode.UPSTREAM_SYNC_BARRIER)){
					Barrier b = new Barrier(numUpstreams);
					uniqueDso= b;
					NodeManager.nLogger.info("-> Ingest with Sync-Barrier from "+entry.getKey());
				}
			}
		}
	}
	
	/** SPECIFIC METHODS **/
	
	public void reconfigureNumUpstream(int originalOpId, int upstreamSize){
		// Number of upstream has changed, this affects the barrier
		System.out.println("NEW UPSTREAM SIZE: "+upstreamSize);
		DataStructureI barrier = dsoMap.get(originalOpId);
		if(barrier instanceof Barrier){
			System.out.println("Calling to reconfigure barrier");
			((Barrier)barrier).reconfigureBarrier(upstreamSize);
		}
	}
}