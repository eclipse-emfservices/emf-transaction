/**
 * <copyright>
 *
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *
 * </copyright>
 *
 * $Id: EMFOperationTransaction.java,v 1.1 2006/01/30 16:18:19 cdamus Exp $
 */
package org.eclipse.emf.workspace.impl;

import java.util.Map;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.transaction.impl.EMFCommandTransaction;
import org.eclipse.emf.transaction.impl.InternalTXEditingDomain;
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
	public EMFOperationTransaction(Command command, InternalTXEditingDomain domain, Map options) {
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
