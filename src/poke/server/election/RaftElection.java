package poke.server.election;

import static java.lang.System.currentTimeMillis;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.core.Mgmt.LogEntry;
import poke.core.Mgmt.Management;
import poke.core.Mgmt.LeaderElection.ElectAction;
import poke.core.Mgmt.MgmtHeader;
import poke.core.Mgmt.RaftMessage;
import poke.core.Mgmt.RaftMessage.RaftAction;
import poke.core.Mgmt.RaftMessage.RaftAppendAction;
import poke.server.managers.ConnectionManager;
import poke.server.managers.HeartbeatManager;
import poke.server.monitor.RaftMonitor;

public class RaftElection implements Election {
	protected static Logger logger = LoggerFactory.getLogger("Raft");
	private final static int MIN_ELECTION_WAIT_TIME_IN_MILLIS = 15000;
	private final static int MAX_ELECTION_WAIT_TIME_IN_MILLIS = 30000;

	private Integer nodeID;
	private ElectionState electionState;
	private ElectionListener listener;
	private int term;
	private int electionWaitTimeMillis;
	private int voteCount = 0;
	private int leaderID;
	private RaftState currentNodeState;
	private RaftMessage votedFor;
	private LogMessage logMessage;
	private boolean appendLogs = false;
	private RaftMonitor raftMonitor;
	private long lastHeartBeatFromLeaderTimestamp;

	public enum RaftState {
		FOLLOWER, CANDIDATE, LEADER
	}

	public RaftElection() {
		this.electionWaitTimeMillis = ThreadLocalRandom.current().nextInt(
				MIN_ELECTION_WAIT_TIME_IN_MILLIS,
				MAX_ELECTION_WAIT_TIME_IN_MILLIS + 1);
		logger.info(String.format(
				"***Node will wait for %s seconds before triggering re-election****",
				electionWaitTimeMillis/1000));
		this.currentNodeState = RaftState.FOLLOWER;
		this.setLastHeartBeatFromLeaderTimestamp(currentTimeMillis());
		this.logMessage = new LogMessage();
	}

	public RaftMonitor getMonitor() {
		if (raftMonitor == null)
			raftMonitor = new RaftMonitor(this);
		return raftMonitor;
	}

	@Override
	public void setListener(ElectionListener listener) {
		this.listener = listener;

	}

	@Override
	public void clear() {
		electionState = null;

	}

	@Override
	public boolean isElectionInprogress() {
		return electionState != null;
	}

	@Override
	public Integer getElectionId() {
		if (electionState == null)
			return null;
		return electionState.electionID;
	}

	@Override
	public Integer createElectionID() {
		return ElectionIDGenerator.nextID();
	}

	@Override
	public Integer getWinner() {
		if (electionState == null)
			return null;
		else if (electionState.state.getNumber() == ElectAction.DECLAREELECTION_VALUE)
			return electionState.candidate;
		else
			return null;
	}

	public Integer getNodeID() {
		return nodeID;
	}

	public void setNodeID(Integer nodeID) {
		this.nodeID = nodeID;
	}

	public ElectionState getElectionState() {
		return electionState;
	}

	public void setElectionState(ElectionState electionState) {
		this.electionState = electionState;
	}

	public int getTerm() {
		return term;
	}

	public boolean isAppendLogs() {
		return appendLogs;
	}

	public void setAppendLogs(boolean appendLogs) {
		this.appendLogs = appendLogs;
	}

	public void setTerm(int term) {
		this.term = term;
	}

	public int getElectionWaitTime() {
		return electionWaitTimeMillis;
	}

	public void setElectionWaitTime(int electionWaitTime) {
		this.electionWaitTimeMillis = electionWaitTime;
	}

	public int getVoteCount() {
		return voteCount;
	}

	public void setVoteCount(int voteCount) {
		this.voteCount = voteCount;
	}

	public RaftMessage getVotedFor() {
		return votedFor;
	}

	public void setVotedFor(RaftMessage votedFor) {
		this.votedFor = votedFor;
	}

	public LogMessage getLogMessage() {
		return logMessage;
	}

	public void setLogMessage(LogMessage logMessage) {
		this.logMessage = logMessage;
	}

	public ElectionListener getListener() {
		return listener;
	}

	@Override
	public void setNodeId(int nodeId) {
		this.nodeID = nodeId;
	}

	public RaftState getCurrentNodeState() {
		return currentNodeState;
	}

	public void setCurrentNodeState(RaftState currentNodeState) {
		this.currentNodeState = currentNodeState;
	}

	@Override
	public Management process(Management req) {
		if (!req.hasRaftmessage()) {
			return null;
		}
		Management rtn = null;
		RaftMessage raftMessage = req.getRaftmessage();
		int actionNumber = raftMessage.getRaftAction().getNumber();
		System.out.println("Received action. action number is: " + actionNumber);
		if (actionNumber == RaftAction.REQUESTVOTE_VALUE) {
			castVote(raftMessage, req);
		} else if (actionNumber == RaftAction.VOTE_VALUE) {
			receiveVote(raftMessage, req);
		} else if (actionNumber == RaftAction.LEADER_VALUE) {
			leaderHeartBeat(raftMessage, req);
		} else if (actionNumber == RaftAction.APPEND_VALUE) {
			appendLog(raftMessage, req);
		}

		return rtn;

	}

