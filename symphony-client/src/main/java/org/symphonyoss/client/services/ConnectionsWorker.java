/*
 *
 * Copyright 2016 The Symphony Software Foundation
 *
 * Licensed to The Symphony Software Foundation (SSF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.symphonyoss.client.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.symphonyoss.client.SymphonyClient;
import org.symphonyoss.exceptions.ConnectionsException;
import org.symphonyoss.symphony.clients.model.SymUserConnection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Created by Frank Tarsillo on 5/15/2016.
 */
class ConnectionsWorker implements Runnable {
    private final SymphonyClient symClient;
    private final ConnectionsListener connectionsListener;
    private final ConcurrentHashMap<Long, SymUserConnection> pendingConnections = new ConcurrentHashMap<Long, SymUserConnection>();
    private final Logger logger = LoggerFactory.getLogger(ConnectionsWorker.class);
    private boolean KILL = false;


    public ConnectionsWorker(SymphonyClient symClient, ConnectionsListener connectionsListener) {
        this.symClient = symClient;
        this.connectionsListener = connectionsListener;

    }


    //Lets look for all new pending requests..
    public void run() {

        logger.info("Starting connections service worker..");

        List<SymUserConnection> symUserConnectionList;

        while (true) {
            try {

                try {
                    symUserConnectionList = symClient.getConnectionsClient().getIncomingRequests();

                    //logger.debug("Connections queue..{}",symUserConnectionList.size());

                } catch (ConnectionsException e) {

                    logger.error("Pending connections request retrieval failure", e);
                    try {
                        TimeUnit.SECONDS.sleep(30);
                    } catch (InterruptedException ie) {
                    }
                    continue;
                }


                if (symUserConnectionList != null)
                    for (SymUserConnection symUserConnection : symUserConnectionList) {

                        SymUserConnection cUserConnection = pendingConnections.get(symUserConnection.getUserId());

                        if (cUserConnection == null) {
                            pendingConnections.put(symUserConnection.getUserId(), symUserConnection);
                            connectionsListener.onConnectionNotification(symUserConnection);
                            logger.debug("Received new pending connection request from {}...", symUserConnection.getUserId());
                            continue;
                        }


                        if (cUserConnection.getStatus() != symUserConnection.getStatus()) {

                            logger.debug("Connection status changed for {}: from: {}  to:{}", cUserConnection.getUserId(), cUserConnection.getStatus().toString(), symUserConnection.getStatus().toString());
                            pendingConnections.remove(symUserConnection.getUserId());
                            connectionsListener.onConnectionNotification(symUserConnection);

                        }


                    }

                if (KILL) {
                    logger.debug("Connections worker thread killed..");
                    return;
                }

                try {
                    TimeUnit.SECONDS.sleep(30);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } catch (Exception bad) {
                logger.error("Serious failure in connections worker thread..please verify stacktrace.", bad);
            }
        }


    }


    public void kill() {
        KILL = true;

    }


}
