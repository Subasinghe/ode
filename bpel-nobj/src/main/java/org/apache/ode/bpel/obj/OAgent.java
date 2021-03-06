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
package org.apache.ode.bpel.obj;

import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashSet;
import java.util.Set;

/**
 * Base class for active BPEL agents.
 */
public class OAgent extends OBase  implements Serializable{
	public static final long serialVersionUID = -1L;

	/** Links entering this agent. */
	private static final String INCOMINGLINKS = "incomingLinks";

	/** Links exiting this agent. */
	private static final String OUTGOINGLINKS = "outgoingLinks";

	/** Variables read from. */
	private static final String VARIABLERD = "variableRd";

	/** Variables written to. */
	private static final String VARIABLEWR = "variableWr";

	/** The children of this agent. */
	private static final String NESTED = "nested";

	@JsonCreator
	public OAgent(){
	}
	public OAgent(OProcess owner) {
		super(owner);
		setIncomingLinks(new HashSet<OLink>());
		setOutgoingLinks(new HashSet<OLink>());
		setVariableRd(new HashSet<OScope.Variable>());
		setVariableWr(new HashSet<OScope.Variable>());
		setNested(new HashSet<OAgent>());
	}

	@SuppressWarnings("unchecked")
	@JsonIgnore
	public Set<OLink> getIncomingLinks() {
		Object o = fieldContainer.get(INCOMINGLINKS);
		return o == null ? null : (Set<OLink>)o;
	}

	@SuppressWarnings("unchecked")
	@JsonIgnore
	public Set<OAgent> getNested() {
		Object o = fieldContainer.get(NESTED);
		return o == null ? null : (Set<OAgent>)o;
	}

	@SuppressWarnings("unchecked")
	@JsonIgnore
	public Set<OLink> getOutgoingLinks() {
		Object o = fieldContainer.get(OUTGOINGLINKS);
		return o == null ? null : (Set<OLink>)o;
	}

	@SuppressWarnings("unchecked")
	@JsonIgnore
	public Set<OScope.Variable> getVariableRd() {
		Object o = fieldContainer.get(VARIABLERD);
		return o == null ? null : (Set<OScope.Variable>)o;
	}

	@SuppressWarnings("unchecked")
	@JsonIgnore
	public Set<OScope.Variable> getVariableWr() {
		Object o = fieldContainer.get(VARIABLEWR);
		return o == null ? null : (Set<OScope.Variable>)o;
	}

	public void setIncomingLinks(Set<OLink> incomingLinks) {
		if (getIncomingLinks() == null){
			fieldContainer.put(INCOMINGLINKS, incomingLinks);
		}
	}

	public void setNested(Set<OAgent> nested) {
		if (getNested() == null){
			fieldContainer.put(NESTED, nested);
		}
	}

	public void setOutgoingLinks(Set<OLink> outgoingLinks) {
		if (getOutgoingLinks() == null){
			fieldContainer.put(OUTGOINGLINKS, outgoingLinks);
		}
	}

	public void setVariableRd(Set<OScope.Variable> variableRd) {
		if (getVariableRd() == null){
			fieldContainer.put(VARIABLERD, variableRd);
		}
	}

	public void setVariableWr(Set<OScope.Variable> variableWr) {
		if (getVariableWr() == null){
			fieldContainer.put(VARIABLEWR, variableWr);
		}
	}
}
