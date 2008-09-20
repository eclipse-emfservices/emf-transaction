/**
 * <copyright>
 * 
 * Copyright (c) 2008 Zeligsoft Inc. and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *   Zeligsoft - Initial API and implementation
 * 
 * </copyright>
 *
 * $Id: TransactionalEditingDomainListenerImpl.java,v 1.1 2008/09/20 21:23:08 cdamus Exp $
 */

package org.eclipse.emf.transaction;

/**
 * A default implementation of the {@link TransactionalEditingDomainListener}
 * interface that does nothing in response to those call-backs, but which is
 * useful for subclassing to handle just the interesting events.
 * 
 * @author Christian W. Damus (cdamus)
 * 
 * @since 1.3
 */
public class TransactionalEditingDomainListenerImpl implements TransactionalEditingDomainListener {

	/**
	 * Initializes me.
	 */
	public TransactionalEditingDomainListenerImpl() {
		super();
	}

	public void editingDomainDisposing(TransactionalEditingDomainEvent event) {
		// nothing to do
	}

	public void transactionClosed(TransactionalEditingDomainEvent event) {
		// nothing to do
	}

	public void transactionClosing(TransactionalEditingDomainEvent event) {
		// nothing to do
	}

	public void transactionInterrupted(TransactionalEditingDomainEvent event) {
		// nothing to do
	}

	public void transactionStarted(TransactionalEditingDomainEvent event) {
		// nothing to do
	}

	public void transactionStarting(TransactionalEditingDomainEvent event) {
		// nothing to do
	}

}
