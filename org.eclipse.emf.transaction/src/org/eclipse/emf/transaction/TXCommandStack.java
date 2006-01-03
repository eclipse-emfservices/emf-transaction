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
 * $Id: TXCommandStack.java,v 1.1 2006/01/03 20:41:54 cdamus Exp $
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
public interface TXCommandStack
	extends CommandStack {

	/**
	 * Option to suppress the post-commit event upon completion of the
	 * transaction.  This does not suppress the pre-commit triggers.
	 * <p>
	 * The value is a {@link Boolean}; the default is {@link Boolean#FALSE}.
	 * </p>
	 */
	String OPTION_NO_NOTIFICATIONS = "silent"; //$NON-NLS-1$
	
	/**
	 * Option to suppress the pre-commit event that implements triggers.
	 * This does not suppress the post-commit event.
	 * <p>
	 * The value is a {@link Boolean}; the default is {@link Boolean#FALSE}.
	 * </p>
	 */
	String OPTION_NO_TRIGGERS = "no_triggers"; //$NON-NLS-1$
	
	/**
	 * Option to suppress validation.  Note that it does not suppress triggers,
	 * so a transaction could still roll back on commit if a pre-commit
	 * listener throws.
	 * <p>
	 * The value is a {@link Boolean}; the default is {@link Boolean#FALSE}.
	 * </p>
	 */
	String OPTION_NO_VALIDATION = "no_validation"; //$NON-NLS-1$
	
	/**
	 * Option to suppress undo/redo recording.  This has two effects:  it
	 * prevents rollback of the transaction, as this requires the undo
	 * information.  It also prevents undo/redo of any {@link RecordingCommand}s
	 * executed in the scope of this transaction.
	 * <p>
	 * The value is a {@link Boolean}; the default is {@link Boolean#FALSE}.
	 * </p>
	 */
	String OPTION_NO_UNDO = "no_undo"; //$NON-NLS-1$
	
	/**
	 * Option to enable a read/write transaction in the scope of a (nesting)
	 * read-only transaction.  Because this option deliberately violates the
	 * read-write exclusion mechanism for model integrity, this option also
	 * suppresses undo recording, triggers, and validation.  It does not
	 * suppress post-commit events.
	 * <p>
	 * The value is a {@link Boolean}; the default is {@link Boolean#FALSE}.
	 * </p>
	 */
	String OPTION_UNPROTECTED = "unprotected"; //$NON-NLS-1$
	
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
