/**
 * <copyright>
 *
 * Copyright (c) 2006 IBM Corporation and others.
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
 * $Id: ConditionalRedoCommand.java,v 1.1 2006/04/26 13:13:39 cdamus Exp $
 */
package org.eclipse.emf.transaction.util;

import java.util.Iterator;
import java.util.List;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.command.CompoundCommand;

/**
 * A specialization of the EMF {@link Command} API that accounts for conditional
 * redoable-ness.  This interface adds a {@link #canRedo()} operation in
 * conjunction with the {@link Command#canUndo()}.
 *
 * @author Christian W. Damus (cdamus)
 */
public interface ConditionalRedoCommand extends Command {
	  /**
	   * Queries whether I can be redone.  The result of calling this
	   * operation is undefined until I have been {@link Command#undo() undone}.
	   * Note that it is acceptable for a conditionally redoable command not
	   * to be redoable if it has successfully been undone, or even after
	   * having been successfully redone at least once before.
	   * 
	   * @return <code>true</code> if I can be redone; <code>false</code>,
	   *    otherwise
	   */
	  boolean canRedo();

	  /**
	   * A conditionally redoable compound command.
	   *
	   * @author Christian W. Damus (cdamus)
	   */
	  class Compound
	  		extends CompoundCommand
	  		implements ConditionalRedoCommand {
		  
		public Compound() {
			super();
		}

		public Compound(int resultIndex, List commandList) {
			super(resultIndex, commandList);
		}

		public Compound(int resultIndex, String label, List commandList) {
			super(resultIndex, label, commandList);
		}

		public Compound(int resultIndex, String label, String description,
				List commandList) {
			super(resultIndex, label, description, commandList);
		}

		public Compound(int resultIndex, String label, String description) {
			super(resultIndex, label, description);
		}

		public Compound(int resultIndex, String label) {
			super(resultIndex, label);
		}

		public Compound(int resultIndex) {
			super(resultIndex);
		}

		public Compound(List commandList) {
			super(commandList);
		}

		public Compound(String label, List commandList) {
			super(label, commandList);
		}

		public Compound(String label, String description, List commandList) {
			super(label, description, commandList);
		}

		public Compound(String label, String description) {
			super(label, description);
		}

		public Compound(String label) {
			super(label);
		}

		/**
		 * I can redo if none of my composed commands cannot redo.
		 * 
		 * @return <code>false</code> if any command that is a
		 *     {@link ConditionalRedoCommand} cannot redo;
		 *     <code>true</code>, otherwise
		 */
		public boolean canRedo() {
			for (Iterator iter = commandList.iterator(); iter.hasNext();) {
				Object next = iter.next();

				if ((next instanceof ConditionalRedoCommand)
						&& !((ConditionalRedoCommand) next).canRedo()) {
					return false;
				}
			}

			return true;
		}
		
		/**
		 * I am self-chaining.
		 */
		public Command chain(Command c) {
			append(c);
			return this;
		}
	}
}
