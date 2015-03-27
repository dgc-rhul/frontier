package uk.ac.imperial.lsds.seep.manet;

import java.net.InetAddress;
import java.util.Map;

import uk.ac.imperial.lsds.seep.comm.serialization.controlhelpers.DownUpRCtrl;
import uk.ac.imperial.lsds.seep.manet.GraphUtil.InetAddressNodeId;

public class AbstractRouter implements IRouter {

	@Override
	public Integer route(long batchId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void handleDownUp(DownUpRCtrl downUp) {
		// TODO Auto-generated method stub

	}
	
	@Override
	public void updateNetTopology(
			Map<InetAddressNodeId, Map<InetAddressNodeId, Double>> linkState) {
		throw new RuntimeException("Logic error");		
	}
}