/**
 * Copyright (c) 2008 Zeligsoft Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *   Zeligsoft - Initial API and implementation
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
