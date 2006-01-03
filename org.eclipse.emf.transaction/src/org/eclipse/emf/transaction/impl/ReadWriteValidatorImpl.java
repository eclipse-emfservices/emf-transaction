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
 * $Id: ReadWriteValidatorImpl.java,v 1.1 2006/01/03 20:41:54 cdamus Exp $
 */
package org.eclipse.emf.transaction.impl;

import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.internal.EMFTransactionPlugin;
import org.eclipse.emf.transaction.internal.EMFTransactionStatusCodes;
import org.eclipse.emf.transaction.internal.Tracing;
import org.eclipse.emf.transaction.internal.l10n.Messages;
import org.eclipse.emf.validation.model.EvaluationMode;
import org.eclipse.emf.validation.service.IValidator;
import org.eclipse.emf.validation.service.ModelValidationService;

/**
 * A validator for read/write transactions.  It provides all of the notifications
 * (in order) that occurred during the transaction, and validates them to detect
 * changes that would violate model integrity.
 * <p>
 * A read/write validator should be created for the root transaction of any
 * nested read/write transaction structure, when the root transaction is
 * activated.  As child transactions are activated, they must be
 * {@link #add(InternalTransaction) added} to me so that I may correctly track
 * which notifications were received during which transaction, and at which
 * time relative to the start and completion of nested transactions.
 * </p>
 * <p>
 * Whenever a transaction (nested or otherwise) is rolled back, it must be
 * {@link #remove(InternalTransaction) removed} from me so that I may forget
 * the notifications received for any changes that it or its nested transactions
 * made.
 * </p>
 *
 * @author Christian W. Damus (cdamus)
 * 
 * @see ReadOnlyValidatorImpl
 */
public class ReadWriteValidatorImpl implements TXValidator {
	private TransactionTree tree = null;
	private List notifications = null;
	
	/**
	 * Initializes me.
	 */
	public ReadWriteValidatorImpl() {
		super();
	}
	
	/**
	 * Adds the specified transaction to me for validation.  This must be called
	 * when the transaction is activated.
	 */
	public void add(InternalTransaction transaction) {
		TransactionTree parent = findTree(transaction.getParent());
		
		if (parent == null) {
			// got the root transaction
			tree = new TransactionTree(transaction);
		} else {
			parent.addChild(transaction);
		}
		
		notifications = null;  // clear cache (if any)
	}
	
	/**
	 * Removes the specified transaction from me.  This must be called
	 * when the transaction is rolled back (<em>not</em> when it is committed).
	 */
	public void remove(InternalTransaction transaction) {
		TransactionTree parent = findTree(transaction.getParent());
		
		if (parent != null) {
			parent.removeChild(transaction);
		}
		
		notifications = null;  // clear cache (if any)
	}
	
	// Documentation copied from the inherited method specification
	public synchronized List getNotifications() {
		if ((notifications == null) && (tree != null)) {
			notifications = tree.collectNotifications();
		}
		
		return notifications;
	}
	
	/**
	 * Finds the specified transaction in the tree structure that I maintain.
	 * 
	 * @param tx the transaction to search for
	 * 
	 * @return the corresponding tree node, or <code>null</code> if this
	 *    transaction has not yet been added to me
	 */
	private TransactionTree findTree(Transaction tx) {
		TransactionTree result = null;
		
		if (tree != null) {
			result = tree.find(tx);
		}
		
		return result;
	}
	
	
	// Documentation copied from the inherited method specification
	public IStatus validate() {
		IStatus result;
		
		try {
			IValidator validator = ModelValidationService.getInstance().newValidator(
				EvaluationMode.LIVE);
			
			result = validator.validate(getNotifications());
		} catch (Exception e) {
			Tracing.catching(ReadWriteValidatorImpl.class, "validate", e); //$NON-NLS-1$
			result = new Status(
				IStatus.ERROR,
				EMFTransactionPlugin.getPluginId(),
				EMFTransactionStatusCodes.VALIDATION_FAILURE,
				Messages.validationFailure,
				e);
		}
		
		return result;
	}
	
	// Documentation copied from the inherited method specification
	public void dispose() {
		tree = null;
		notifications = null;
	}

