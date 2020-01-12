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
package org.eclipse.emf.transaction.impl;

import java.util.Map;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.transaction.NotificationFilter;
import org.eclipse.emf.transaction.ResourceSetListener;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionalCommandStack;
import org.eclipse.emf.transaction.TransactionalEditingDomain;

/**
 * Internal interface that must be provided by any implementation of the public
 * {@link TransactionalEditingDomain} API, in order to function correctly in the transactional
 * editing domain framework.
 *
 * @author Christian W. Damus (cdamus)
 */
public interface InternalTransactionalEditingDomain extends TransactionalEditingDomain {	
	/**
	 * Creates and starts a new transaction.  The current thread is blocked
	 * until I grant it exclusive access to my resource set.
	 * 
	 * @param readOnly <code>true</code> if the transaction is intended only
	 *     to read the resource set; <code>false</code> if it will modify it
	 * @param options the options to apply to the transaction (as specified by
	 *     the {@link TransactionalCommandStack} interface
	 *     
	 * @return the newly started transaction
	 * 
	 * @throws InterruptedException if the current thread is interrupted while
	 *     waiting for the transaction to start
	 * @throws IllegalArgumentException if the current thread does not
	 *     {@link Transaction#getOwner() own} the transaction that it wants
	 *     to start or if it is attempting to start a transaction in an
	 *     inappropriate context
	 *     
	 * @see #activate(InternalTransaction)
	 */
	InternalTransaction startTransaction(boolean readOnly, Map<?, ?> options)
	throws InterruptedException;
	
	/**
	 * Obtains the transaction that currently has access to me, and whose
	 * thread of execution is active.
	 * 
	 * @return my active transaction, or <code>null</code> if no transaction
	 *     is currently active
	 */
	InternalTransaction getActiveTransaction();
	
	/**
	 * Activates (starts) the specified transaction.  The current thread is
	 * blocked until the transaction is activated, at which point it will be
	 * my {@linkplain #getActiveTransaction() active transaction} until it either
	 * yields (in the case of a read-only transaction) or closes.
	 * <p>
	 * Note that only the thread that owns a transaction may activate it.  Also,
	 * a nested read-write transaction cannot be activated if its parent
	 * transaction is read-only, unless the read-write transaction has the
	 * {@linkplain Transaction#OPTION_UNPROTECTED 'unprotected' option}.
	 * </p>
	 * 
	 * @param tx the transaction to activate
	 * 
	 * @throws InterruptedException if the current thread is interrupted while
	 *     waiting for me to activate its transaction
	 * @throws IllegalArgumentException if the current thread does not
	 *     {@linkplain Transaction#getOwner() own} the transaction that it wants
	 *     to activate or if it is attempting to activate a transaction in an
	 *     inappropriate context
	 * 
	 * @see #getActiveTransaction()
	 * @see TransactionalEditingDomain#yield()
	 * @see #startTransaction(boolean, Map)
	 * @see #deactivate(InternalTransaction)
	 */
	void activate(InternalTransaction tx) throws InterruptedException;
	
	/**
	 * Performs the pre-commit notifications and processing of trigger comamnds.
	 * This method must be called at the beginning of the
	 * {@linkplain Transaction#commit() commit} of a read/write transaction (not a
	 * read-only transaction), unless it has the
	 * {@linkplain Transaction#OPTION_NO_TRIGGERS 'no triggers' option}.
	 * 
	 * @param tx the transaction that is being committed
	 * 
	 * @throws RollbackException if any of the pre-commit listeners forces
	 *     rollback of the transaction.  The caller must honour this rollback
	 *     request by actually {@linkplain Transaction#rollback() rolling back}
	 *     the transaction
	 * 
	 * @see Transaction#commit()
	 * @see Transaction#rollback()
	 * @see ResourceSetListener#transactionAboutToCommit(org.eclipse.emf.transaction.ResourceSetChangeEvent)
	 */
	void precommit(InternalTransaction tx) throws RollbackException;
	
	/**
	 * Deactivates the specified transaction.  After this method completes, the
	 * transaction is no longer my
	 * {@link #getActiveTransaction() active transaction}.
	 * <p>
	 * The current thread must own the transaction that it is attempting to
	 * deactivate and this transaction must currently be my active transaction.
	 * </p>
	 * <p>
	 * <b>Note</b> that a transaction <em>must</em> ensure that this method is
	 * called when it closes, either by commit or by rollback, and at most once.
	 * </p>
	 *   
	 * @param tx the transaction to deactivate
	 * 
	 * @throws IllegalArgumentException if either the transaction is not the
	 *     active transaction, or the current thread does not own it
	 *     
	 * @see #activate(InternalTransaction)
	 * @see Transaction#commit()
	 * @see Transaction#rollback()
	 */
	void deactivate(InternalTransaction tx);
	
	/**
	 * Obtains the change recorder that I use to track changes in my resource
	 * set.  Transactions are expected to use this change recorder as follows:
	 * <ul>
	 *   <li>Start recording a fresh change description on 
	 *       {@linkplain InternalTransaction#start() starting} and
	 *       {@linkplain InternalTransaction#resume(org.eclipse.emf.transaction.TransactionChangeDescription) resuming}</li>
	 *   <li>End recording (storing the change description) on
	 *       {@linkplain Transaction#commit() committing} and
	 *       {@linkplain InternalTransaction#pause() pausing}</li>
	 *   <li>End recording (applying the change description) on
	 *       {@linkplain Transaction#rollback() rolling back}</li>
	 * </ul>
	 * 
	 * @return my change recorder
	 */
	TransactionChangeRecorder getChangeRecorder();
	
	/**
	 * Gets the validator that transactions should use to validate themselves
	 * upon committing.  A transaction must ask the validator to validate after
	 * performing the pre-commit phase (if needed), unless it has the
	 * {@linkplain Transaction#OPTION_NO_VALIDATION 'no validation' option}.
	 * 
	 * @return my transaction validator
	 */
	TransactionValidator getValidator();
	
	/**
	 * Broadcasts the specified notification to listeners as a singleton list,
	 * in a situation where batching is not possible because events are
	 * occurring outside of any transaction context.  This can only occur in
	 * the case of {@linkplain NotificationFilter#READ read notifications}.
	 * 
	 * @param notification the notification to send to resource set listeners
	 * 
	 * @see NotificationFilter#READ
	 * @see ResourceSetListener
	 * @see FilterManager#selectUnbatched(java.util.List, NotificationFilter)
	 */
	void broadcastUnbatched(Notification notification);

	/**
	 * Retrieves the undo/redo options that should be used when creating
	 *  transactions.
	 *  
	 * @return A map with undo/redo options.
	 */
	Map<Object, Object> getUndoRedoOptions();
	
	/**
	 * Transfers ownership of this editing domain to the specified
	 * privileged runnable.
	 *  
	 * @param runnable the runnable whose thread is to borrow me
	 */
	void startPrivileged(PrivilegedRunnable<?> runnable);
	
	/**
	 * Returns me to my previous owner, upon completion of the specified
	 * privileged runnable.
	 * 
	 * @param runnable the runnable whose thread had borrowed me
	 */
	void endPrivileged(PrivilegedRunnable<?> runnable);
}
