package com.hayderhassan.objectdetector.detector;

import static org.opencv.dnn.Dnn.blobFromImage;
import static org.opencv.dnn.Dnn.readNetFromTensorflow;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Net;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

/**
 * A class to represent the object detector
 *
 * The Detector class can be used to find all the objects in a given image, or it can be used to check if a specific
 * object is in the image.
 *
 * @author Hayder Hassan
 * @version 1.0
 */
public class Detector {

    private Net net;
    private HashMap<Integer, String> labelMap;
    private String modelDir;
    private String target;
    private static final double CONFIDENCE_THRESHOLD = 0.8;
    
    public Detector() {
        this.modelDir = System.getenv("PROJECT_ROOT") + "/frontier/seep-system/examples/object_detector/src/main/resources/model/";
        String model = this.modelDir + "ssd_inception_v2_coco.pb";
        String config = this.modelDir + "ssd_inception_v2_coco.pbtxt";
        this.net = readNetFromTensorflow(model, config);
        this.labelMap = loadLabels(this.modelDir + "labelmap.txt");
        this.target = "all";
        System.out.println("Detector initialised!");
    }

    public HashMap<Integer, String> loadLabels(String labelFilePath) {
        HashMap<Integer, String> labelMap = new HashMap<>();
        File labelFile = new File(Paths.get(labelFilePath).toUri());

        try {
            BufferedReader br = new BufferedReader(new FileReader(labelFile));
            int id = 0;
            String label;
            while ((label = br.readLine()) != null) {
                labelMap.put(id, label);
                id++;
            }
        } catch (Exception e) {
            System.err.println("Failed to extract labels: " + e.getMessage());
            System.exit(1);
        }

        return labelMap;
    }

    public void setInput(Mat image) {
        Size scaleSize = new Size(300, 300);
        Scalar mean = new Scalar(127.5, 127.5, 127.5);
        Mat input = blobFromImage(image, (1.0 / 255.0) * 2, scaleSize, mean, true, false);
        this.net.setInput(input);
    }

    public void setTarget(String target) {
        this.target = this.labelMap.containsValue(target.toLowerCase()) ? target.toLowerCase(): "all";
    }

    /**
     * Given a byte array representing an image, this will convert bytes to Mat and then return a list of Detections. It calls predict(Mat image).
     * 
     * @return list of detections found.
     */
    public List<Detection> predict(byte[] imageBytes) {
        Mat image = Imgcodecs.imdecode(new MatOfByte(imageBytes), Imgcodecs.IMREAD_UNCHANGED);
        return predict(image);
    }

    /**
     * Given an image in the form of a Mat this will return a list of Detections found.
     * 
     * @param image the mat which is to be put through object detector.
     * @return a list of detections found in image.
     */
    public List<Detection> predict(Mat image) {
        setInput(image);

        Mat detections = net.forward();
        // this converts NN output into a format where the results can be extracted
        detections = detections.reshape(1, (int) detections.total() / 7);

        List<Detection> results = new ArrayList<>();

        int cols = image.cols();
        int rows = image.rows();

        for (int i = 0; i < detections.rows(); ++i) {
            double confidenceScore = detections.get(i, 2)[0];
            if (confidenceScore >= CONFIDENCE_THRESHOLD) {
                int classId = (int) detections.get(i, 1)[0];
                String labelName = this.labelMap.get(classId);
                if (this.target.equals(labelName) || this.target.equals("all")) {
                    // get four corners of detected object
                    int left = (int) (detections.get(i, 3)[0] * cols);
                    int top = (int) (detections.get(i, 4)[0] * rows);
                    int right = (int) (detections.get(i, 5)[0] * cols);
                    int bottom = (int) (detections.get(i, 6)[0] * rows);
                    Rect box = new Rect(left, top, right-left, bottom-top);
                    // copy image so bounding box can be overlaid on it
                    Mat detectionImage = image.clone();
                    int[] baseLine = new int[1];
                    String label = labelName + ": " + confidenceScore;
                    Size labelSize = Imgproc.getTextSize(label, Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, 1, baseLine);
                    Imgproc.rectangle(detectionImage, box, new Scalar(0, 255, 0));
                    Imgproc.rectangle(detectionImage, new Point(left, top - labelSize.height), new Point(left + labelSize.width, top + baseLine[0]), new Scalar(255, 255, 255), Imgproc.FILLED);
                    Imgproc.putText(detectionImage, label, new Point(left, top), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0, 0, 0));
                    MatOfByte matOfByte = new MatOfByte();
                    Imgcodecs.imencode(".jpg", detectionImage, matOfByte);
                    byte[] detectedImageBytes = matOfByte.toArray();
                    // create a Detection object to add to list of detections
                    Detection detection = new Detection(labelName, confidenceScore, detectedImageBytes);
                    results.add(detection);
                }
            }
        }

        return results;
    }

}