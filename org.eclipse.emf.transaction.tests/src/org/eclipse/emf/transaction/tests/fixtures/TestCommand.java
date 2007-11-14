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
 * $Id: TestCommand.java,v 1.4 2007/11/14 18:14:13 cdamus Exp $
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
