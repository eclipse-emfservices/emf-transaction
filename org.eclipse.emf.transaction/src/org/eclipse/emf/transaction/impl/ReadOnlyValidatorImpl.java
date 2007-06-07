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
 * $Id: ReadOnlyValidatorImpl.java,v 1.2 2007/06/07 14:25:59 cdamus Exp $
 */
package org.eclipse.emf.transaction.impl;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

/**
 * A validator for read-only transactions.  It provides all of the notifications
 * (in order) that occurred during the transaction, but does not validate them
 * (validation always passes with no problems).
 * <p>
 * A read-only validator should be created for the root transaction of any
 * nested read-only transaction structure, when the root transaction is
 * activated.  As child transactions are activated, they must be
 * {@link #add(InternalTransaction) added} to me so that I may correctly track
 * which notifications were received during which transaction, and at which
 * time relative to the start and completion of nested transactions.
 * </p>
 *
 * @author Christian W. Damus (cdamus)
 * 
 * @see ReadWriteValidatorImpl
 */
public class ReadOnlyValidatorImpl extends ReadWriteValidatorImpl {
	/**
	 * Initializes me.
	 */
	public ReadOnlyValidatorImpl() {
		super();
	}
	
	/**
	 * I always return an OK status because there is never anything to validate
	 * in a read-only transaction.
	 * 
	 * @return an OK status, always
	 */
	public IStatus validate() {
		return Status.OK_STATUS;
	}
}
