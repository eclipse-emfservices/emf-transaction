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
package org.eclipse.emf.transaction;

/**
 * An interface for client objects that handle exceptions occurring in the
 * interaction with an editing domain's command stack.  This is useful, for
 * example, for UIs that want to show a dialog when a command's transaction
 * is rolled back by validation problems. 
 *
 * @author Christian W. Damus (cdamus)
 * 
 * @see RollbackException
 */
public interface ExceptionHandler {
	/**
	 * Handles the specified exception in some way.
	 * 
	 * @param e the exception that occurred.  The {@link RollbackException}
	 *     is the most interesting exception type that is likely to occur
	 */
	void handleException(Exception e);
}
