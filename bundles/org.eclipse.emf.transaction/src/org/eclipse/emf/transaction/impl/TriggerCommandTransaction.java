/**
 * Copyright (c) 2006, 2015 IBM Corporation, Christian W. Damus, and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *   Christian W. Damus - Bug 460206
 */
package org.eclipse.emf.transaction.impl;

import java.util.Map;

import org.eclipse.emf.transaction.util.CommandChangeDescription;
import org.eclipse.emf.transaction.util.TriggerCommand;

/**
 * A transaction implementation used by the command stack to wrap the execution
 * of {@link TriggerCommand}s, to provide them the write access that they need.
 * This transaction does not propagate its change description to the parent,
 * because that is handled separately via {@link CommandChangeDescription}s.
 *
 * @author Christian W. Damus (cdamus)
 */
public class TriggerCommandTransaction
	extends EMFCommandTransaction {

	/**
	 * Initializes me with my command, editing domain, and options.
	 * 
	 * @param command the trigger command that I am servicing
	 * @param domain the editing domain in which I am active
	 * @param options my options
	 */
	public TriggerCommandTransaction(TriggerCommand command,
			InternalTransactionalEditingDomain domain, Map<?, ?> options) {
		super(command, domain, options);
	}

	/**
	 * Extends the inherited implementation by first clearing my change
	 * description, so that I will not propagate these changes upwards.
	 */
	@Override
	protected synchronized void close() {
		change.detach();
		
		super.close();
	}
	
	/**
	 * Overrides the inherited implementation to simply propagate triggers to
	 * my parent, because it's the transaction that the outside world can see.
	 */
	@Override
	public void addTriggers(TriggerCommand triggers) {
		((InternalTransaction) getParent()).addTriggers(triggers);
	}
}
