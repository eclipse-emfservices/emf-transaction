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
package org.eclipse.emf.workspace.tests.fixtures;

import org.eclipse.emf.common.command.AbstractCommand;

/**
 * A command that does nothing.
 *
 * @author Christian W. Damus (cdamus)
 */
public class NullCommand extends AbstractCommand {

	@Override
	protected boolean prepare() {
		return true;
	}
	
	/** Does nothing. */
	public void execute() {
		// nothing to do
	}
	
	/** Does nothing. */
	@Override
	public void undo() {
		// nothing to do
	}

	/** Does nothing. */
	public void redo() {
		// nothing to do
	}
}
