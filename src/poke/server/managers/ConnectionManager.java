package poke.server.managers;

import io.netty.channel.Channel;

import java.util.HashMap;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.comm.App.Request;
import poke.core.Mgmt.Management;

/**
 * the connection map for server-to-server communication.
 * 
 * Note the connections/channels are initialized through the heartbeat manager
 * as it starts (and maintains) the connections through monitoring of processes.
 * 
 * 
 * TODO refactor to make this the consistent form of communication for the rest
 * of the code
 * 
 * @author gash
 * 
 */
public class ConnectionManager {
	protected static Logger logger = LoggerFactory.getLogger("management");

	/** node ID to channel */
	private static HashMap<Integer, Channel> appconnections = new HashMap<Integer, Channel>();
	private static HashMap<Integer, Channel> connections = new HashMap<Integer, Channel>();
	private static HashMap<Integer, Channel> mgmtConnections = new HashMap<Integer, Channel>();

	public static enum ConnectionState {
		SERVERAPP, SERVERMGMT, CLIENTAPP
	};

	public static void addConnection(Integer nodeId, Channel channel,
			ConnectionState state) {
		logger.info("ConnectionManager adding connection to " + nodeId);

		if (state == ConnectionState.SERVERMGMT)
			mgmtConnections.put(nodeId, channel);
		else if (state == ConnectionState.CLIENTAPP) {
			connections.put(nodeId, channel);
			logger.info("Client Added in Connection Manager " + nodeId);
		} else if (state == ConnectionState.SERVERAPP) {
			appconnections.put(nodeId, channel);
			logger.info("Server Added in Connection Manager " + nodeId);
		}
	}

	public static Channel getConnection(Integer nodeId, ConnectionState state) {
		if (state == ConnectionState.SERVERMGMT)
			return mgmtConnections.get(nodeId);
		else if (state == ConnectionState.CLIENTAPP) {
			logger.info("Client got in Connection Manager " + nodeId);
			return connections.get(nodeId);

		} else if (state == ConnectionState.SERVERAPP) {
			logger.info("Node got in Connection Manager " + nodeId);
			return appconnections.get(nodeId);
		}
		return null;

	}

	public synchronized static void removeConnection(Integer nodeId,
			ConnectionState state) {
		if (state == ConnectionState.SERVERMGMT)
			mgmtConnections.remove(nodeId);
		else if (state == ConnectionState.CLIENTAPP) {
			connections.remove(nodeId);
		} else if (state == ConnectionState.SERVERAPP)
			appconnections.remove(nodeId);
	}

	public synchronized static void removeConnection(Channel channel,
			ConnectionState state) {
		if (state == ConnectionState.SERVERMGMT) {
			if (!mgmtConnections.containsValue(channel)) {
				return;
			}
			logger.info("Management Connections Size : "
					+ ConnectionManager.getNumMgmtConnections());
			for (Integer nid : mgmtConnections.keySet()) {
				if (channel == mgmtConnections.get(nid)) {
					mgmtConnections.remove(nid);
					break;
				}
			}
			logger.info("Management Connections Size After Removal: "
					+ ConnectionManager.getNumMgmtConnections());
		} else if (state == ConnectionState.CLIENTAPP) {
			if (!connections.containsValue(channel)) {
				return;
			}

			for (Integer nid : connections.keySet()) {
				if (channel == connections.get(nid)) {
					connections.remove(nid);
					break;
				}
			}
		} else if (state == ConnectionState.SERVERAPP) {
			if (!appconnections.containsValue(channel)) {
				return;
			}

			for (Integer nid : appconnections.keySet()) {
				if (channel == appconnections.get(nid)) {
					appconnections.remove(nid);
					break;
				}
			}

		}
	}

	public synchronized static void broadcast(Request req) {
		if (req == null)
			return;

		for (Channel ch : connections.values())
			ch.writeAndFlush(req);
	}

	public synchronized static void broadcastServers(Request req) {
		if (req == null)
			return;
		System.out.println("Broadcast to servers");
		System.out.println("Length " + appconnections.keySet().size());
		for (Channel ch : appconnections.values()) {
			System.out.println("app connections");
			ch.writeAndFlush(req);
		}
	}

	public synchronized static void broadcast(Management mgmt) {
		if (mgmt == null)
			return;

		for (Channel ch : mgmtConnections.values())
			ch.write(mgmt);
	}

	public synchronized static void broadcastAndFlushChannel(Management mgmt) {
		if (mgmt == null)
			return;

		for (Channel ch : mgmtConnections.values())
			ch.writeAndFlush(mgmt);
	}

	public static int getNumMgmtConnections() {
		return mgmtConnections.size();
	}

	public synchronized static void sendToNode(Request req, Integer destination) {
		if (req == null)
			return;
		if (appconnections.get(destination) != null) {
			appconnections.get(destination).writeAndFlush(req);
		} else
			System.out.println("No destination found");
	}

	public synchronized static void sendToClient(Request req,
			Integer destination) {
		if (req == null)
			return;
		if (connections.get(destination) != null) {
			connections.get(destination).writeAndFlush(req);
		} else
			System.out.println("No clients found");
	}

	public static Set<Integer> getKeySetConnections(ConnectionState state) {
		if (state == ConnectionState.CLIENTAPP)
			return connections.keySet();
		else if (state == ConnectionState.SERVERAPP)
			return appconnections.keySet();
		else if (state == ConnectionState.SERVERMGMT)
			return mgmtConnections.keySet();
		else
			return null;
	}

	public static boolean checkClient(ConnectionState state, int key) {
		if (state == ConnectionState.CLIENTAPP)
			return connections.containsKey(key);
		else if (state == ConnectionState.SERVERAPP)
			return appconnections.containsKey(key);
		else if (state == ConnectionState.SERVERMGMT)
			return mgmtConnections.containsKey(key);
		else
			return false;
	}
}
