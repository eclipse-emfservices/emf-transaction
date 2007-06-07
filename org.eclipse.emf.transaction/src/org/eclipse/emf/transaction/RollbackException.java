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
 * $Id: RollbackException.java,v 1.3 2007/06/07 14:25:59 cdamus Exp $
 */
package org.eclipse.emf.transaction;

import org.eclipse.core.runtime.IStatus;

/**
 * Exception indicating that a transaction was automatically rolled back on
 * attempting to commit.  The usual cause of this exception is either a
 * validation failure or some run-time exception during the commit phase.
 * The {@link #getStatus() status} object provides details suitable for display
 * in a JFace error dialog or the like.
 * <p>
 * This class is intended to be instantiated by clients, particularly in the
 * {@link ResourceSetListener#transactionAboutToCommit(ResourceSetChangeEvent)}
 * method.
 * </p>
 * 
 * @author Christian W. Damus (cdamus)
 * 
 * @see Transaction#commit()
 * @see TransactionalCommandStack#execute(org.eclipse.emf.common.command.Command, java.util.Map)
 * @see ResourceSetListener#transactionAboutToCommit(ResourceSetChangeEvent)
 * @see ExceptionHandler
 */
public class RollbackException
	extends Exception {

	private static final long serialVersionUID = -1193949990049166426L;
	
	private final IStatus status;
	
	/**
	 * Initializes me with the status indicating the reason(s) for rolling
	 * back.
	 * 
	 * @param status the status.  Its severity should be {@link IStatus#ERROR}
	 *     or greater, otherwise the transaction should not have rolled back
	 */
	public RollbackException(IStatus status) {
		super(status.getMessage());
		
		this.status = status;
	}

	/**
	 * Obtains the status describing the cause of the transaction rollback.
	 * Its severity should be {@link IStatus#ERROR} or greater, otherwise the
	 * transaction should not have rolled back.
	 * 
	 * @return the status
	 */
	public final IStatus getStatus() {
		return status;
	}
}
