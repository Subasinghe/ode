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

package org.apache.ode.bpel.engine.migration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.ode.bpel.dao.BpelDAOConnection;
import org.apache.ode.bpel.dao.ProcessDAO;
import org.apache.ode.bpel.dao.ProcessInstanceDAO;
import org.apache.ode.bpel.engine.BpelProcess;
import org.apache.ode.bpel.engine.IMAManager;
import org.apache.ode.bpel.engine.IMAManager2;
import org.apache.ode.jacob.vpu.ExecutionQueueImpl;

/**
 * Migrates OutstandingRequestManager to IMAManager
 *
 */
public class IMAManagerMigration implements Migration {
    private static Logger __log = LoggerFactory.getLogger(IMAManagerMigration.class);

    public boolean migrate(Set<BpelProcess> registeredProcesses, BpelDAOConnection connection) {
        boolean migrationResult = true;
        for (BpelProcess process : registeredProcesses) {
            ProcessDAO processDao = connection.getProcess(process.getConf().getProcessId());
            Collection<ProcessInstanceDAO> pis = processDao.getActiveInstances();

            for (ProcessInstanceDAO instance : pis) {
                __log.debug("Migrating from IMAManager to IMAManager2 for instance " + instance.getInstanceId());

                try {
                    if (instance.getExecutionState() == null) {
                        //Completed instance
                        __log.debug("Skipped");
                    } else {
                        ExecutionQueueImpl soup = new ExecutionQueueImpl(this.getClass().getClassLoader());
                        soup.setReplacementMap(process.getReplacementMap(processDao.getProcessId()));
                        soup.read(new ByteArrayInputStream(instance.getExecutionState()));
                        Object data = soup.getGlobalData();
                        if (data instanceof IMAManager) {
                            IMAManager imaOld = (IMAManager) data;

                            soup.setGlobalData(imaOld.toIMAManager2());

                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            soup.write(bos);
                            instance.setExecutionState(bos.toByteArray());
                            __log.debug("Migrated outstanding requests for instance " + instance.getInstanceId());
                        }
                    }
                } catch (Exception e) {
                    __log.debug("", e);
                    __log.error("Error migrating outstanding requests for instance " + instance.getInstanceId());
                    migrationResult = false;
                }
            }
        }

        return migrationResult;
    }
}
