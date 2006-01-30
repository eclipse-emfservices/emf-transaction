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
 * $Id: InternalTransactionalCommandStack.java,v 1.1 2006/01/30 19:47:54 cdamus Exp $
 */
package org.eclipse.emf.transaction.impl;

import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.transaction.ResourceSetListener;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.TransactionalCommandStack;

/**
 * An internal interface that must be provided by any implementation of the public
 * {@link TransactionalCommandStack} interface, in order to function correctly in the
 * transactional editing domain framework.
 *
 * @author Christian W. Damus (cdamus)
 */
public interface InternalTransactionalCommandStack extends TransactionalCommandStack {
	/**
	 * Obtains the editing domain in which I create transactions.
	 * 
	 * @return my editing domain
	 * 
	 * @see #createTransaction(Command, Map)
	 */
	InternalTransactionalEditingDomain getDomain();
	
	/**
	 * Assigns the editing domain in which I create transactions.
	 * 
	 * @param domain my editing domain
	 * 
	 * @see #createTransaction(Command, Map)
	 */
	void setEditingDomain(InternalTransactionalEditingDomain domain);
	
	/**
	 * Creates a read/write transaction in my editing domain for the purpose
	 * of executing the specified command.  The resulting transaction is
	 * expected to be started when it is returned (hence the possibility of
	 * interruption).
	 * 
	 * @param command a command that I need to execute
	 * @param options the options to apply to the resulting transaction
	 * @return the command transaction
	 * 
	 * @throws InterruptedException if the current thread is interrupted while
	 *     waiting for the transaction to start
	 *     
	 * @see #getDomain()
	 */
	EMFCommandTransaction createTransaction(Command command, Map options) throws InterruptedException;
	
	/**
	 * Executes the specified list of trigger commands.  All of the commands are
	 * executed within a single child transaction of the transaction that executed
	 * the triggering <code>command</code>; they must not be "piggy-backed" on
	 * the currently active transaction.
	 * 
	 * @param command the command whose execution triggered additional commands
	 *     (from pre-commit listeners)
	 * @param triggers a list of zero or more {@link Command}s to execute.
	 *     If there are none, then no transaction needs to be started
	 * @param options the options to apply to the child transaction
	 * 
	 * @throws InterruptedException if the current thread is interrupted while
	 *     waiting for the trigger transaction to start
	 * @throws RollbackException if the trigger transaction rolls back on commit
	 * 
	 * @see ResourceSetListener#transactionAboutToCommit(org.eclipse.emf.transaction.ResourceSetChangeEvent)
	 * @see #createTransaction(Command, Map)
	 */
	void executeTriggers(Command command, List triggers, Map options) throws InterruptedException, RollbackException;
	
	/**
	 * Disposes of my state and any additional resources that I may be
	 * retaining.  I am only disposed when my {@link #getDomain() editing domain}
	 * is disposed.
	 */
	void dispose();
}
