/**
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 */
package org.eclipse.emf.transaction.tests.fixtures;

import org.eclipse.emf.common.command.AbstractCommand;
import org.eclipse.emf.transaction.util.ConditionalRedoCommand;

/**
 * A simple abstract command requiring subclasses to implement only the
 * {@link org.eclipse.emf.common.command.Command#execute()} method.
 * Does not implement redo.
 *
 * @author Christian W. Damus (cdamus)
 */
public abstract class TestCommand
	extends AbstractCommand {

	@Override
	protected boolean prepare() {
		return true;
	}
	
	@Override
	public void undo() {
		// do nothing
	}
	
	public void redo() {
		// do nothing
	}
	
	public static abstract class Redoable
			extends TestCommand
			implements ConditionalRedoCommand {
		
		
		public boolean canRedo() {
			return true;
		}
	}
}
