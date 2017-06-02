package com.gfk.mri.util;

import java.net.InetAddress;
import java.util.Set;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.policies.ConstantReconnectionPolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;

public class CassandraService {
	
	

	public Session createSession(Set<InetAddress> host, String keyspace, String username, String password, Integer driverReadTimeout) {
		
		Session session = null;

		Cluster.Builder cb = Cluster.builder().addContactPoints(host)
				.withReconnectionPolicy(new ConstantReconnectionPolicy(10000));

		if (username != null && !username.isEmpty()) {
			cb = cb.withCredentials(username, password);
		}

		cb.withLoadBalancingPolicy(new RoundRobinPolicy());

		Cluster cluster = cb.build();
		cluster.getConfiguration().getSocketOptions().setReadTimeoutMillis(driverReadTimeout);

		if (keyspace != null && !keyspace.isEmpty())
			session = cluster.connect(keyspace);
		else
			session = cluster.connect();

		return session;
	}

	
	

}
