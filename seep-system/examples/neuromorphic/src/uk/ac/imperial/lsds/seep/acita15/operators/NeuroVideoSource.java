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
package uk.ac.imperial.lsds.seep.acita15.operators;

import java.io.File;
import java.io.FilenameFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.util.Enumeration;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import uk.ac.imperial.lsds.seep.GLOBALS;
import uk.ac.imperial.lsds.seep.comm.serialization.DataTuple;
import uk.ac.imperial.lsds.seep.comm.serialization.messages.TuplePayload;
import uk.ac.imperial.lsds.seep.operator.StatelessOperator;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.imgscalr.Scalr;

/*
import com.googlecode.javacpp.FloatPointer;
import com.googlecode.javacpp.Pointer;
import com.googlecode.javacpp.PointerPointer;
import java.util.List;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import org.apache.log4j.Logger;
import static com.googlecode.javacv.cpp.opencv_core.*;
import static com.googlecode.javacv.cpp.opencv_highgui.*;
import static com.googlecode.javacv.cpp.opencv_legacy.*;
*/
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.opencv_core.Mat;

import java.nio.file.Files;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_highgui.*;
//import static org.bytedeco.javacpp.opencv_legacy.*;

public class NeuroVideoSource implements StatelessOperator {

	private static final long serialVersionUID = 1L;
	private static final Logger logger = LoggerFactory.getLogger(NeuroVideoSource.class);
	
	private IplImage[] testIplFrames = null;
	private byte[][] testRawFrames = null;
	private final String testFramesDir = GLOBALS.valueFor("testFramesDir");
	private final String neuroEventsDir = GLOBALS.valueFor("neuroEventsDir");
	private final String neuroEventsFilename = GLOBALS.valueFor("neuroEventsFilename");
	private final Boolean getNeuroEventsByNum = Boolean.parseBoolean(GLOBALS.valueFor("getNeuroEventsByNum"));
	private final Integer maxEventsPerBatch = Integer.parseInt(GLOBALS.valueFor("maxEventsPerBatch"));
	private final Long maxDurationPerBatch = Long.parseLong(GLOBALS.valueFor("maxDurationPerBatch"));
	private final Boolean useKeyFrames = false; // Boolean.parseBoolean(GLOBALS.valueFor("useKeyFrames"));

	private BufferedReader eventsFileReader = null;
	
	public void setUp() {
		System.out.println("Setting up NEURO_VIDEO_SOURCE operator with id="+api.getOperatorId());
		try {
			eventsFileReader = new BufferedReader(new FileReader(neuroEventsDir + "/" + neuroEventsFilename));
		}
		catch (Exception e) { 
			logger.error("Exception in video source: "+e);		
			e.printStackTrace();
			System.exit(1);
		}	
		logger.info("NEURO_VIDEO_SOURCE setup complete.");
	}

	public void processData(DataTuple dt) {

		try
		{

			Map<String, Integer> mapper = api.getDataMapper();
			DataTuple data = new DataTuple(mapper, new TuplePayload());
			
			long tupleId = 0;
			
			boolean sendIndefinitely = Boolean.parseBoolean(GLOBALS.valueFor("sendIndefinitely"));
			long numTuples = Long.parseLong(GLOBALS.valueFor("numTuples"));
			long warmUpTuples = Long.parseLong(GLOBALS.valueFor("warmUpTuples"));
			boolean rateLimitSrc = Boolean.parseBoolean(GLOBALS.valueFor("rateLimitSrc"));
			long batchRate = Long.parseLong(GLOBALS.valueFor("frameRate"));
			long interBatchDelay = 1000 / batchRate;
			logger.info("Source inter-batch delay="+interBatchDelay);
			
			final long tStart = System.currentTimeMillis();
			
			NeuroFrame keyFrame = initKeyFrame();

			while(sendIndefinitely || tupleId < numTuples + warmUpTuples)
			{
				if (tupleId == warmUpTuples)
				{ 
					long tWarmedUp = System.currentTimeMillis();
					logger.info("Source sending started at t="+tWarmedUp);
					logger.info("Source sending started at t="+tWarmedUp);
					logger.info("Source sending started at t="+tWarmedUp);
				}

				NeuroEvent[] events = getNextEvents();
				NeuroFrame updatedKeyFrame = updateKeyFrame(keyFrame, events);
				NeuroBatch batch = new NeuroBatch(keyFrame, events);
				keyFrame = updatedKeyFrame;	

				DataTuple output = null;

				//output = data.newTuple(tupleId, testRawFrames[currentFrame], 0, 0, 1, 0, 0, 0, 0, "");
				output = data.newTuple(tupleId, batch, 0, 0, 1, 0, 0, 0, 0, "");
				output.getPayload().timestamp = tupleId;
				if (tupleId % 1000 == 0)
				{
					logger.info("Source sending tuple id="+tupleId+",t="+output.getPayload().instrumentation_ts);
				}
				else
				{
					logger.debug("Source sending tuple id="+tupleId+",t="+output.getPayload().instrumentation_ts);
				}
				api.send_highestWeight(output);
				
				tupleId++;
				
				long tNext = tStart + (tupleId * interBatchDelay);
				long tNow = System.currentTimeMillis();
				if (tNext > tNow && rateLimitSrc)
				{
					logger.debug("Source wait to send next frame="+(tNext-tNow));
					try {
						Thread.sleep(tNext - tNow);
					} 
					catch (InterruptedException e) {
						e.printStackTrace();
					}				
				}
			}
			if (!sendIndefinitely)
			{
				long tStop = System.currentTimeMillis() + (Long.parseLong(GLOBALS.valueFor("sourceShutdownPause")) * 1000);
				while (System.currentTimeMillis() < tStop)
				{
					logger.debug("Source waiting to shutdown="+(tStop-System.currentTimeMillis()));
					try {
						Thread.sleep(Math.max(1, tStop - System.currentTimeMillis()));
					} 
					catch (InterruptedException e) {
						e.printStackTrace();
					}				
				}
			}

		}
		catch(Exception e)
		{
			logger.error("Exception in video source: "+e);		
			e.printStackTrace();
		}
		finally
		{
			System.exit(0);
		}
	}
	
