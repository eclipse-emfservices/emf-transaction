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
 * $Id: InternalTransaction.java,v 1.2 2006/01/30 19:47:54 cdamus Exp $
 */
package org.eclipse.emf.transaction.impl;

import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.TransactionChangeDescription;
import org.eclipse.emf.transaction.Transaction;

/**
 * An internal interface that must be provided by any implementation of the
 * public {@link Transaction} interface, in order to function correctly in
 * the transactional editing domain framework.
 *
 * @author Christian W. Damus (cdamus)
 */
public interface InternalTransaction
	extends Transaction {

	/**
	 * Obtains the root transaction (the one that has no parent).  This could
	 * be me if I am the root.
	 * 
	 * @return the root transaction in a nested transaction structure
	 */
	InternalTransaction getRoot();
	
	/**
	 * Assigns my parent transaction (the one in which I am nested).  This
	 * must be done by the editing domain immediately upon activating me.
	 * 
	 * @param parent my parent transaction
	 */
	void setParent(InternalTransaction parent);
	
	/**
	 * Starts me.  Usually, this will delegate to the editing domain
	 * to {@link InternalTransactionalEditingDomain#activate(InternalTransaction) activate}
	 * me.
	 * <p>
	 * <b>Note</b> that this call should block the current thread until the
	 * editing domain grants exclusive access.
	 * </p>
	 * 
	 * @throws InterruptedException if the current thread is interrupted while
	 *     waiting for activation
	 */
	void start() throws InterruptedException;
	
	/**
	 * Aborts the transaction with a reason given by the specified status.  This is
	 * used, for example, when a transaction is corrupted by another thread
	 * concurrently writing the model (obviously without an active transaction!).
	 * The transaction is expected to rollback when it attempts to commit, and to
	 * propagate this status up to the root transaction.
	 * 
	 * @param status a status object providing the reason.  It should be the status
	 *     attached to the eventual {@link RollbackException}, and should be
	 *     set as my {@link #setStatus(IStatus) status}
	 */
	void abort(IStatus status);
	
	/**
	 * Adds the specified notification to the list of notifications received
	 * during this transaction.
	 * 
	 * @param notification the notification to add
	 * 
	 * @see #getNotifications()
	 */
	void add(Notification notification);
	
	/**
	 * Obtains the list of notifications that were received during execution
	 * of this transaction.  These are the notifications that later will
	 * be sent to pre-commit listeners, validation, and eventually to
	 * post-commit listeners (if I successfully commit).
	 * 
	 * @return my notifications
	 * 
	 * @see #add(Notification)
	 */
	List getNotifications();
	
	/**
	 * Pauses me while a child transaction is active, so that I do not collect
	 * either notifications or recorded changes during that time.
	 */
	void pause();
	
	/**
	 * Resumes me after completion of a child transaction.  If the child
	 * committed, then I add its change description to my changes.
	 * 
	 * @param nestedChanges the nested transaction's recorded changes, or
	 *     <code>null</code> if it rolled back (in which case, I do not add
	 *     anything to my changes)
	 */
	void resume(TransactionChangeDescription nestedChanges);
	
	/**
	 * Sets the status of the transaction.
	 * 
	 * @param status my status
	 */
	void setStatus(IStatus status);
	
	/**
	 * Queries whether this transaction or any of its ancestors is in the
	 * process of rolling back.
	 * 
	 * @return <code>true</code> if I or my
	 *   {@link Transaction#getParent() parent} (if any) am rolling back;
	 *   <code>false</code> otherwise
	 */
	boolean isRollingBack();
}
