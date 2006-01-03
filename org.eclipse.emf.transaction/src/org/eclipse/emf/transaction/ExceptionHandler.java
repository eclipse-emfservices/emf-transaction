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
 * $Id: ExceptionHandler.java,v 1.1 2006/01/03 20:41:55 cdamus Exp $
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
