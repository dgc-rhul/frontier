package com.hayderhassan.objectdetector;

import java.util.ArrayList;

import com.hayderhassan.objectdetector.operators.Processor;
import com.hayderhassan.objectdetector.operators.Sink;
import com.hayderhassan.objectdetector.operators.Source;

import org.opencv.core.Core;

import uk.ac.imperial.lsds.seep.GLOBALS;
import uk.ac.imperial.lsds.seep.api.QueryBuilder;
import uk.ac.imperial.lsds.seep.api.QueryComposer;
import uk.ac.imperial.lsds.seep.api.QueryPlan;
import uk.ac.imperial.lsds.seep.operator.Connectable;

/**
 * The base class sets up the frontier system, declares the operators and connects them together.
 *
 * @author Hayder Hassan
 * @version 1.0
 */
public class Base implements QueryComposer{

  private int replicationFactor;

  public QueryPlan compose() {    
    System.out.println(Core.NATIVE_LIBRARY_NAME);
    System.out.println("OpenCV version: " + Core.VERSION);
    this.replicationFactor = Integer.parseInt(GLOBALS.valueFor("replicationFactor"));
  
    // Declare Source
    ArrayList<String> srcFields = new ArrayList<String>();
    srcFields.add("frame");
    Connectable src = QueryBuilder.newStatelessSource(new Source(), -1, srcFields);

    // Declare processor
    ArrayList<String> pFields = new ArrayList<String>();
    pFields.add("frame");
    Connectable p = QueryBuilder.newStatelessOperator(new Processor(), 1, pFields);

    // Declare sink
    ArrayList<String> snkFields = new ArrayList<String>();
    snkFields.add("frame");
    Connectable snk = QueryBuilder.newStatelessSink(new Sink(), -2, snkFields);

    // Connect the operators
    src.connectTo(p, true, 0);
    p.connectTo(snk, true, 0);

    // Set the number of replicas, default is 1
    QueryBuilder.scaleOut(p.getOperatorId(), this.replicationFactor);

    return QueryBuilder.build();
  }
}