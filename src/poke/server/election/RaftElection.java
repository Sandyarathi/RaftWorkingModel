package poke.server.election;

import poke.core.Mgmt.Management;
import poke.core.Mgmt.LeaderElection.ElectAction;

public class RaftElection implements Election {

	private Integer nodeId;
	private ElectionState current;
	private ElectionListener listener;
	
	public RaftElection(Integer nodeId) {
		this.nodeId = nodeId;
	}
	@Override
	public void setListener(ElectionListener listener) {
		this.listener=listener;

	}

	@Override
	public void clear() {
		current = null;

	}

	@Override
	public boolean isElectionInprogress() {
		return current != null;
	}

	@Override
	public Integer getElectionId() {
		if (current == null)
			return null;
		return current.electionID;
	}

	@Override
	public Integer createElectionID() {
		return ElectionIDGenerator.nextID();
	}

	@Override
	public Integer getWinner() {
		if (current == null)
			return null;
		else if (current.state.getNumber() == ElectAction.DECLAREELECTION_VALUE)
			return current.candidate;
		else
			return null;
	}

	@Override
	public Management process(Management req) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setNodeId(int nodeId) {
		this.nodeId = nodeId;

	}

}
