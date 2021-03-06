package poke.server.managers;

import io.netty.channel.Channel;
import java.beans.Beans;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.core.Mgmt.Management;
import poke.core.Mgmt.MgmtHeader;
import poke.core.Mgmt.RaftMessage;
import poke.core.Mgmt.RaftMessage.RaftAction;
import poke.server.conf.ServerConf;
import poke.server.election.Election;
import poke.server.election.ElectionListener;
import poke.server.election.LogMessage;
import poke.server.election.RaftElection;
import poke.server.managers.ConnectionManager.ConnectionState;

public class RaftManager implements ElectionListener {

	protected static Logger logger = LoggerFactory.getLogger("election");
	protected static AtomicReference<RaftManager> instance = new AtomicReference<RaftManager>();

	private static ServerConf conf;
	private static long lastKnownHeartBeat = System.currentTimeMillis();
	// number of times we try to get the leader when a node starts up
	private int firstTime = 2;
	/** The election that is in progress - only ONE! */
	private Election election;
	private int electionCycle = -1;
	private Integer syncPt = 1;
	/** The leader */
	Integer leaderNode;

	public static RaftManager initManager(ServerConf conf) {
		RaftManager.conf = conf;
		instance.compareAndSet(null, new RaftManager());

		return instance.get();
	}

	public void processRequest(Management mgmt) {
		if (!mgmt.hasRaftmessage())
			return;
		RaftMessage rm = mgmt.getRaftmessage();

		if (rm.getRaftAction().getNumber() == RaftAction.WHOISTHELEADER_VALUE) {
			respondToWhoIsTheLeader(mgmt);
			return;
		}

		Management rtn = electionInstance().process(mgmt);
		if (rtn != null)
			ConnectionManager.broadcastAndFlushChannel(rtn);
	}

	/**
	 * check the health of the leader (usually called after a HB update)
	 * 
	 * @param mgmt
	 */
	public boolean assessCurrentState() {
		// give it two tries to get the leader else return true to start
		// election
		if (firstTime > 0) {
			this.firstTime--;
			logger.info("Assessing current state");
			askWhoIsTheLeader();
			return false;
		} else
			return true;// starts Elections
	}

	private void askWhoIsTheLeader() {
		logger.info("Node " + conf.getNodeId() + " is searching for the leader");
		if (whoIsTheLeader() == null) {
			logger.info("----> I cannot find the leader is! I don't know!");
			return;
		} else {
			logger.info("The Leader is " + this.leaderNode);
		}

	}

	private void respondToWhoIsTheLeader(Management mgmt) {
		if (this.leaderNode == null) {
			logger.info("----> I cannot respond to who the leader is! I don't know!");
			return;
		}
		logger.info("Node " + conf.getNodeId() + " is replying to "
				+ mgmt.getHeader().getOriginator()
				+ "'s request who the leader is. Its Node " + this.leaderNode);

		MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
		mhb.setOriginator(conf.getNodeId());
		mhb.setTime(System.currentTimeMillis());
		mhb.setSecurityCode(-999);

		RaftMessage.Builder rmb = RaftMessage.newBuilder();
		rmb.setLeaderID(this.leaderNode);
		rmb.setRaftAction(RaftAction.THELEADERIS);

		Management.Builder mb = Management.newBuilder();
		mb.setHeader(mhb.build());
		mb.setRaftmessage(rmb);
		try {

			Channel ch = ConnectionManager.getConnection(mgmt.getHeader()
					.getOriginator(), ConnectionState.SERVERMGMT);
			if (ch != null)
				ch.writeAndFlush(mb.build());

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private Election electionInstance() {
		if (election == null) {
			synchronized (syncPt) {
				if (election != null)
					return election;

				// new election
				String clazz = RaftManager.conf.getElectionImplementation();

				// if an election instance already existed, this would
				// override the current election
				try {
					election = (Election) Beans.instantiate(this.getClass()
							.getClassLoader(), clazz);
					election.setNodeId(conf.getNodeId());
					election.setListener(this);
				} catch (Exception e) {
					logger.error("Failed to create " + clazz, e);
				}
			}
		}

		return election;

	}

	public void startMonitor() {
		logger.info("Raft Monitor Started ");
		if (election == null)
			((RaftElection) electionInstance()).getMonitor().start();
	}

	public Integer whoIsTheLeader() {
		return this.leaderNode;
	}

	/** election listener implementation */
	public void concludeWith(boolean success, Integer leaderID) {
		if (success) {
			logger.info("----> the leader is " + leaderID);
			this.leaderNode = leaderID;
		}

		election.clear();
	}

	public void createLogs(String imageName) {
		LogMessage lm = ((RaftElection) electionInstance()).getLogMessage();
		Integer logIndex = lm.getLogIndex();
		LinkedHashMap<Integer, String> entries = lm.getEntries();
		lm.setPrevLogIndex(logIndex);
		lm.setLogIndex(++logIndex);
		entries.put(logIndex, imageName);
		lm.setEntries(entries);
		((RaftElection) electionInstance()).setAppendLogs(true);

	}

	// Setters and Getters
	public static Logger getLogger() {
		return logger;
	}

	public static void setLogger(Logger logger) {
		RaftManager.logger = logger;
	}

	public static RaftManager getInstance() {
		return instance.get();
	}

	public static void setInstance(AtomicReference<RaftManager> instance) {
		RaftManager.instance = instance;
	}

	public static ServerConf getConf() {
		return conf;
	}

	public static void setConf(ServerConf conf) {
		RaftManager.conf = conf;
	}

	public static long getLastKnownBeat() {
		return lastKnownHeartBeat;
	}

	public static void setLastKnownBeat(long lastKnownBeat) {
		RaftManager.lastKnownHeartBeat = lastKnownBeat;
	}

	public int getFirstTime() {
		return firstTime;
	}

	public void setFirstTime(int firstTime) {
		this.firstTime = firstTime;
	}

	public Election getElection() {
		return election;
	}

	public void setElection(Election election) {
		this.election = election;
	}

	public int getElectionCycle() {
		return electionCycle;
	}

	public void setElectionCycle(int electionCycle) {
		this.electionCycle = electionCycle;
	}

	public Integer getSyncPt() {
		return syncPt;
	}

	public void setSyncPt(Integer syncPt) {
		this.syncPt = syncPt;
	}

	public Integer getLeaderNode() {
		return leaderNode;
	}

	public void setLeaderNode(Integer leaderNode) {
		this.leaderNode = leaderNode;
	}
	
	/**For the inter-cluster configuration**/
	//instantiate the cluster conf list
	/*private ClusterConfList clusterConfList;
	 * include the clusterConfList in initManager method
	 * The inter cluster set-up is done after the leader election in clusters are done.
	 */

}