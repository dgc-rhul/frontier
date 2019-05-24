/*******************************************************************************
 * Copyright (c) 2014 Imperial College London
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Raul Castro Fernandez - initial API and implementation
 ******************************************************************************/
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import org.slf4j.LoggerFactory;

import uk.ac.imperial.lsds.seep.GLOBALS;
import uk.ac.imperial.lsds.seep.acita15.operators.Processor;
import uk.ac.imperial.lsds.seep.acita15.operators.FaceDetector;
import uk.ac.imperial.lsds.seep.acita15.operators.FaceDetectorRecognizer;
import uk.ac.imperial.lsds.seep.acita15.operators.SEEPFaceRecognizer;
import uk.ac.imperial.lsds.seep.acita15.operators.SEEPFaceRecognizerJoin;
import uk.ac.imperial.lsds.seep.acita15.operators.Sink;
import uk.ac.imperial.lsds.seep.acita15.operators.Source;
import uk.ac.imperial.lsds.seep.acita15.operators.VideoSource;
import uk.ac.imperial.lsds.seep.acita15.operators.VideoSink;
import uk.ac.imperial.lsds.seep.acita15.operators.NeuroVideoSource;
import uk.ac.imperial.lsds.seep.acita15.operators.NeuroVideoSink;
import uk.ac.imperial.lsds.seep.acita15.operators.NeuroFaceDetectorRecognizer;
import uk.ac.imperial.lsds.seep.api.QueryBuilder;
import uk.ac.imperial.lsds.seep.api.QueryComposer;
import uk.ac.imperial.lsds.seep.api.QueryPlan;
import uk.ac.imperial.lsds.seep.operator.Connectable;
import uk.ac.imperial.lsds.seep.operator.InputDataIngestionMode;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class Base implements QueryComposer{
	private final static Logger logger = LoggerFactory.getLogger(Base.class);
	private int CHAIN_LENGTH;
	private int REPLICATION_FACTOR;
	private final boolean AUTO_SCALEOUT = true;
	private final boolean CONNECT_TO_ALL_DOWNSTREAMS = false;	//Deprecated.

	public QueryPlan compose() {
		/** Declare operators **/
		//TODO: Operator ids
		//TODO: Stream ids
		
		REPLICATION_FACTOR = Integer.parseInt(GLOBALS.valueFor("replicationFactor"));
		CHAIN_LENGTH = Integer.parseInt(GLOBALS.valueFor("chainLength"));
		
		String queryType = GLOBALS.valueFor("queryType");
		if (queryType.equals("chain"))
		{
			return composeChain();
		}
		else if (queryType.equals("fr"))
		{
			return composeFaceRecognizer();
		}
		else if (queryType.equals("fdr"))
		{
			return composeFaceDetectorRecognizer();
		}
		else if (queryType.equals("neurofdr"))
		{
			return composeNeuroFaceDetectorRecognizer();
		}
		else { throw new RuntimeException("Logic error - unknown query type: "+GLOBALS.valueFor("queryType")); }
	}

	private QueryPlan composeChain()
	{
		// Declare Source
		ArrayList<String> srcFields = new ArrayList<String>();
		srcFields.add("tupleId");
		srcFields.add("value");
		srcFields.add("latencyBreakdown");
		Connectable src = QueryBuilder.newStatelessSource(new Source(), -1, srcFields);
		
		
		
		// Declare sink
		ArrayList<String> snkFields = new ArrayList<String>();
		snkFields.add("tupleId");
		snkFields.add("value");
		snkFields.add("latencyBreakdown");
		Connectable snk = QueryBuilder.newStatelessSink(new Sink(), -2, snkFields);
		
		
		if (AUTO_SCALEOUT)
		{
			if (Boolean.parseBoolean(GLOBALS.valueFor("scaleOutSinks")))
			{
				int sinkScaleFactor = Integer.parseInt(GLOBALS.valueFor("sinkScaleFactor"));
				QueryBuilder.scaleOut(snk.getOperatorId(), sinkScaleFactor > 0 ? sinkScaleFactor : REPLICATION_FACTOR);
			}
			Map<Integer, Map<Integer, Connectable>> ops = this.createChainOps(CHAIN_LENGTH, 1); 
			connectToOneDownstream(src, snk, ops);
			autoScaleout(ops);
		}
		else
		{
			// Declare processors
			Map<Integer, Map<Integer, Connectable>> ops = this.createChainOps(CHAIN_LENGTH, REPLICATION_FACTOR); 
			
			if (CONNECT_TO_ALL_DOWNSTREAMS)
			{
				connectToAllDownstreams(src, snk, ops);
			}
			else
			{
				connectToOneDownstream(src, snk, ops);
			}
		}
		
		return QueryBuilder.build();
	}

	private QueryPlan composeFaceRecognizer()
	{
		// Declare Source
		ArrayList<String> srcFields = new ArrayList<String>();
		srcFields.add("tupleId");
		srcFields.add("value");
		srcFields.add("rows");
		srcFields.add("cols");
		srcFields.add("type");
		srcFields.add("x");
		srcFields.add("y");
		srcFields.add("x2");
		srcFields.add("y2");
		srcFields.add("label");
		Connectable src = QueryBuilder.newStatelessSource(new VideoSource(), -1, srcFields);
		
		
		//Declare FaceDetector
		ArrayList<String> faceDetectFields = new ArrayList<String>();
		faceDetectFields.add("tupleId");
		faceDetectFields.add("value");
		faceDetectFields.add("rows");
		faceDetectFields.add("cols");
		faceDetectFields.add("type");
		faceDetectFields.add("x");
		faceDetectFields.add("y");
		faceDetectFields.add("x2");
		faceDetectFields.add("y2");
		faceDetectFields.add("label");
		Connectable faceDetect = QueryBuilder.newStatelessOperator(new FaceDetector(), 0, faceDetectFields);
		
		
		//Declare SpeechRecognizer
		ArrayList<String> faceRecFields = new ArrayList<String>();
		faceRecFields.add("tupleId");
		faceRecFields.add("value");
		faceRecFields.add("rows");
		faceRecFields.add("cols");
		faceRecFields.add("type");
		faceRecFields.add("x");
		faceRecFields.add("y");
		faceRecFields.add("x2");
		faceRecFields.add("y2");
		faceRecFields.add("label");
		Connectable faceRec = QueryBuilder.newStatelessOperator(new SEEPFaceRecognizer(), 1, faceRecFields);
		
		// Declare sink
		ArrayList<String> snkFields = new ArrayList<String>();
		snkFields.add("tupleId");
		snkFields.add("value");
		snkFields.add("rows");
		snkFields.add("cols");
		snkFields.add("type");
		snkFields.add("x");
		snkFields.add("y");
		snkFields.add("x2");
		snkFields.add("y2");
		snkFields.add("label");
		//Connectable snk = QueryBuilder.newStatelessSink(new Sink(), -2, snkFields);
		Connectable snk = QueryBuilder.newStatelessSink(new VideoSink(), -2, snkFields);
		
		src.connectTo(faceDetect, true, 0);
		faceDetect.connectTo(faceRec, true, 1);
		faceRec.connectTo(snk, true, 2);
		
			
		if (Boolean.parseBoolean(GLOBALS.valueFor("scaleOutSinks")))
		{
			int sinkScaleFactor = Integer.parseInt(GLOBALS.valueFor("sinkScaleFactor"));
			if (REPLICATION_FACTOR > 1 || sinkScaleFactor > 1)
			{
				QueryBuilder.scaleOut(snk.getOperatorId(), sinkScaleFactor > 0 ? sinkScaleFactor : REPLICATION_FACTOR);
			}
		}
		
		if (REPLICATION_FACTOR > 1)
		{
			QueryBuilder.scaleOut(faceDetect.getOperatorId(), REPLICATION_FACTOR);
			QueryBuilder.scaleOut(faceRec.getOperatorId(), REPLICATION_FACTOR);
		}

		return QueryBuilder.build();
	}
	
	private QueryPlan composeFaceDetectorRecognizer()
	{
		// Declare Source
		ArrayList<String> srcFields = new ArrayList<String>();
		srcFields.add("tupleId");
		srcFields.add("value");
		srcFields.add("rows");
		srcFields.add("cols");
		srcFields.add("type");
		srcFields.add("x");
		srcFields.add("y");
		srcFields.add("x2");
		srcFields.add("y2");
		srcFields.add("label");
		Connectable src = QueryBuilder.newStatelessSource(new VideoSource(), -1, srcFields);
		
		
		//Declare FaceDetector
		ArrayList<String> faceDetectorRecognizerFields = new ArrayList<String>();
		faceDetectorRecognizerFields.add("tupleId");
		faceDetectorRecognizerFields.add("value");
		faceDetectorRecognizerFields.add("rows");
		faceDetectorRecognizerFields.add("cols");
		faceDetectorRecognizerFields.add("type");
		faceDetectorRecognizerFields.add("x");
		faceDetectorRecognizerFields.add("y");
		faceDetectorRecognizerFields.add("x2");
		faceDetectorRecognizerFields.add("y2");
		faceDetectorRecognizerFields.add("label");
		Connectable faceDetectorRecognizer = QueryBuilder.newStatelessOperator(new FaceDetectorRecognizer(), 0, faceDetectorRecognizerFields);
		
		
		// Declare sink
		ArrayList<String> snkFields = new ArrayList<String>();
		snkFields.add("tupleId");
		snkFields.add("value");
		snkFields.add("rows");
		snkFields.add("cols");
		snkFields.add("type");
		snkFields.add("x");
		snkFields.add("y");
		snkFields.add("x2");
		snkFields.add("y2");
		snkFields.add("label");
		//Connectable snk = QueryBuilder.newStatelessSink(new Sink(), -2, snkFields);
		Connectable snk = QueryBuilder.newStatelessSink(new VideoSink(), -2, snkFields);
		
		src.connectTo(faceDetectorRecognizer, true, 0);
		faceDetectorRecognizer.connectTo(snk, true, 1);
		
		if (Boolean.parseBoolean(GLOBALS.valueFor("scaleOutSinks")))
		{
			int sinkScaleFactor = Integer.parseInt(GLOBALS.valueFor("sinkScaleFactor"));
			if (REPLICATION_FACTOR > 1 || sinkScaleFactor > 1)
			{
				QueryBuilder.scaleOut(snk.getOperatorId(), sinkScaleFactor > 0 ? sinkScaleFactor : REPLICATION_FACTOR);
			}
		}
		
		if (REPLICATION_FACTOR > 1)
		{
			QueryBuilder.scaleOut(faceDetectorRecognizer.getOperatorId(), REPLICATION_FACTOR);
		}

		return QueryBuilder.build();
	}
	
	private QueryPlan composeNeuroFaceDetectorRecognizer()
	{
		// Declare Source
		ArrayList<String> srcFields = new ArrayList<String>();
		srcFields.add("tupleId");
		srcFields.add("value");
		srcFields.add("rows");
		srcFields.add("cols");
		srcFields.add("type");
		srcFields.add("x");
		srcFields.add("y");
		srcFields.add("x2");
		srcFields.add("y2");
		srcFields.add("label");
		Connectable src = QueryBuilder.newStatelessSource(new NeuroVideoSource(), -1, srcFields);
		
		
		//Declare FaceDetector
		ArrayList<String> faceDetectorRecognizerFields = new ArrayList<String>();
		faceDetectorRecognizerFields.add("tupleId");
		faceDetectorRecognizerFields.add("value");
		faceDetectorRecognizerFields.add("rows");
		faceDetectorRecognizerFields.add("cols");
		faceDetectorRecognizerFields.add("type");
		faceDetectorRecognizerFields.add("x");
		faceDetectorRecognizerFields.add("y");
		faceDetectorRecognizerFields.add("x2");
		faceDetectorRecognizerFields.add("y2");
		faceDetectorRecognizerFields.add("label");
		Connectable faceDetectorRecognizer = QueryBuilder.newStatelessOperator(new NeuroFaceDetectorRecognizer(), 0, faceDetectorRecognizerFields);
		
		
		// Declare sink
		ArrayList<String> snkFields = new ArrayList<String>();
		snkFields.add("tupleId");
		snkFields.add("value");
		snkFields.add("rows");
		snkFields.add("cols");
		snkFields.add("type");
		snkFields.add("x");
		snkFields.add("y");
		snkFields.add("x2");
		snkFields.add("y2");
		snkFields.add("label");
		//Connectable snk = QueryBuilder.newStatelessSink(new Sink(), -2, snkFields);
		Connectable snk = QueryBuilder.newStatelessSink(new NeuroVideoSink(), -2, snkFields);
		
		src.connectTo(faceDetectorRecognizer, true, 0);
		faceDetectorRecognizer.connectTo(snk, true, 1);
		
		if (Boolean.parseBoolean(GLOBALS.valueFor("scaleOutSinks")))
		{
			int sinkScaleFactor = Integer.parseInt(GLOBALS.valueFor("sinkScaleFactor"));
			if (REPLICATION_FACTOR > 1 || sinkScaleFactor > 1)
			{
				QueryBuilder.scaleOut(snk.getOperatorId(), sinkScaleFactor > 0 ? sinkScaleFactor : REPLICATION_FACTOR);
			}
		}
		
		if (REPLICATION_FACTOR > 1)
		{
			QueryBuilder.scaleOut(faceDetectorRecognizer.getOperatorId(), REPLICATION_FACTOR);
		}

		return QueryBuilder.build();
	}
	
	
	
	private Map<Integer, Map<Integer, Connectable>> createChainOps(int chainLength, int replicationFactor)
	{
		Map<Integer, Map<Integer, Connectable>> ops = new HashMap();

		for (int i = 0; i < chainLength; i++)
		{
			ops.put(i, new HashMap<Integer, Connectable>());
			for (int j = 0; j < replicationFactor; j++)
			{ 
				ArrayList<String> pFields = new ArrayList<String>();
				pFields.add("tupleId");
				pFields.add("value");
				pFields.add("latencyBreakdown");
				Connectable p = QueryBuilder.newStatelessOperator(new Processor(), (i*replicationFactor)+j, pFields);
				ops.get(i).put(j, p);
			}
		}

		return ops;
	}

	private void connectToAllDownstreams(Connectable src, Connectable snk, Map<Integer, Map<Integer, Connectable>> ops)
	{
		// Connect intermediate ops 
		for (int i = 0; i < CHAIN_LENGTH-1; i++)
		{
			for (int j = 0; j < ops.get(i).size(); j++)
			{
				for (int k=0; k < ops.get(i+1).size(); k++)
				{
					ops.get(i).get(j).connectTo(ops.get(i+1).get(k), true, i+1);
				}
			}
		}

		// Connect src to first layer ops 
		for (int j = 0; j < ops.get(0).size(); j++)
		{
			src.connectTo(ops.get(0).get(j), true, 0);
		}

		// Connect final layer ops to sink
		for (int j = 0; j < ops.get(CHAIN_LENGTH-1).size(); j++)
		{
			ops.get(CHAIN_LENGTH-1).get(j).connectTo(snk, true, CHAIN_LENGTH);
		}
	}

	private void connectToOneDownstream(Connectable src, Connectable snk, Map<Integer, Map<Integer, Connectable>> ops)
	{
		// Connect intermediate ops 
		// Assumes all layers are the same size.
		for (int i = 0; i < CHAIN_LENGTH-1; i++)
		{
			for (int j = 0; j < ops.get(i).size(); j++)
			{
				ops.get(i).get(j).connectTo(ops.get(i+1).get(j), true, i+1);
			}
		}

		if (ops.isEmpty())
		{
			src.connectTo(snk, true, 0);
		}
		else
		{
			//Connect src to first op
			src.connectTo(ops.get(0).get(0), true, 0);
	
			// Connect ops to sink
			for (int j = 0; j < ops.get(CHAIN_LENGTH-1).size(); j++)
			{
				ops.get(CHAIN_LENGTH-1).get(j).connectTo(snk, true, CHAIN_LENGTH);
			}
		}
	}
	
	private void autoScaleout(Map<Integer, Map<Integer, Connectable>> ops)
	{
		for (int i = 0; i < CHAIN_LENGTH; i++)
		{
			if (ops.get(i).size() != 1) { throw new RuntimeException("Logic error."); }
			QueryBuilder.scaleOut(ops.get(i).get(0).getOperatorId(), REPLICATION_FACTOR);
		}
	}
}
