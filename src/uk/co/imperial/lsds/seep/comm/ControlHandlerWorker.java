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
package uk.co.imperial.lsds.seep.comm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;

import uk.co.imperial.lsds.seep.comm.serialization.ControlTuple;
import uk.co.imperial.lsds.seep.comm.serialization.controlhelpers.Ack;
import uk.co.imperial.lsds.seep.comm.serialization.controlhelpers.BackupNodeState;
import uk.co.imperial.lsds.seep.comm.serialization.controlhelpers.BackupOperatorState;
import uk.co.imperial.lsds.seep.comm.serialization.controlhelpers.BackupRI;
import uk.co.imperial.lsds.seep.comm.serialization.controlhelpers.InitNodeState;
import uk.co.imperial.lsds.seep.comm.serialization.controlhelpers.InitOperatorState;
import uk.co.imperial.lsds.seep.comm.serialization.controlhelpers.InitRI;
import uk.co.imperial.lsds.seep.comm.serialization.controlhelpers.InvalidateState;
import uk.co.imperial.lsds.seep.comm.serialization.controlhelpers.RawData;
import uk.co.imperial.lsds.seep.comm.serialization.controlhelpers.ReconfigureConnection;
import uk.co.imperial.lsds.seep.comm.serialization.controlhelpers.Resume;
import uk.co.imperial.lsds.seep.comm.serialization.controlhelpers.ScaleOutInfo;
import uk.co.imperial.lsds.seep.comm.serialization.controlhelpers.StateAck;
import uk.co.imperial.lsds.seep.comm.serialization.controlhelpers.StateChunk;
import uk.co.imperial.lsds.seep.infrastructure.NodeManager;
import uk.co.imperial.lsds.seep.processingunit.StreamStateChunk;
import uk.co.imperial.lsds.seep.runtimeengine.CoreRE;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.serializers.MapSerializer;

/** 
* ControlHandlerWorker. This class is in charge of managing control messages.
*/

public class ControlHandlerWorker implements Runnable{

	private Socket incomingSocket = null;
	private CoreRE owner = null;
	//In charge of control thread execution
	private boolean goOn;
	private Kryo k = null;

	public ControlHandlerWorker(Socket incomingSocket, CoreRE owner){
		this.incomingSocket = incomingSocket;
		this.owner = owner;
		this.goOn = true;
		this.k = initializeKryo();
	}
	
	private Kryo initializeKryo(){
		//optimize here kryo
		Kryo k = new Kryo();
		k.setClassLoader(owner.getRuntimeClassLoader());
		k.register(ControlTuple.class);
		k.register(StreamStateChunk.class);
		k.register(StateChunk.class);
		k.register(HashMap.class, new MapSerializer());
		k.register(BackupOperatorState.class);
		k.register(byte[].class);
		k.register(RawData.class);
		k.register(Ack.class);
		k.register(BackupNodeState.class);
		k.register(Resume.class);
		k.register(ScaleOutInfo.class);
		k.register(StateAck.class);
		k.register(ArrayList.class);
		k.register(BackupRI.class);
		k.register(InitNodeState.class);
		k.register(InitOperatorState.class);
		k.register(InitRI.class);
		k.register(InvalidateState.class);
		k.register(ReconfigureConnection.class);
		return k;
	}

	public void run(){
		InputStream is = null;
		OutputStream os = null;
		ControlTuple tuple = null;
//		Seep.ControlTuple.Builder ct = null;
		try{
			//Establish input stream, which receives serialized objects
			is = incomingSocket.getInputStream();
			os = incomingSocket.getOutputStream();
			Input i = new Input(is, 100000);
			//Read the connection to get the data
			while(goOn){
//				tuple = Seep.ControlTuple.parseDelimitedFrom(is);
				tuple = k.readObject(i, ControlTuple.class);
//				System.out.println("RECEIVED");
/// \todo {what is the underlying problem that makes tuple potentially be null?}
				if(tuple != null){
//					if(tuple.getType() == null){
//						System.out.println("MIERDA");
//						System.out.println("RCVD FROM: "+incomingSocket.toString());
//						System.exit(0);
//					}
					InetAddress ip = incomingSocket.getInetAddress();
					owner.processControlTuple(tuple, os, ip);
				}
				else{
					NodeManager.nLogger.severe("-> ControlHandlerWorker. TUPLE IS NULL !");
					break;
				}
			}
			//Close streams and socket
			NodeManager.nLogger.severe("-> Closing connection");
			is.close();
			incomingSocket.close();
		}
		catch(IOException io){
			NodeManager.nLogger.severe("-> ControlHandlerWorker. IO Error "+io.getMessage());
			io.printStackTrace();
		}
	}
}
