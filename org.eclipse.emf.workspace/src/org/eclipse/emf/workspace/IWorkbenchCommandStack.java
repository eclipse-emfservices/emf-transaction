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
 * $Id: IWorkbenchCommandStack.java,v 1.1 2006/01/30 16:18:18 cdamus Exp $
 */
package org.eclipse.emf.workspace;

import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.command.CommandStack;
import org.eclipse.emf.transaction.TXCommandStack;


/**
 * A specialized transactional command stack that delegates the execution of
 * commands to an {@link IOperationHistory}.  This command stack supports
 * {@link CommandStack#execute(org.eclipse.emf.common.command.Command) execution}
 * of {@link Command}s, but supports the following APIs only in terms of the
 * default undo context of the command stack:
 * <ul>
 *   <li>{@link CommandStack#execute(Command)}</li>
 *   <li>{@link CommandStack#undo()}</li>
 *   <li>{@link CommandStack#redo()}</li>
 *   <li>{@link CommandStack#flush()}</li>
 *   <li>{@link CommandStack#getMostRecentCommand()}</li>
 *   <li>{@link CommandStack#getUndoCommand()}</li>
 *   <li>{@link CommandStack#getRedoCommand()}</li>
 * </ul>
 * All of the above operations map to the effective linear stack of operations
 * in the history that wrap commands and have the default undo context.  Thus,
 * they will only be consistent with the default command stack semantics if
 * all commands executed on this command stack's operation history use its
 * default undo context (which is guaranteed if all commands are executed via
 * this stack).
 * 
 * @author Christian W. Damus (cdamus)
 * 
 * @see #getDefaultUndoContext()
 */
public interface IWorkbenchCommandStack extends TXCommandStack {
	/**
	 * Obtains the operation history to which I delegate command execution.
	 * 
	 * @return my operation history
	 */
	IOperationHistory getOperationHistory();
	
	/**
	 * Obtains the default undo context to add to the undoable operations that
	 * I execute on my operation history to wrap {@link Command}s.  Moreover,
	 * undo, redo, and flush are context-specific operations
	 * in the operation history, so my undo/redo/flush commands use this context
	 * in delegating to the history.  Likewise, the determination of the
	 * most recent command and undo/redo commands depends on this context.
	 *  
	 * @return the default undo context for undo/redo/flush invocations
	 * 
	 * @see TXCommandStack#execute(Command, java.util.Map)
	 * @see CommandStack#undo()
	 * @see CommandStack#redo()
	 * @see CommandStack#flush()
	 * @see CommandStack#getMostRecentCommand()
	 * @see CommandStack#getUndoCommand()
	 * @see CommandStack#getRedoCommand()
	 */
	IUndoContext getDefaultUndoContext();
}
