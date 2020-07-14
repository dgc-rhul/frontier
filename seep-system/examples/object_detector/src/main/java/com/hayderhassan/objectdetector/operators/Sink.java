package com.hayderhassan.objectdetector.operators;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.util.List;

import com.hayderhassan.objectdetector.detector.Detection;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import uk.ac.imperial.lsds.seep.comm.serialization.DataTuple;
import uk.ac.imperial.lsds.seep.operator.StatelessOperator;

/**
 * THe sink receives detection results from processor and outputs it to console, as well as saving frame image to disk.
 * 
 * @author Hayder Hassan
 * @version 1.0
 */
public class Sink implements StatelessOperator {

  private static final long serialVersionUID = 1L;

  private static int imageCounter;
  private final String IMAGE_DIR = System.getenv("PROJECT_ROOT") + "/frontier/seep-system/examples/object_detector/src/main/resources/images/";

  public void setUp() {
    System.out.println("Setting up sink...");
    imageCounter = 0;
  }

  public void processData(DataTuple dt) {

    List<Detection> results = null;
    byte[] frameBytes = dt.getByteArray("frame");

    ByteArrayInputStream bis = new ByteArrayInputStream(frameBytes);
    ObjectInput in = null;
    try {
      in = new ObjectInputStream(bis);
      results = (List<Detection>) in.readObject(); 

    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } finally {
      try {
        if (in != null) {
          in.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    String resultsText = "";
    for (Detection detection : results) {
      Mat image = Imgcodecs.imdecode(new MatOfByte(detection.getImageBytes()), Imgcodecs.IMREAD_UNCHANGED);
      Imgcodecs.imwrite(IMAGE_DIR + "detections/detection" + (++imageCounter) + ".jpg", image);  
      resultsText += detection.getLabel() + " = " + detection.getConfidenceScore() + "\n";
    }
    System.out.println("\n\n\n**************\nDETECTIONS:\n\n" + resultsText + "**************\n\n\n");
  }

  public void processData(List<DataTuple> arg0) {
    // TODO Auto-generated method stub
  }
}
