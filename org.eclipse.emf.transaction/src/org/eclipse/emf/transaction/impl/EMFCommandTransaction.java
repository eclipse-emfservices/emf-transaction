/**
 * <copyright>
 *
 * Copyright (c) 2005, 2008 IBM Corporation, Zeligsoft Inc. and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *   Zeligsoft - Bugs 218276, 245446
 *
 * </copyright>
 *
 * $Id: EMFCommandTransaction.java,v 1.6 2008/11/30 16:38:08 cdamus Exp $
 */
package org.eclipse.emf.transaction.impl;

import java.util.Collections;
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
	public EMFCommandTransaction(Command command, InternalTransactionalEditingDomain domain,
			Map<?, ?> options) {
		super(domain, false, addCommand(options, command));
		
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
	
	/**
	 * Given the specified options and command, computes an options map that
	 * has the same options as those provided, plus the specified command as
	 * the executing-command option.
	 * 
	 * @param options a map of options
	 * @param command the command that we are executing in this transaction
	 * @return a new map of options that increments those supplied with the
	 *       appropriate executing-command option
	 */
	private static Map<?, ?> addCommand(Map<?, ?> options, Command command) {
		Map<?, ?> result = options;

		if (options == null) {
			result = Collections
				.singletonMap(OPTION_EXECUTING_COMMAND, command);
		} else if (options.get(OPTION_EXECUTING_COMMAND) != command) {
			Map<Object, Object> mutable = new java.util.HashMap<Object, Object>(
				options);
			mutable.put(OPTION_EXECUTING_COMMAND, command);
			result = mutable;
		}

		return result;
	}
}
