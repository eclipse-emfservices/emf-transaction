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
package org.eclipse.emf.transaction.impl;

import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.TransactionalEditingDomain.Lifecycle;

/**
 * <p>
 * Internal adapter interface that must be provided by a
 * {@link TransactionalEditingDomain} implementation that provides a
 * {@link Lifecycle} adapter. It is required by transactions, to send their
 * life-cycle notifications.
 * </p><p>
 * This interface is not intended to be implemented by clients, but by
 * editing domain providers.
 * </p>
 * 
 * @author Christian W. Damus (cdamus)
 * 
 * @see TransactionalEditingDomain.Lifecycle
 * 
 * @since 1.3
 */
public interface InternalLifecycle
		extends Lifecycle {

	/**
	 * Notifies me that a transaction has commenced its start sequence. There
	 * may be any number of steps implemented before requesting the editing
	 * domain to
	 * {@linkplain InternalTransactionalEditingDomain#activate(InternalTransaction)
	 * activate} it.
	 * 
	 * @param transaction
	 *            a transaction that is starting
	 */
	void transactionStarting(InternalTransaction transaction);

	/**
	 * Notifies me that a transaction has been interrupted in its start
	 * sequence. There may be any number of steps implemented by the transaction
	 * that can be interrupted.
	 * 
	 * @param transaction
	 *            a transaction thatwas interrupted while starting
	 */
	void transactionInterrupted(InternalTransaction transaction);

	/**
	 * Notifies me that a transaction has completed its start sequence. There
	 * may be any number of steps implemented after requesting the editing
	 * domain to
	 * {@linkplain InternalTransactionalEditingDomain#activate(InternalTransaction)
	 * activate} it.
	 * 
	 * @param transaction
	 *            a transaction that has started
	 */
	void transactionStarted(InternalTransaction transaction);

	/**
	 * Notifies me that a transaction has commenced its commit or rollback
	 * sequence. There may be any number of steps implemented before requesting
	 * the editing domain to
	 * {@linkplain InternalTransactionalEditingDomain#deactivate(InternalTransaction)
	 * deactivate} it.
	 * 
	 * @param transaction
	 *            a transaction that is closing
	 */
	void transactionClosing(InternalTransaction transaction);

	/**
	 * Notifies me that a transaction has completed its commit or rollback
	 * sequence. There may be any number of steps implemented after requesting
	 * the editing domain to
	 * {@linkplain InternalTransactionalEditingDomain#deactivate(InternalTransaction)
	 * deactivate} it.
	 * 
	 * @param transaction
	 *            a transaction that has closed
	 */
	void transactionClosed(InternalTransaction transaction);
}