	/**
	 * A tree mirroring the nesting structure of transactions.  The tree
	 * records, for every transaction:
	 * <ul>
	 *   <li>the children of the transaction (transactions otherwise only know
	 *       their parents)</li>
	 *   <li>the number of notifications in the parent transaction that
	 *       preceded its activation, if it has a parent</li>
	 * </ul>
	 * <p>
	 * The second item above is important in reconstructing the complete
	 * ordering (in linear time) of the notifications received during nesting
	 * transactions, so that both validation and post-commit listeners get
	 * the correct sequence of events.
	 * </p>
	 * 
	 * @author Christian W. Damus (cdamus)
	 */
	private static class TransactionTree {
		private final InternalTransaction transaction;
		private final List children = new java.util.ArrayList();
		
		// number of notifications in parent before start of child transaction
		private final int parentNotificationCount;
		
		/**
		 * Initializes a new tree node for the specified transaction.  If it
		 * has a parent, then it records the number of notifications that the
		 * parent has so far collected.
		 * 
		 * @param transaction the transaction
		 */
		TransactionTree(InternalTransaction transaction) {
			this.transaction = transaction;
			
			InternalTransaction parent = (InternalTransaction) transaction.getParent();
			
			if (parent == null) {
				parentNotificationCount = 0;
			} else {
				parentNotificationCount = parent.getNotifications().size();
			}
		}
		
		/**
		 * Adds a child transaction to me.  If this transaction has no parent,
		 * then it is the root transaction.
		 * 
		 * @param child the child transaction to add
		 */
		void addChild(InternalTransaction child) {
			children.add(new TransactionTree(child));
		}
		
		/**
		 * Removes the specified child transaction from me (which has rolled
		 * back).
		 * 
		 * @param child the child transaction to remove
		 */
		void removeChild(InternalTransaction child) {
			for (Iterator iter = children.iterator(); iter.hasNext();) {
				if (((TransactionTree) iter.next()).getTransaction() == child) {
					iter.remove();
					break;
				}
			}
		}
		
		/**
		 * Finds the tree node corresponding to the specified transaction.
		 * 
		 * @param tx the transaction to search for
		 * @return the corresponding tree node, or <code>null</code> if it has
		 *    not (yet) been added
		 */
		TransactionTree find(Transaction tx) {
			TransactionTree result = null;
			
			if (transaction == tx) {
				result = this;
			} else {
				for (Iterator iter = children.iterator();
						iter.hasNext() && (result == null);) {
					
					result = ((TransactionTree) iter.next()).find(tx);
				}
			}
			
			return result;
		}
		
		/**
		 * Obtains the transaction that this tree node represents.
		 * 
		 * @return my transaction
		 */
		InternalTransaction getTransaction() {
			return transaction;
		}
		
		/**
		 * Obtains my child nodes, representing my transaction's children.
		 * 
		 * @return my children
		 */
		List getChildren() {
			return children;
		}
		
		/**
		 * Collects all of the notifications from me and my children, in the
		 * correct time-linear order.
		 * 
		 * @return my notifications (which might be an empty list)
		 */
		List collectNotifications() {
			List result = new java.util.ArrayList();
			
			collectNotifications(result);
			
			return result;
		}
		
		/**
		 * Recursive implementation of the {@link #collectNotifications()} method.
		 * 
		 * @param notifications the accumulator list
		 * 
		 * @see #collectNotifications()
		 */
		private void collectNotifications(List notifications) {
			int lastIndex = 0;
			List parentNotifications = transaction.getNotifications();
			
			for (Iterator iter = children.iterator(); iter.hasNext();) {
				TransactionTree next = (TransactionTree) iter.next();
				
				// append the parent transaction's notifications from the last position
				//    to this child's position
				notifications.addAll(parentNotifications.subList(
						lastIndex,
						next.parentNotificationCount));
				lastIndex = next.parentNotificationCount;
				
				next.collectNotifications(notifications);
			}
			
			// append the remaining notifications following the last child
			notifications.addAll(parentNotifications.subList(
					lastIndex,
					parentNotifications.size()));
		}
	}
}
