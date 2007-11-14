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
 * $Id: CommandChangeDescription.java,v 1.5 2007/11/14 18:14:00 cdamus Exp $
 */
package org.eclipse.emf.transaction.util;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.ecore.change.ChangeDescription;
import org.eclipse.emf.ecore.change.impl.ChangeDescriptionImpl;
import org.eclipse.emf.transaction.TransactionChangeDescription;

/**
 * A change description that simply wraps a {@link Command}, asking
 * it to undo or redo when {@link ChangeDescription#applyAndReverse() applying}.
 * <p>
 * <b>Note</b> that this class is not intended to be used by clients.  It is
 * only needed by service providers extending this API.
 * </p>
 * 
 * @author Christian W. Damus (cdamus)
 */
public class CommandChangeDescription
		extends ChangeDescriptionImpl
		implements TransactionChangeDescription {
	private boolean isRedone = true;
	private Command command;

	/**
	 * Initializes me with the command that I encapsulate.
	 * 
	 * @param command the command that I encapsulate
	 */
	public CommandChangeDescription(Command command) {
		this.command = command;
	}
	
	/**
	 * Chains a new command onto the command that I encapsulate, returning the
	 * chained result.
	 * 
	 * @param newCommand a command to chain onto my command
	 * 
	 * @return the new command chain
	 */
	public Command chain(Command newCommand) {
		command = command.chain(newCommand);
		return command;
	}
	
	/**
	 * I can apply if my wrapped command can undo or redo, according to whether
	 * it was last undone or redone.
	 * 
	 * @return <code>true</code> if my command can undo/redo;
	 *    <code>false</code>, otherwise
	 *    
	 * @see Command#canUndo()
	 * @see ConditionalRedoCommand#canRedo()
	 */
	public boolean canApply() {
		return (command != null)
				&& (isRedone? command.canUndo() : canRedo(command));
	}
	
	private boolean canRedo(Command cmd) {
		return !(cmd instanceof ConditionalRedoCommand)
				|| ((ConditionalRedoCommand) cmd).canRedo();
	}
	
	/**
	 * I apply my change by undoing the encapsulated operation.  After it is
	 * undone, I dispose myself.
	 */
	@Override
	public void apply() {
		try {
			command.undo();
		} finally {
			dispose();
		}
	}

	/**
	 * I apply-and-reverse by alternately undoing and redoing the encapsulated
	 * operation.
	 */
	@Override
	public void applyAndReverse() {
		if (isRedone) {
			command.undo();
			isRedone = false;
		} else {
			command.redo();
			isRedone = true;
		}
	}
	
	/**
	 * I can only assume that the operation I wrap makes some kind of change.
	 * 
	 * @return <code>false</code>, always
	 */
	public boolean isEmpty() {
		return false;
	}
	
	/**
	 * Forgets my operation and clears my reference to the adaptable.
	 */
	public void dispose() {
		if (command != null) {
			command.dispose();
			command = null;
		}
	}
}
