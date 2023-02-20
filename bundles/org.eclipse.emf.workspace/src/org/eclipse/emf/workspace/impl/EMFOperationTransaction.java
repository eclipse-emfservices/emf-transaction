/**
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 */
package org.eclipse.emf.workspace.impl;

import java.util.Map;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.transaction.impl.EMFCommandTransaction;
import org.eclipse.emf.transaction.impl.InternalTransactionalEditingDomain;
import org.eclipse.emf.workspace.EMFCommandOperation;

/**
 * A specialized transaction implementation that knows the
 * {@link EMFCommandOperation} that it supports.  It is used to communicate
 * trigger information back to the command operation to support undo/redo.
 *
 * @author Christian W. Damus (cdamus)
 */
public class EMFOperationTransaction extends EMFCommandTransaction {
	private EMFCommandOperation operation;

	/**
	 * Initializes me with the command, my editing domain, and my options.
	 * 
	 * @param command a command that is being wrapped in an
	 *     {@link EMFCommandOperation}
	 * @param domain my editing domain
	 * @param options my options
	 */
	public EMFOperationTransaction(Command command,
			InternalTransactionalEditingDomain domain, Map<?, ?> options) {
		super(command, domain, options);
	}

	/**
	 * Obtains the operation that wraps my command.
	 * 
	 * @return my operation
	 */
	public EMFCommandOperation getOperation() {
		return operation;
	}
	
	/**
	 * Sets the operation that wraps my command.
	 * 
	 * @param operation my operation
	 */
	public void setOperation(EMFCommandOperation operation) {
		this.operation = operation;
	}
}
