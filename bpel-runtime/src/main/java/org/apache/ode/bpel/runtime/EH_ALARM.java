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
package org.apache.ode.bpel.runtime;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.ode.bpel.common.FaultException;
import org.apache.ode.bpel.explang.EvaluationContext;
import org.apache.ode.bpel.explang.EvaluationException;
import org.apache.ode.bpel.obj.OEventHandler;
import org.apache.ode.bpel.obj.OScope;
import org.apache.ode.bpel.runtime.channels.EventHandlerControl;
import org.apache.ode.bpel.runtime.channels.FaultData;
import org.apache.ode.bpel.runtime.channels.ParentScope;
import org.apache.ode.bpel.runtime.channels.Termination;
import org.apache.ode.bpel.runtime.channels.TimerResponse;
import org.apache.ode.jacob.CompositeProcess;
import org.apache.ode.jacob.ReceiveProcess;
import org.apache.ode.jacob.Synch;
import org.w3c.dom.Element;

import static org.apache.ode.jacob.ProcessUtil.compose;


/**
 * Alarm event handler. This process template manages a single alarm event handler.
 * It acts like an activity in that it can be terminated, but also adds a channel for
 * "soft" termination (aka stopping) to deal with the case when the scope that owns
 * the event handler completes naturally.
 */
class EH_ALARM extends BpelJacobRunnable {

    private static final Logger __log = LoggerFactory.getLogger(EH_ALARM.class);

    private static final long serialVersionUID = 1L;

    private ParentScope _psc;
    private Termination _tc;
    private OEventHandler.OAlarm _oalarm;
    private ScopeFrame _scopeFrame;
    private EventHandlerControl _cc;
    private Set<CompensationHandler> _comps = new HashSet<CompensationHandler>();

    /**
     * Concretion constructor.
     * @param psc a link to our parent.
     * @param tc channel we listen on for termination requests
     * @param cc channel we listen on for "stop" requests
     * @param o our prototype / compiled representation
     * @param scopeFrame the {@link ScopeFrame} in which we are executing
     */
    EH_ALARM(ParentScope psc, Termination tc, EventHandlerControl cc, OEventHandler.OAlarm o, ScopeFrame scopeFrame) {
        _psc = psc;
        _tc = tc;
        _cc = cc;
        _scopeFrame = scopeFrame;
        _oalarm  = o;
    }

    public void run() {

        Calendar alarm = Calendar.getInstance();

        if (_oalarm.getForExpr() != null)
            try {
                getBpelRuntimeContext().getExpLangRuntime().evaluateAsDuration(_oalarm.getForExpr(), getEvaluationContext()).addTo(alarm);
            } catch (EvaluationException e) {
                throw new InvalidProcessException(e);
            } catch (FaultException e) {
                __log.error("",e);
                _psc.completed(createFault(e.getQName(),_oalarm.getForExpr()), _comps);
                return;
            }
        else if (_oalarm.getUntilExpr() != null)
            try {
                alarm.setTime(getBpelRuntimeContext().getExpLangRuntime().evaluateAsDate(_oalarm.getUntilExpr(), getEvaluationContext()).getTime());
            } catch (EvaluationException e) {
                throw new InvalidProcessException(e);
            } catch (FaultException e) {
                __log.error("",e);
                _psc.completed(createFault(e.getQName(),_oalarm.getUntilExpr()), _comps);
                return;
            }
        else if (_oalarm.getRepeatExpr() != null)
            try {
                getBpelRuntimeContext().getExpLangRuntime().evaluateAsDuration(_oalarm.getRepeatExpr(), getEvaluationContext()).addTo(alarm);
            } catch (EvaluationException e) {
                throw new InvalidProcessException(e);
            } catch (FaultException e) {
                __log.error("",e);
                _psc.completed(createFault(e.getQName(),_oalarm.getRepeatExpr()), _comps);
                return;
            }

        // We reduce to waiting for the alarm to be triggered.
        instance(new WAIT(alarm));
    }

    protected EvaluationContext getEvaluationContext() {
        return new ExprEvaluationContextImpl(_scopeFrame,getBpelRuntimeContext());
    }

    /**
     * Template used to wait until a given time, reduing to a {@link FIRE} after the
     * elapsed time. This template also monitors the termination and event-control channels
     * for requests from parent.
     */
    private class WAIT extends BpelJacobRunnable {
        private static final long serialVersionUID = -1426724996925898213L;
        Calendar _alarm;

        /**
         * Concretion constructor.
         * @param alarm date at which time to fire. If null, then we wait forever (for control channels handling)
         */
        WAIT(Calendar alarm) {
            _alarm = alarm;
        }

