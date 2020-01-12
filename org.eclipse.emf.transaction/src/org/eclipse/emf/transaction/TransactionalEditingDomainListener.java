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

import java.util.EventListener;

import org.eclipse.core.runtime.IStatus;

/**
 * <p>
 * A listener interface providing notifications of changes to a transactional
 * editing domain. This differs from the {@link ResourceSetListener} which
 * notifies of changes to the resource-set managed by the editing domain. The
 * events that occur in a transactional editing domain are:
 * </p>
 * <ul>
 * <li>pre-notification of the
 * {@linkplain #transactionStarting(TransactionalEditingDomainEvent) starting}
 * of a top-level transaction</li>
 * <li>{@linkplain #transactionStarted(TransactionalEditingDomainEvent)
 * successful start} of a top-level transaction</li>
 * <li>{@linkplain #transactionInterrupted(TransactionalEditingDomainEvent)
 * interrupted start} of a top-level transaction (thus a non-start)</li>
 * <li>pre-notification of the
 * {@linkplain #transactionClosing(TransactionalEditingDomainEvent) closing} of
 * a top-level transaction</li>
 * <li>{@linkplain #transactionClosed(TransactionalEditingDomainEvent)
 * successful or failed close} of a top-level transaction</li>
 * <li>{@linkplain #editingDomainDisposing(TransactionalEditingDomainEvent)
 * disposal} of the editing domain</li>
 * </ul>
 * <p>
 * The {@link TransactionalEditingDomainListenerImpl} class provides convenient
 * empty implementations of the listener methods, suitable for subclassing to
 * selectively implement these call-backs.
 * </p>
 * 
 * @author Christian W. Damus (cdamus)
 * 
 * @since 1.3
 * 
 * @see TransactionalEditingDomain.Lifecycle
 * @see TransactionalEditingDomainListenerImpl
 * 
 */
public interface TransactionalEditingDomainListener
		extends EventListener {

	/**
	 * <p>
	 * Signals that a transaction has requested activation, but is not yet
	 * started. Note that any number of transactions may be in this state
	 * simultaneously, on different threads, but obviously only one will
	 * actually start.
	 * </p>
	 * <p>
	 * This event is not sent for nested transactions, nor for a transaction's
	 * resumption from the {@linkplain TransactionalEditingDomain#yield()
	 * yielded} state.
	 * </p>
	 * 
	 * @param event
	 *            indicates the transaction that is about to start
	 */
	void transactionStarting(TransactionalEditingDomainEvent event);

	/**
	 * <p>
	 * Signals that a transaction that has requested activation was interrupted
	 * before it could start. Thus, this indicates a failed start.
	 * </p>
	 * <p>
	 * This event is not sent for nested transactions.
	 * </p>
	 * 
	 * @param event
	 *            indicates the transaction that was interrupted
	 */
	void transactionInterrupted(TransactionalEditingDomainEvent event);

	/**
	 * <p>
	 * Signals that a transaction has been activated.
	 * </p>
	 * <p>
	 * This event is not sent for nested transactions, nor for a transaction's
	 * resumption from the {@linkplain TransactionalEditingDomain#yield()
	 * yielded} state.
	 * </p>
	 * 
	 * @param event
	 *            indicates the transaction that has started
	 */
	void transactionStarted(TransactionalEditingDomainEvent event);

	/**
	 * <p>
	 * Signals that a transaction has finished its work and is about to close.
	 * This may be the beginning of the normal commit sequence of trigger firing
	 * followed by validation (subsequently rolling back, if necessary), or an
	 * explicit roll-back requested by the transaction, itself.
	 * </p>
	 * <p>
	 * This event is not sent for nested transactions, nor for a transaction's
	 * entry into the {@linkplain TransactionalEditingDomain#yield() yielded}
	 * state.
	 * </p>
	 * 
	 * @param event
	 *            indicates the transaction that is about to close
	 */
	void transactionClosing(TransactionalEditingDomainEvent event);

	/**
	 * <p>
	 * Signals that a transaction has closed, either with a successful commit or
	 * with a roll-back. The {@linkplain Transaction#getStatus() status} of the
	 * transaction will indicate which has occurred; an
	 * {@linkplain IStatus#ERROR} indicates roll-back. This event is sent after
	 * all post-commit notifications have gone out to
	 * {@link ResourceSetListener}s.
	 * </p>
	 * <p>
	 * This event is not sent for nested transactions.
	 * </p>
	 * 
	 * @param event
	 *            indicates the transaction that has closed
	 */
	void transactionClosed(TransactionalEditingDomainEvent event);

	/**
	 * <p>
	 * Signals that the transactional editing domain to which the lister is
	 * attached is to be disposed. Disposal result, among other things, in the
	 * removal of the listener from it.
	 * </p>
	 * 
	 * @param event
	 *            indicates the editing domain that is being disposed. The event
	 *            has no
	 *            {@linkplain TransactionalEditingDomainEvent#getTransaction()
	 *            transaction}
	 */
	void editingDomainDisposing(TransactionalEditingDomainEvent event);
}
