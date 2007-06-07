/**
 * <copyright>
 *
 * Copyright (c) 2005, 2007 IBM Corporation and others.
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
 * $Id: EMFCommandTransaction.java,v 1.3 2007/06/07 14:25:59 cdamus Exp $
 */
package org.eclipse.emf.transaction.impl;

import java.util.Map;

import org.eclipse.emf.common.command.Command;

/**
 * A transaction implementation use by the command stack to wrap the execution
 * of {@link Command}s, to provide them the write access that they need.
 * The transaction knows the {@link #getCommand() command} that it is
 * servicing.
 *
 * @author Christian W. Damus (cdamus)
 */
public class EMFCommandTransaction
	extends TransactionImpl {

	private final Command command;

	/**
	 * Initializes me with my command, editing domain, and options.
	 * 
	 * @param command the command that I am servicing
	 * @param domain the editing domain in which I am active
	 * @param options my options
	 */
	public EMFCommandTransaction(Command command, InternalTransactionalEditingDomain domain, Map options) {
		super(domain, false, options);
		
		this.command = command;
	}

	/**
	 * Obtains the command for which I provide read/write access to the
	 * editing domain.
	 * 
	 * @return my command
	 */
	public final Command getCommand() {
		return command;
	}
}