        public void run() {
            Calendar now = Calendar.getInstance();

            CompositeProcess listeners = compose(new ReceiveProcess() {
                private static final long serialVersionUID = -7750428941445331236L;
            }.setChannel(_cc).setReceiver(new EventHandlerControl() {
                public void stop() {
                    _psc.completed(null, _comps);
                }
            })).or(new ReceiveProcess() {
                private static final long serialVersionUID = 6100105997983514609L;
            }.setChannel(_tc).setReceiver(new Termination() {
                public void terminate() {
                    _psc.completed(null, _comps);
                }
            }));

            if (_alarm == null) {
                object(false, listeners);
            } else if (now.before(_alarm)) {
                TimerResponse trc = newChannel(TimerResponse.class);
                getBpelRuntimeContext().registerTimer(trc,_alarm.getTime());

                listeners.or(new ReceiveProcess() {
                    private static final long serialVersionUID = 1110683632756756017L;
                }.setChannel(trc).setReceiver(new TimerResponse(){
                    public void onTimeout() {
                        // This is what we are waiting for, fire the activity
                        instance(new FIRE());
                    }

                    public void onCancel() {
                        _psc.completed(null, _comps);
                    }
                }));
                object(false, listeners);
            } else /* now is later then alarm time */ {
                // If the alarm has passed we fire the nested activity
                ActivityInfo child = new ActivityInfo(genMonotonic(),
                    _oalarm.getActivity(),
                    newChannel(Termination.class), newChannel(ParentScope.class));
                instance(createChild(child, _scopeFrame, new LinkFrame(null) ));
                instance(new ACTIVE(child));
            }
        }
    }

    /**
     * Snipped that fires the alarm activity.
     */
    private class FIRE extends BpelJacobRunnable {
        private static final long serialVersionUID = -7261315204412433250L;

        public void run() {
            // Start the child activity.
            ActivityInfo child = new ActivityInfo(genMonotonic(),
                _oalarm.getActivity(),
                newChannel(Termination.class), newChannel(ParentScope.class));
            instance(createChild(child, _scopeFrame, new LinkFrame(null) ));
            instance(new ACTIVE(child));
        }
    }

    /**
     * Snippet that is used to monitor a running activity.
     */
    private class ACTIVE extends BpelJacobRunnable {
        private static final long serialVersionUID = -2166253425722769701L;

        private ActivityInfo _activity;

        /** Indicates whether our parent has requested a stop. */
        private boolean _stopped = false;

        ACTIVE(ActivityInfo activity) {
            _activity = activity;
        }

        public void run() {
            object(false, compose(new ReceiveProcess() {
                private static final long serialVersionUID = -3357030137175178040L;
            }.setChannel(_activity.parent).setReceiver(new ParentScope() {
                public void compensate(OScope scope, Synch ret) {
                    _psc.compensate(scope,ret);
                    instance(ACTIVE.this);
                }

                public void completed(FaultData faultData, Set<CompensationHandler> compensations) {
                    _comps.addAll(compensations);
                    if (!_stopped && _oalarm.getRepeatExpr() != null) {
                        Calendar next = Calendar.getInstance();
                        try {
                            getBpelRuntimeContext().getExpLangRuntime().evaluateAsDuration(_oalarm.getRepeatExpr(), getEvaluationContext()).addTo(next);
                        } catch (EvaluationException e) {
                            throw new InvalidProcessException(e);
                        } catch (FaultException e) {
                            __log.error("",e);
                            _psc.completed(createFault(e.getQName(),_oalarm.getForExpr()), _comps);
                            return;
                        }
                        instance(new WAIT(next));
                    } else {
                        if (faultData != null) {
                            //propagate completion into bounding scope only if we got fault during processing onAlarm
                            _psc.completed(faultData, _comps);
                        } else {
                            instance(new WAIT(null));
                        }
                    }
                }

                public void cancelled() { completed(null, CompensationHandler.emptySet()); }
                public void failure(String reason, Element data) { completed(null, CompensationHandler.emptySet()); }
            })).or(new ReceiveProcess() {
                private static final long serialVersionUID = -3873619538789039424L;
            }.setChannel(_cc).setReceiver(new EventHandlerControl() {
                public void stop() {
                    _stopped = true;
                    instance(ACTIVE.this);
                }
            })).or(new ReceiveProcess() {
                private static final long serialVersionUID = -4566956567870652885L;
            }.setChannel(_tc).setReceiver(new Termination() {
                public void terminate() {
                    replication(_activity.self).terminate();
                    _stopped = true;
                    instance(ACTIVE.this);
                }
            })));
        }
    }
}
