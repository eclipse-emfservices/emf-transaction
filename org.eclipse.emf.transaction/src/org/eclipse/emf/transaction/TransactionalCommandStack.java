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
 * $Id: TransactionalCommandStack.java,v 1.1 2006/01/30 19:47:54 cdamus Exp $
 */
package org.eclipse.emf.transaction;

import java.util.Map;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.command.CommandStack;

/**
 * Extension of the basic {@link CommandStack} API providing additional control
 * over (and feed-back from) the transactions used to execute commands.
 *
 * @author Christian W. Damus (cdamus)
 */
public interface TransactionalCommandStack
	extends CommandStack {
	
	/**
	 * Executes the specified command in a read/write transaction.
	 * This method is preferred over the inherited
	 * {@link CommandStack#execute(Command)} method because it provides
	 * feed-back when a command fails to complete.  The implementation of this
	 * latter method simply suppresses (but logs) any checked exception that
	 * occurs.
	 * <p>
	 * <b>Note</b> that this method will block the caller until a read/write
	 * transaction can be started (if other transactions are currently active).
	 * </p>
	 * 
	 * @param command the command to execute
	 * @param options the options to apply to the command's transaction, or
	 *    <code>null</code> to select the defaults
	 *    
	 * @throws InterruptedException if the current thread is interrupted while
	 *    waiting to start a read/write transaction for the command execution
	 * @throws RollbackException if the changes performed by the command are
	 *    rolled back by validation of the transaction
	 */
	void execute(Command command, Map options) throws InterruptedException, RollbackException;

	/**
	 * Sets an exception handler.  This object will be notified when exceptions
	 * occur, but is not really expected to be able to do anything about them.
	 * Its intended purpose is to support an user feed-back mechanism
	 * appropriate to the environment.
	 * 
	 * @param handler the exception handler to set
	 */
	void setExceptionHandler(ExceptionHandler handler);
	
	/**
	 * Obtains my exception handler.
	 * 
	 * @return my exception handler, or <code>null</code> if none
	 * 
	 * @see #setExceptionHandler(ExceptionHandler)
	 */
	ExceptionHandler getExceptionHandler();
}
