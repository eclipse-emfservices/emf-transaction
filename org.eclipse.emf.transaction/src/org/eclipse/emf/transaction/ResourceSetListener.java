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
 * $Id: ResourceSetListener.java,v 1.1 2006/01/03 20:41:55 cdamus Exp $
 */
package org.eclipse.emf.transaction;

import java.util.EventListener;

import org.eclipse.emf.common.command.Command;

/**
 * Listener interface for batched notification of changes to a resource set.
 * Unlike EMF {@link org.eclispse.emf.common.notify.Adapter}s, resource-set
 * listeners receive notifications at the close of a transaction and, in the
 * case of the {@link #resourceSetChanged post-commit} call-back, only in
 * the case that the transaction committed (did not roll back).
 * <p>
 * This interface is intended to be implemented by clients.
 * For convenience, clients can extend the {@link ResourceSetListenerImpl}
 * class if they need no other superclass.
 * </p>
 *
 * @author Christian W. Damus (cdamus)
 * 
 * @see ResourceSetListenerImpl
 * @see ResourceSetChangeEvent
 * @see NotificationFilter
 * @see TXEditingDomain#addResourceSetListener(ResourceSetListener)
 */
public interface ResourceSetListener extends EventListener {
	/**
	 * Provides a filter to select which notifications should be sent to this
	 * listener.  If none is provided, the default is the
	 * {@link NotificationFilter#NOT_TOUCH} filter.
	 * <p>
	 * <b>Note</b> that, if a listener's filter does not match any of the
	 * notifications that were received during a transaction, then it is not
	 * invoked at all.  Thus, the notification lists received in the
	 * {@link ResourceSetChangeEvent}s will never be empty.
	 * </p>
	 * 
	 * @return the filter used to select notifications, or <code>null</code> to
	 *     obtain the default
	 */
	NotificationFilter getFilter();
	
	/**
	 * Informs the listener that the execution of a command (which may be
	 * implicit) is about to complete.  More precisely, that a read/write
	 * transaction is about to commit.
	 * <p>
	 * Listeners implement this method in order to provide "trigger commands",
	 * akin to database triggers in RDBMSes.  To follow-up changes that
	 * occurred in the model, to proactively maintain model integrity, the
	 * listener can return a command that makes additional changes.
	 * </p>
	 * <p>
	 * Trigger commands are executed after all listeners have been invoked,
	 * in the same transaction context as the original events (and therefore
	 * validated together with them).  Moreover, because these triggers perform
	 * model changes, they cause another round of invocation of these very same
	 * resource set listeners.
	 * </p>
	 * <p>
	 * The command returned by a trigger, if any, should be careful to implement
	 * its {@link Command#canExecute()} method appropriately.  In particular,
	 * it is important to check that the conditions observed by the listener
	 * still apply, as other trigger commands may be executed before it that can
	 * make inconsistent changes.
	 * </p>
	 * <p>
	 * Finally, a pre-commit listener has the opportunity to force the current
	 * transaction to roll back instead of completing the commit.  This helps
	 * in the implementation of certain kinds of live validation checks that
	 * cannot be implemented using the EMF validation framework.
	 * </p>
	 * <p>
	 * <b>Note</b> that the listener is invoked in a read-only transaction
	 * context.  It is safe to read the model, but direct changes are not
	 * permitted (return a command instead).
	 * </p>
	 * 
	 * @param event the event object describing the changes that occurred in
	 *    the resource set
	 *    
	 * @return an optional command to perform additional changes.  Can be
	 *    <code>null</code> if no changes are required
	 *    
	 * @throws RollbackException to force a roll-back of the current transaction
	 */
	Command transactionAboutToCommit(ResourceSetChangeEvent event) throws RollbackException;
	
	/**
	 * Informs the listener that changes have been committed to the model.
	 * Unlike the {@link #transactionAboutToCommit(ResourceSetChangeEvent)}
	 * call-back, this method has no opportunity to make subsequent changes via
	 * triggers or to roll back the transaction.  It has already committed.
	 * This has the advantage, however, of guaranteeing that it is safe to
	 * update the user interface or other dependent components or systems
	 * because the changes are committed.  This call-back is not invoked if
	 * the transaction rolls back, as all of its pending changes are reverted.
	 * <p>
	 * <b>Note</b> that the listener is invoked in a read-only transaction
	 * context.  It is safe to read the model, but changes are not permitted.
	 * </p>
	 * 
	 * @param event the event object describing the changes that occurred in
	 *    the resource set
	 */
	void resourceSetChanged(ResourceSetChangeEvent event);
	
	/**
	 * Queries whether I am interested only in the pre-commit
	 * ({@link #transactionAboutToCommit(ResourceSetChangeEvent)}) call-back.
	 * This helps the editing domain to optimize the distribution of events.
	 * <p>
	 * <b>Note</b> that this method is queried only once when the listener is
	 * added to the editing domain, so the result should not change over time.
	 * </p>
	 * 
	 * @return <code>true</code> if I only am interested in pre-commit events;
	 *     <code>false</code>, otherwise
	 */
	boolean isPrecommitOnly();
	
	/**
	 * Queries whether I am interested only in the post-commit
	 * ({@link #resourceSetChanged(ResourceSetChangeEvent)}) call-back.
	 * This helps the editing domain to optimize the distribution of events.
	 * <p>
	 * <b>Note</b> that this method is queried only once when the listener is
	 * added to the editing domain, so the result should not change over time.
	 * </p>
	 * 
	 * @return <code>true</code> if I only am interested in post-commit events;
	 *     <code>false</code>, otherwise
	 */
	boolean isPostcommitOnly();
}
