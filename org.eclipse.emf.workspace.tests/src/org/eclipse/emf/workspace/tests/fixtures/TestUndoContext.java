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
 * $Id: TestUndoContext.java,v 1.2 2007/06/07 14:26:02 cdamus Exp $
 */
package org.eclipse.emf.workspace.tests.fixtures;

import org.eclipse.core.commands.operations.IUndoContext;

/**
 * An undo context implementation for testing purposes.
 *
 * @author Christian W. Damus (cdamus)
 */
public class TestUndoContext implements IUndoContext {

	/**
	 * Initializes me.
	 */
	public TestUndoContext() {
		super();
	}

	public String getLabel() {
		return "Testing"; //$NON-NLS-1$
	}

	public boolean matches(IUndoContext context) {
		return context == this;
	}

}
