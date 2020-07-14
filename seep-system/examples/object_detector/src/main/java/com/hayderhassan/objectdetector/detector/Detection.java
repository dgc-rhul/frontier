package com.hayderhassan.objectdetector.detector;

import java.io.Serializable;

/**
 * Represents a detection object consisting of a label, confidence score and bounding box
 *
 * @author Hayder Hassan
 * @version 1.0
 */
public class Detection implements Serializable {

    private static final long serialVersionUID = 1L;

    private String labelName;
    private double confidenceScore;
    private byte[] detectedImage;

    public Detection(String labelName, double confidenceScore, byte[] detectedImage) {
        this.labelName = labelName;
        this.confidenceScore = confidenceScore;
        this.detectedImage = detectedImage;
    }

    public String getLabel() {
        return this.labelName;
    }

    public double getConfidenceScore() {
        return this.confidenceScore;
    }

    public byte[] getImageBytes() {
        return this.detectedImage;
    }

}