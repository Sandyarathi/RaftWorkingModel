package poke.server.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.core.Mgmt.LogEntry;
import poke.core.Mgmt.Management;
import poke.core.Mgmt.MgmtHeader;
import poke.core.Mgmt.RaftMessage;
import poke.core.Mgmt.RaftMessage.RaftAction;
import poke.core.Mgmt.RaftMessage.RaftAppendAction;
import poke.server.election.RaftElection;
import poke.server.election.RaftElection.RaftState;
import poke.server.managers.ConnectionManager;
import poke.server.managers.HeartbeatManager;
import poke.server.managers.RaftManager;

public class RaftMonitor extends Thread {
	protected static Logger logger = LoggerFactory.getLogger("RaftMonitor");

	private RaftElection raftElection;
	private boolean appendLogs = false;

	public RaftMonitor(RaftElection raftElection) {
		this.raftElection = raftElection;
	}

	@Override
	public void run() {
		while (true) {
			try {
				Thread.sleep(11000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (raftElection.getCurrentNodeState() == RaftState.LEADER)
				ConnectionManager.broadcastAndFlushChannel(sendAppendNotice());

			else {
				boolean startElection = RaftManager.getInstance().assessCurrentState();
				if (startElection) {
					long now = System.currentTimeMillis();
					if ((now - raftElection
							.getLastHeartBeatFromLeaderTimestamp()) > raftElection
							.getElectionWaitTime())
						startElection();
				}
			}
		}
	}

	private void startElection() {
		logger.info(String
				.format("No HeartBeat from leader. I am declaring election for term %s",
						raftElection.getTerm() + 1));
	
		raftElection.setLastHeartBeatFromLeaderTimestamp(System.currentTimeMillis());
		raftElection.setCurrentNodeState(RaftState.CANDIDATE);
		raftElection.setVoteCount(1);
		raftElection.setTerm(raftElection.getTerm()+1);
		if (HeartbeatManager.getInstance().outgoingHB.size() == 0) {
			//then this is a single node cluster
			raftElection.notify(true, raftElection.getNodeID());
			raftElection.setVoteCount(0);
			raftElection.setCurrentNodeState(RaftState.LEADER);
			raftElection.setLeaderID(raftElection.getNodeID());
			logger.info(" Leader elected " + raftElection.getNodeID());
			ConnectionManager.broadcastAndFlushChannel(raftElection.createRaftMessage(RaftAction.LEADER));
		}

		else {
			ConnectionManager.broadcastAndFlushChannel(raftElection.createRaftMessage(RaftAction.REQUESTVOTE));
		}

	}

	public Management sendAppendNotice() {
		logger.info(String.format(
				"Leader Node: %s is sending appendAction HeartBeat",
				raftElection.getNodeID()));

		RaftMessage.Builder raftMessageBuilder = RaftMessage.newBuilder();
		MgmtHeader.Builder mgmtHeaderBuilder = MgmtHeader.newBuilder();
		mgmtHeaderBuilder.setTime(System.currentTimeMillis());
		mgmtHeaderBuilder.setSecurityCode(-999);
		mgmtHeaderBuilder.setOriginator(raftElection.getNodeID());

		if (this.appendLogs) {
			int tempLogIndex = raftElection.getLogMessage().getLogIndex();
			raftMessageBuilder.setPreviousTerm(raftElection.getLogMessage()
					.getPrevLogTerm());
			raftMessageBuilder.setLogIndex(tempLogIndex);
			raftMessageBuilder.setPreviousLogIndex(raftElection.getLogMessage()
					.getPrevLogIndex());

			for (Integer key : raftElection.getLogMessage().getEntries()
					.keySet()) {
				LogEntry.Builder logEntry = LogEntry.newBuilder();
				String value = raftElection.getLogMessage().getEntries()
						.get(key);
				logEntry.setIndex(key);
				logEntry.setData(value);
				raftMessageBuilder.setEntries(key, logEntry);
			}
			raftMessageBuilder.setRaftAppendAction(RaftAppendAction.APPENDLOG);
			this.appendLogs = false;
		} else {
			raftMessageBuilder
					.setRaftAppendAction(RaftAppendAction.APPENDHEARTBEAT);
		}

		raftMessageBuilder.setTerm(raftElection.getTerm());
		raftMessageBuilder.setRaftAction(RaftAction.APPEND);
		// Raft Message to be added
		Management.Builder mb = Management.newBuilder();
		mb.setHeader(mgmtHeaderBuilder.build());
		mb.setRaftmessage(raftMessageBuilder.build());
		return mb.build();
	}
}