	public void processData(List<DataTuple> arg0) {
		// TODO Auto-generated method stub
		
	}

	public NeuroFrame initKeyFrame()
	{
		if (useKeyFrames)
		{
			//TODO: open the events file and find the max x and max y event
			return new NeuroFrame(100, 100);

		}
		else { return null; }
	}

	public NeuroEvent[] getNextEvents() {
		if (getNeuroEventsByNum) { return getNumEvents(maxEventsPerBatch); }
		else { return getDurationEvents(maxDurationPerBatch); }
	}

	public NeuroEvent[] getNumEvents(int num) {

		NeuroEvent[] events = new NeuroEvent[num];

		for (int i = 0; i < num; i++)
		{
			events[i] = getNextEvent();
			if (events[i] == null) 
			{
				//EOF, return
				break;
			}
		}

		return events;
	}

	public NeuroEvent[] getDurationEvents(long duration) { throw new RuntimeException("TODO"); }

	public NeuroEvent getNextEvent()
	{
		if (eventsFileReader == null) { return null; }
		try 
		{
			String line;
			while ((line = eventsFileReader.readLine()) != null)
			{
				String stripped = line.trim();
				if (!stripped.isEmpty()) {
					return parseNeuroEvent(stripped);
				}
			}
		} catch (Exception e) { 
			logger.error("Exception in video source: "+e);		
			e.printStackTrace();
			System.exit(1);
		}	

		try{ eventsFileReader.close(); }catch(IOException e) {}
		eventsFileReader = null;
		return null;
	}

	NeuroEvent parseNeuroEvent(String eventStr)
	{
		String[] fields = eventStr.split(" ");
		return new NeuroEvent(
				Integer.parseInt(fields[0]), //x
				Integer.parseInt(fields[1]),	//y 
				Long.parseLong(fields[2]), //ts 
				Integer.parseInt(fields[3]) > 0); // active
	}	


	public NeuroFrame updateKeyFrame(NeuroFrame keyFrame, NeuroEvent[] next)
	{
		if (keyFrame == null) { return null; }

		NeuroFrame updatedFrame = new NeuroFrame(keyFrame);
		for (int i = 0; i < next.length; i++)
		{

			updatedFrame.pixelStates[next[i].x][next[i].y] = next[i];
		}

		return updatedFrame;
	}

	public final static class NeuroEvent {
		int x;
		int y;
		long ts;
		boolean active;
		
		public NeuroEvent(int x, int y, long ts, boolean active)
		{
			this.x = x;
			this.y = y;
			this.ts = ts;
			this.active = active;
		}

		public NeuroEvent() { this(-1,-1,-1, false);};
	}

	public final static class NeuroFrame {

		final int xDim;
		final int yDim;
		NeuroEvent[][] pixelStates;

		public NeuroFrame(int xDim, int yDim)
		{
			this.xDim = xDim;
			this.yDim = yDim;
			this.pixelStates = new NeuroEvent[xDim][yDim]; 
		}

		public NeuroFrame(NeuroFrame other)
		{
			this.pixelStates = new NeuroEvent[other.xDim][other.yDim];
			this.xDim = other.xDim;
			this.yDim = other.yDim;
			for (int x = 0; x < xDim; x++)
			{
				for (int y = 0; y < yDim; y++)
				{
					pixelStates[x][y] = other.pixelStates[x][y];
				}
			}
		}
	}

	public final static class NeuroBatch {
		NeuroFrame startFrame;
		NeuroEvent[] events;

		public NeuroBatch(NeuroFrame sf, NeuroEvent[] e)
		{
			this.startFrame = sf;
			this.events = e;
		}	

		// For applications that don't need key frames.
		public NeuroBatch(NeuroEvent[] e)
		{
			this(null, e);
		}
	}


}
