/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ode.axis2;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Polls a directory for the deployment of a new deployment unit.
 */
public class DeploymentPoller {

    private static final Log __log = LogFactory.getLog(DeploymentPoller.class);

    /** The polling interval. */
    private static final long POLL_TIME = 3000;

    private File _deployDir;

    private PollingThread _poller;

    private ODEServer _odeServer;

   
    /** Filter accepting directories containing a .odedd file. */
    private static final FileFilter _fileFilter = new FileFilter() {
        public boolean accept(File path) {
            if (path.isDirectory()) {
                return path.listFiles(new FilenameFilter() {
                    public boolean accept(File dir, String name) {
                        return name.toLowerCase().equals("deploy.xml");
                    }
                }).length == 1;
            } else
                return false;
        }
    };
    
    private static final FileFilter _deployedFilter = new FileFilter() {
        public boolean accept(File path) {
            return path.isFile() && path.getName().endsWith(".deployed");
        }
    };
    
    public DeploymentPoller(File deployDir, ODEServer odeServer) {
        _odeServer = odeServer;
        _deployDir = deployDir;
        if (!_deployDir.exists())
            _deployDir.mkdir();
    }

    public void start() {
        _poller = new PollingThread();
        _poller.start();
        __log.info("Poller started.");
    }

    public void stop() {
        _poller.kill();
        _poller = null;
    }

    /**
     * Scan the directory for new (or removed) files (called mainly from
     * {@link PollingThread}) and calls whoever is in charge of the actual
     * deployment (or undeployment).
     */
    private void check() {
        File[] files = _deployDir.listFiles(_fileFilter);

        // Checking for new deployment directories
        for (File file : files) {
            File deployXml = new File(file, "deploy.xml");
            File deployedMarker = new File(_deployDir, file.getName() + ".deployed");

            if (!deployXml.exists()) {
                // Skip if deploy.xml is abset
                __log.debug("Not deploying " + file + " (missing deploy.xml)");
            }

            if (deployedMarker.exists()) {
                __log.debug("Deployed marker for " + file + " exists, skipping deployment");
                continue;
            }

            try {
                deployedMarker.createNewFile();
            } catch (IOException e1) {
                __log.error("Error creating deployed marker file, " + file + " will not be deployed");
                continue;
            }
            try {
                _odeServer.getBpelServer().deploy(file);
                __log.info("Deployment of artifact " + file.getName() + " successful.");
            } catch (Exception e) {
                __log.error("Deployment of " + file.getName() + " failed, aborting for now.", e);
            }
        }

        // Removing deployments that disappeared
        for (File m :_deployDir.listFiles(_deployedFilter)) {
            File deployDir = new File(m.getParentFile(),m.getName().substring(0,m.getName().length() 
                    - ".deployed".length()));

            if (!deployDir.exists()) {
                // TODO handle undeploy
                
            }
        }
            
     }


    /**
     * Thread that does the actual polling for new files.
     */
    private class PollingThread extends Thread {
        private boolean _active = true;
        private byte[] _mutex = new byte[0];

        /** Stop this poller, and block until it terminates. */
        void kill() {
            synchronized (_mutex) {
                _active = false;
                _mutex.notify();
            }
            try {
                join();
            } catch (InterruptedException ie) {
                __log.fatal("Thread unexpectedly interrupted.", ie);
            }
        }

        public void run() {
            try {
                while (_active) {
                    check();
                    synchronized (_mutex) {
                        try {
                            _mutex.wait(POLL_TIME);
                        } catch (InterruptedException e) {
                        }
                    }
                }
            } catch (Throwable t) {
                __log.fatal("Encountered an unexpected error.  Exiting poller...", t);
            }
        }
    }
}