	private void appendLog(RaftMessage raftMessage, Management req) {
		if (currentNodeState == RaftState.CANDIDATE) {
			if (raftMessage.getTerm() >= term) {
				this.setLastHeartBeatFromLeaderTimestamp(System
						.currentTimeMillis());
				this.term = raftMessage.getTerm();
				this.setLeaderID(req.getHeader().getOriginator());
				this.currentNodeState = RaftState.FOLLOWER;
				logger.info(String.format(
						"Received Append Message from Leader: %s", req
								.getHeader().getOriginator()));
			}
		} else if (currentNodeState == RaftState.FOLLOWER) {
			this.term = raftMessage.getTerm();
			this.setLastHeartBeatFromLeaderTimestamp(System.currentTimeMillis());
			logger.info("Message from:  " + req.getHeader().getOriginator()
					+ "\n RaftAction="
					+ raftMessage.getRaftAction().getNumber()
					+ " RaftAppendAction="
					+ raftMessage.getRaftAppendAction().getNumber());

			if (raftMessage.getRaftAppendAction().getNumber() == RaftAppendAction.APPENDHEARTBEAT_VALUE) {

				logger.info(String
						.format("Received AppendAction HeartBeat from Leader %s\nRaftAction: %s\nRaftAppendAction=%s",
								req.getHeader().getOriginator(), raftMessage
										.getRaftAction().getNumber(),
								raftMessage.getRaftAppendAction().getNumber()));

			} else if (raftMessage.getRaftAppendAction().getNumber() == RaftAppendAction.APPENDLOG_VALUE) {
				List<LogEntry> logEntries = raftMessage.getEntriesList();

				for (int i = this.logMessage.prevLogIndex + 1; i < logEntries
						.size(); i++) {
					LogEntry logEntry = logEntries.get(i);
					int tempindex = logEntry.getIndex();
					String value = logEntry.getData();
					this.logMessage.getEntries().put(tempindex, value);
				}

			}
		}
	}

	private void leaderHeartBeat(RaftMessage raftMessage, Management req) {
		if (raftMessage.getTerm() >= this.term) {
			this.setLeaderID(req.getHeader().getOriginator());
			this.term = raftMessage.getTerm();
			this.setLastHeartBeatFromLeaderTimestamp(System.currentTimeMillis());
			notify(true, req.getHeader().getOriginator());
			logger.info("Node " + req.getHeader().getOriginator()
					+ " is the leader ");
		}
	}

	private void receiveVote(RaftMessage raftMessage, Management req) {
		System.out.println("My current state is: " + currentNodeState.toString());
		if (currentNodeState == RaftState.CANDIDATE) {
			logger.info(String.format(
					"****Received Vote From Node: %s. Vote count is: %s*****", req
							.getHeader().getOriginator(), voteCount));
			logger.info("Size "
					+ HeartbeatManager.getInstance().outgoingHB.size());
			if (++voteCount > (HeartbeatManager.getInstance().outgoingHB.size() + 1) / 2) {
				logger.info(String.format(
						"****Final Vote Count: %s. I am the leader.*****", voteCount));
				voteCount = 0;
				currentNodeState = RaftState.LEADER;
				setLeaderID(this.nodeID);
				System.out.println(" Leader elected " + this.nodeID);
				notify(true, this.nodeID);
				ConnectionManager
						.broadcastAndFlushChannel(createRaftMessage(RaftAction.LEADER));
			}
		}

	}

	private Management castVote(RaftMessage raftMessage, Management req) {
		System.out.println("I am casting vote");

		if ((currentNodeState == RaftState.CANDIDATE)
				|| (currentNodeState == RaftState.FOLLOWER)) {
			this.setLastHeartBeatFromLeaderTimestamp(currentTimeMillis());
			if ((this.votedFor == null || raftMessage.getTerm() > this.votedFor
					.getTerm())
					&& (raftMessage.getLogIndex() >= this.logMessage
							.getLogIndex())) {
				if (this.votedFor != null) {
					logger.info(String.format("***Voting for %s for term %s***", req
							.getHeader().getOriginator(), raftMessage.getTerm()));
				}
				this.votedFor = raftMessage;
				Management response = createRaftMessage(RaftAction.VOTE);
				ConnectionManager.broadcastAndFlushChannel(response);
				return response;
			}
		}
		return null;
	}

	public void notify(boolean success, Integer leader) {
		if (listener != null) {
			listener.concludeWith(success, leader);
		}
	}

	public synchronized Management createRaftMessage(RaftAction action) {
		RaftMessage.Builder raftMessageBuilder = RaftMessage.newBuilder();
		MgmtHeader.Builder mgmtHeaderBuilder = MgmtHeader.newBuilder();
		mgmtHeaderBuilder.setTime(currentTimeMillis());
		mgmtHeaderBuilder.setSecurityCode(-999);
		mgmtHeaderBuilder.setOriginator(this.nodeID);

		// Raft Message to be added
		raftMessageBuilder.setTerm(term);
		raftMessageBuilder.setRaftAction(action);

		Management.Builder mb = Management.newBuilder();
		mb.setHeader(mgmtHeaderBuilder.build());
		mb.setRaftmessage(raftMessageBuilder.build());

		return mb.build();
	}

	public int getLeaderID() {
		return leaderID;
	}

	public void setLeaderID(int leaderID) {
		this.leaderID = leaderID;
	}

	public long getLastHeartBeatFromLeaderTimestamp() {
		return lastHeartBeatFromLeaderTimestamp;
	}

	public void setLastHeartBeatFromLeaderTimestamp(
			long lastHeartBeatFromLeaderTimestamp) {
		this.lastHeartBeatFromLeaderTimestamp = lastHeartBeatFromLeaderTimestamp;
	}

}
