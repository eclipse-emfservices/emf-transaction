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
