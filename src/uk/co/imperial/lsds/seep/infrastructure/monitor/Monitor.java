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
package uk.co.imperial.lsds.seep.infrastructure.monitor;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

import uk.co.imperial.lsds.seep.P;
import uk.co.imperial.lsds.seep.comm.serialization.MetricsTuple;
import uk.co.imperial.lsds.seep.infrastructure.NodeManager;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;

/**
* Monitor. This class implements runnable and is in charge of retrieving information from the system. There is a monitor in each node that is being used by the system.
*/

public class Monitor implements Runnable{

	private int nodeId;
	private Kryo k = null;
	private Output output = null;
	
	private boolean listen = true;
	
	private Kryo initializeKryo(){
		k = new Kryo();
		k.register(MetricsTuple.class);
		return k;
	}
	
	public void setNodeId(int opId) {
		this.nodeId = opId;
	}
	
	public void stopMonitor(){
		listen = false;
	}

	private void sendMonitorInfo() throws IOException{
		//Pick metrics
		long inputQueueEvents = MetricsReader.eventsInputQueue.getCount();
		long numberIncomingdataHandlerWorkers = MetricsReader.numberIncomingDataHandlerWorkers.getCount();
		MetricsTuple mt = new MetricsTuple();
		mt.setOpId(nodeId);
		mt.setInputQueueEvents(inputQueueEvents);
		mt.setNumberIncomingDataHandlerWorkers(numberIncomingdataHandlerWorkers);
		k.writeObject(output, mt);
		output.flush();
	}


	public void run(){
		//Initialize kryo to send the serialized data
		initializeKryo();
		
		initializeLocalReporter();
		
		try{
			//Establish connection with the monitor manager.
			InetAddress addrMon = InetAddress.getByName(P.valueFor("mainAddr"));
			int portMon = Integer.parseInt(P.valueFor("monitorManagerPort"));
			NodeManager.nLogger.info("MONITOR-> conn ip: "+addrMon.toString()+" port: "+portMon);
			Socket conn = new Socket(addrMon, portMon);
			OutputStream out = conn.getOutputStream();
			output = new Output(out);
			//Monitoring interval
			int sleepInterval = 1000*(Integer.parseInt(P.valueFor("monitorInterval"))-1);
			
			//Runtime monitor loop
			while(listen){
				//Remote info
				Thread.sleep(sleepInterval);
				sendMonitorInfo();
			}
			output.close();
			conn.close();
		}
		catch(IOException io){
			NodeManager.nLogger.warning("When trying to connect to the MonitorManager: "+io.getMessage());
			io.printStackTrace();
		}
		catch(InterruptedException ie){
			NodeManager.nLogger.warning("When trying to sleep: "+ie.getMessage());
			ie.printStackTrace();
		}
	}
	
	public void initializeLocalReporter(){
		LocalReporterMonitor lrm = new LocalReporterMonitor();
		Thread t = new Thread(lrm);
		t.start();
	}
}
