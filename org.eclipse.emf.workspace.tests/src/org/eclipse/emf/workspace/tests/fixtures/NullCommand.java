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
 * $Id: NullCommand.java,v 1.1 2006/01/30 16:26:01 cdamus Exp $
 */
package org.eclipse.emf.workspace.tests.fixtures;

import org.eclipse.emf.common.command.AbstractCommand;

/**
 * A command that does nothing.
 *
 * @author Christian W. Damus (cdamus)
 */
public class NullCommand extends AbstractCommand {

	protected boolean prepare() {
		return true;
	}
	
	/** Does nothing. */
	public void execute() {
		// nothing to do
	}
	
	/** Does nothing. */
	public void undo() {
		// nothing to do
	}

	/** Does nothing. */
	public void redo() {
		// nothing to do
	}
}
