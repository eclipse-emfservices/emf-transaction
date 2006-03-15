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
 * $Id: TriggerCommand.java,v 1.2 2006/03/15 01:40:31 cdamus Exp $
 */
package org.eclipse.emf.transaction.util;

import java.util.List;
import java.util.ListIterator;

import org.eclipse.emf.common.CommonPlugin;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.command.CompoundCommand;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.transaction.ResourceSetListener;
import org.eclipse.emf.transaction.internal.EMFTransactionPlugin;
import org.eclipse.emf.transaction.internal.Tracing;

/**
 * A specialized compound command that combines a "triggering" command with commands
 * contributed by {@link ResourceSetListener}s as triggers.  It takes care of
 * the distinction between the triggering command and the others when executing,
 * undoing, and redoing.
 *
 * @author Christian W. Damus (cdamus)
 */
public class TriggerCommand extends CompoundCommand {
	private final Command triggeringCommand;
	private final List triggers;
	
	/**
	 * Initializes me with a list of commands triggered not by the execution of
	 * a command but by direct manipulation of the model during a read/write
	 * transaction.  It is assumed, then, that the trigger command will not
	 * actually be used with a command stack where its label and description
	 * would matter for undo/redo menus.
	 * 
	 * @param triggers the trigger commands that I encapsulate
	 */
	public TriggerCommand(List triggers) {
		super(0, "Triggers", "Triggered Changes", triggers); //$NON-NLS-1$ //$NON-NLS-2$
		
		this.triggeringCommand = null;
		this.triggers = triggers;
	}
	
	/**
	 * Initializes me with a list of commands triggered by the execution of
	 * some command on the command stack.  My label and description are the same
	 * as the triggering command's
	 * 
	 * @param triggeringCommand the command that triggered further commands
	 * @param triggers the trigger commands that I encapsulate
	 */
	public TriggerCommand(Command triggeringCommand, List triggers) {
		super(0, triggeringCommand.getLabel(), triggeringCommand.getDescription(), triggers);
		
		this.triggeringCommand = triggeringCommand;
		this.triggers = triggers;
	}
	
	/**
	 * Retrieves the command that triggered the trigger commands.
	 * 
	 * @return the triggering command, or <code>null</code> if the triggers
	 *     were not instigated by the execution of a command
	 */
	public final Command getTriggeringCommand() {
		return triggeringCommand;
	}
	
	/**
	 * Retrieves my trigger commands (not including the triggering command,
	 * if any).
	 * 
	 * @return my triggers, as a list of {@link Command}s.  Will not be empty
	 */
	public final List getTriggers() {
		return triggers;
	}
	
	// Documentation copied from the inherited specification
	protected boolean prepare() {
		// we will check canExecute() as we go
		return !triggers.isEmpty();
	}
	
	/**
	 * Executes all of my trigger commands, then prepends the original triggering
	 * command (if any) so that it will be undone/redone with the others.
	 */
	public void execute() {
		// execute just the triggers
	    for (ListIterator iter = commandList.listIterator(); iter.hasNext();) {
			try {
				Command command = (Command) iter.next();
				
				if (command.canExecute()) {
					command.execute();
				} else {
					// the trigger is no longer valid.  Don't attempt to undo
					//    or redo it later
					iter.remove();
				}
			} catch (RuntimeException e) {
				Tracing.catching(TriggerCommand.class, "execute", e); //$NON-NLS-1$
				
				// Skip over the command that threw the exception.
				//
				iter.previous();

				try {
					// Iterate back over the executed commands to undo them.
					//
					while (iter.hasPrevious()) {
						Command command = (Command) iter.previous();
						if (command.canUndo()) {
							command.undo();
						} else {
							break;
						}
					}
				} catch (RuntimeException nested) {
					Tracing.catching(TriggerCommand.class, "execute", nested); //$NON-NLS-1$
					EMFTransactionPlugin.INSTANCE.log(new WrappedException(
						CommonPlugin.INSTANCE
							.getString("_UI_IgnoreException_exception"), //$NON-NLS-1$
						nested).fillInStackTrace());
				}

				Tracing.throwing(TriggerCommand.class, "execute", e); //$NON-NLS-1$
				throw e;
			}
		}
		
		if (triggeringCommand != null) {
			// then replace the command-list with a new list that includes
			// the
			// originally executed command (for undo/redo)
			commandList = new java.util.ArrayList(triggers.size() + 1);
			commandList.add(triggeringCommand);
			commandList.addAll(triggers);
		}
	}
}