package com.hayderhassan.objectdetector.operators;

import java.util.List;
import java.util.Map;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import uk.ac.imperial.lsds.seep.comm.serialization.DataTuple;
import uk.ac.imperial.lsds.seep.comm.serialization.messages.TuplePayload;
import uk.ac.imperial.lsds.seep.operator.StatelessOperator;

/**
 * The source iterates through frames in a video and sends it to the processor
 *
 * @author Hayder Hassan
 * @version 1.0
 */
public class Source implements StatelessOperator {

  private static final long serialVersionUID = 1L;

  private static final String VIDEO_DIR = System.getenv("PROJECT_ROOT") + "/frontier/seep-system/examples/object_detector/src/main/resources/videos/";
  private static int frameNumber;
  private VideoCapture video;
  private static int fps;
  private static int frameCount;

  public void setUp() {
    System.out.println("Setting up source...");
    frameNumber = 0;

    // Use a webcam
    // this.video = new VideoCapture(0);
    
    // Use a video file
    this.video = new VideoCapture(VIDEO_DIR + "london_traffic.mp4");
    fps = (int) this.video.get(Videoio.CAP_PROP_FPS);
    frameCount = (int) this.video.get(Videoio.CAP_PROP_FRAME_COUNT);
    System.out.println("\nTOTAL FRAMES = " + frameCount + "\n");
    System.out.println("FPS = " + fps);
  }

  public void processData(DataTuple dt) {
    Map<String, Integer> mapper = api.getDataMapper();
    DataTuple data = new DataTuple(mapper, new TuplePayload());

    DataTuple output;

    Mat frame = new Mat();

    if (video.isOpened()) {
        while (video.read(frame)) {
            // this is true when the last frame has been reached
            if (frame.empty()) {
                break;
            }
            // process a frame that corresponds with each second in a video stream
            if (frameNumber % fps == 0 || frameNumber == 0) {
                // convert frame to byte array
                MatOfByte matOfByte = new MatOfByte();
                Imgcodecs.imencode(".jpg", frame, matOfByte);
                byte[] byteArray = matOfByte.toArray();
                output = data.newTuple(byteArray);
                System.out.println("\nSending frame no. " + frameNumber + " (second: " + (frameNumber / fps) + ") from source to processor...");
                api.send(output);
            }
            frameNumber++;
        }
    }

  }

  public void processData(List<DataTuple> arg0) {
    // TODO Auto-generated method stub
  }
}
