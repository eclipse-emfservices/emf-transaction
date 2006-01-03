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
 * $Id: TestCommand.java,v 1.1 2006/01/03 20:51:13 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests.fixtures;

import org.eclipse.emf.common.command.AbstractCommand;

/**
 * A simple abstract command requiring subclasses to implement only the
 * {@link org.eclipse.emf.common.command.Command#execute()} method.
 * Does not implement redo.
 *
 * @author Christian W. Damus (cdamus)
 */
public abstract class TestCommand
	extends AbstractCommand {

	protected boolean prepare() {
		return true;
	}
	
	public void redo() {
		// do nothing
	}
}
