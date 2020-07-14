package com.hayderhassan.objectdetector.operators;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.logging.Logger;

import com.hayderhassan.objectdetector.detector.Detection;
import com.hayderhassan.objectdetector.detector.Detector;

import uk.ac.imperial.lsds.seep.comm.serialization.DataTuple;
import uk.ac.imperial.lsds.seep.operator.StatelessOperator;

/**
 * Upon receiving a frame this will send the frame bytes to object detector and if there is at least one detection it will be sent to the sink.
 *
 * @author Hayder Hassan
 * @version 1.0
 */
public class Processor implements StatelessOperator{

  private static final long serialVersionUID = 1L;
  private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

  private Detector detector;

  public void setUp() {
    System.out.println("Setting up processor...");
    this.detector = new Detector();
    // can change this to "all" to find all targets in image
    this.detector.setTarget("bus");
  }

  public void processData(DataTuple data) {

    byte[] frameBytes = data.getByteArray("frame");

    List<Detection> results = this.detector.predict(frameBytes);

    // only send tuple to sink if a detection was found
    if (!results.isEmpty()) {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ObjectOutputStream out = null;
      try {
        out = new ObjectOutputStream(bos);   
        out.writeObject(results);
        out.flush();
        byte[] byteArray = bos.toByteArray();
        DataTuple outputTuple = data.setValues(byteArray);
        System.out.println("Sending frame from processor to sink...");
        api.send(outputTuple);
      } catch (IOException ex) {
        System.out.println("Didn't work");
      } finally {
        try {
          bos.close();
        } catch (IOException ex) {
          System.out.println("Didn't work");
        }
      }
    }

  }


  public void processData(List<DataTuple> arg0) {
    // TODO Auto-generated method stub
  }

}
