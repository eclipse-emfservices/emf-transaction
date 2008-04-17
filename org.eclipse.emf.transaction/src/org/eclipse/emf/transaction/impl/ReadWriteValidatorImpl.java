/**
 * <copyright>
 *
 * Copyright (c) 2005, 2008 IBM Corporation and others.
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
 * $Id: ReadWriteValidatorImpl.java,v 1.12 2008/04/17 16:36:17 cdamus Exp $
 */
package org.eclipse.emf.transaction.impl;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
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
public class ReadWriteValidatorImpl implements TransactionValidator {
	/** Bit indicating that we are collecting notifications for validation. */
	static final byte VALIDATION = 1;
	/** Bit indicating that we are collecting notifications for pre-commit. */
	static final byte PRECOMMIT = 2;
	/** Bit indicating that we are collecting notifications for post-commit. */
	static final byte POSTCOMMIT = 4;
	
	// a tree of notifications gathered by (potentially) nested transactions
	private NotificationTree tree = null;
	
	// the next transaction to pre-commit in the "aggregate" mode
	private NotificationTree transactionToPrecommit = null;
	
	// maps the active transaction and its ancestors to notification tree nodes
	private final Map<InternalTransaction, NotificationTree> txToNode =
		new java.util.HashMap<InternalTransaction, NotificationTree>();
	
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
		byte notificationMask = computeNotificationMask(transaction);
		
		// there is no point in allocating a tree node for a transaction that
		//    does not collect notifications
		if (notificationMask > 0) {
			NotificationTree parent = findTree(transaction.getParent());
			NotificationTree newTree = null;
			
			if (transaction.getParent() == null) {
				// got the root transaction
				tree = new NotificationTree(transaction, null, notificationMask);
				newTree = tree;
			} else if (parent != null) {
				// if the transaction has a parent but there is no matching
				//    tree node, then the parent is silent, so there is no
				//    need to create any descendent nodes
				newTree = parent.addChild(transaction, notificationMask);
			}
			
			if (newTree != null) {
				txToNode.put(transaction, newTree);
				
				// next phase of aggregated precommit must start with this transaction
				if ((transactionToPrecommit == null) && !transaction.isReadOnly()) {
					transactionToPrecommit = newTree;
				}
			}
		}
	}
	
	/**
	 * Removes the specified transaction from me.  This must be called
	 * when the transaction is rolled back, and is recommended also after a
	 * successful commit.
	 */
	public void remove(InternalTransaction transaction) {
		NotificationTree node = findTree(transaction);
		
		if (node != null) {
			if (transaction.isRollingBack()) {
				// the transaction is not yet closed, but is rolling back:
				//    filter the notifications now
				node.setRolledBack();
			} else {
				// unmap the closed transaction
				txToNode.remove(transaction);
				
				// obtains the transaction's notifications now and forget the
				//   reference to it
				node.detachTransaction();
			}
		}
	}
	
	// Documentation copied from the inherited method specification
	public synchronized List<Notification> getNotificationsForValidation(Transaction tx) {
		List<Notification> result = null;
		
		if (tree != null) {
			NotificationTree nested = findTree(tx);
			
			if (nested != null) {
				result = nested.collectNotifications(VALIDATION);
			}
		}
		
		return result;
	}
	
	// Documentation copied from the inherited method specification
	public synchronized List<Notification> getNotificationsForPrecommit(Transaction tx) {
		List<Notification> result = null;
		
		if ((transactionToPrecommit != null)
				&& (transactionToPrecommit == findTree(tx))) {
			
			result = transactionToPrecommit.collectNotifications(PRECOMMIT);
			
			// the next transaction that is created (for executing triggers)
			//     will be the next one that we collect these notifications for
			transactionToPrecommit = null;
		}
		
		return result;
	}
	
	// Documentation copied from the inherited method specification
	public synchronized List<Notification> getNotificationsForPostcommit(Transaction tx) {
		List<Notification> result = null;
		
		if (tree != null) {
			NotificationTree nested = findTree(tx);
			
			if (nested != null) {
				result = nested.collectNotifications(POSTCOMMIT);
			}
		}
		
		return result;
	}
	
	/**
	 * Finds the specified transaction's corresponding node in the notification
	 * tree structure that I maintain.
	 * 
	 * @param tx the transaction to search for
	 * 
	 * @return the corresponding notification tree node, or
	 *    <code>null</code> if this transaction has not yet been added to me
	 *    or has already completed (in which case, it is no longer in my map)
	 */
	private NotificationTree findTree(Transaction tx) {
		NotificationTree result = null;
		
		if (tree != null) {
			result = txToNode.get(tx);
		}
		
		return result;
	}
	
	
	// Documentation copied from the inherited method specification
	public IStatus validate(Transaction tx) {
		IStatus result;
		
		try {
			IValidator<Notification> validator = createValidator();
			
			result = validator.validate(getNotificationsForValidation(tx));
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
	
	/**
	 * Creates a validator which will be responsible for the transaction validation
     *
     * @since 1.1
     *
	 * @return the validator
	 */
	protected IValidator<Notification> createValidator() {
		return ModelValidationService.getInstance().newValidator(EvaluationMode.LIVE);
	}
	
	// Documentation copied from the inherited method specification
	public void dispose() {
		tree = null;
		txToNode.clear();
		transactionToPrecommit = null;
	}

	/**
	 * Computes a mask of notification kinds that the specified transaction
	 * supports.  The notification kinds indicate which functions that use
	 * notifications are enabled for the transaction.
	 * 
	 * @param transaction a transaction
	 * @return a mask of the {@link #POSTCOMMIT}, {@link #PRECOMMIT}, and
	 *     {@link #VALIDATION} bits
	 */
	private static byte computeNotificationMask(Transaction transaction) {
		byte result = 0;
		
		if (TransactionImpl.isNotificationEnabled(transaction)) {
			result |= ReadWriteValidatorImpl.POSTCOMMIT;
		}
		if (TransactionImpl.isTriggerEnabled(transaction)) {
			result |= ReadWriteValidatorImpl.PRECOMMIT;
		}
		if (TransactionImpl.isValidationEnabled(transaction)) {
			result |= ReadWriteValidatorImpl.VALIDATION;
		}
		
		return result;
	}
	
	/**
	 * A tree mirroring the nesting structure of transactions.  The tree
	 * records, for every transaction:
	 * <ul>
	 *   <li>the notifications (by directly referencing the mutable
	 *       notification list)</li>
	 *   <li>tree nodes for corresponding to the the children of the transaction
	 *       (transactions otherwise only know their parents)</li>
	 *   <li>the number of notifications in the parent transaction that
	 *       preceded its activation, if it has a parent</li>
	 *   <li>a bit mask indicating which kinds of notifications (pre/post commit
	 *       and validation) the transaction provides</li>
	 * </ul>
	 * <p>
	 * The third item above is important in reconstructing the complete
	 * ordering (in linear time) of the notifications received during nesting
	 * transactions, so that both validation and post-commit listeners get
	 * the correct sequence of events.
	 * </p>
	 * 
	 * @author Christian W. Damus (cdamus)
	 */
	private static class NotificationTree {
		private final List<NotificationTree> children =
			new org.eclipse.emf.common.util.BasicEList.FastCompare<NotificationTree>();
		
		// number of notifications in parent before start of child transaction
		private int parentNotificationCount;
		
		private InternalTransaction transaction;
		private List<Notification> notifications;  // stores the notifications
		
		private final byte notificationMask;
		
		/**
		 * Initializes a new tree node for the specified transaction.  If it
		 * has a parent, then it records the number of notifications that the
		 * parent has so far collected.
		 * 
		 * @param transaction the transaction
		 * @param parent the tree node of the parent transaction, or <code>null</code>
		 *     if there is no parent node
		 * @param notificationMask a mask of the {@link #POSTCOMMIT}, {@link #PRECOMMIT}, and
		 *     {@link #VALIDATION} bits of the kinds of notifications that the
		 *     transaction collects
		 */
		NotificationTree(InternalTransaction transaction, NotificationTree parent, byte notificationMask) {
			assert notificationMask > 0: "transaction must be collecting notifications"; //$NON-NLS-1$
			
			this.transaction = transaction;
			
			if (parent == null) {
				parentNotificationCount = 0;
			} else {
				parentNotificationCount = parent.getNotifications().size();
			}
			
			this.notificationMask = notificationMask;
		}
		
		/**
		 * Adds a child transaction to me.  If this transaction has no parent,
		 * then it is the root transaction.
		 * 
		 * @param child the child transaction to add
		 * @param notificationMask a mask of the {@link #POSTCOMMIT}, {@link #PRECOMMIT}, and
		 *     {@link #VALIDATION} bits of the kinds of notifications that the
		 *     transaction collects
		 */
		NotificationTree addChild(InternalTransaction child, byte notificationMask) {
			NotificationTree result = new NotificationTree(child, this, notificationMask);
			
			children.add(result);
			
			return result;
		}
		
		/**
		 * Obtains my child nodes, storing the notifications from my
		 * transaction's children.
		 * 
		 * @return my children
		 */
		List<NotificationTree> getChildren() {
			return children;
		}
		
		/**
		 * Collects all of the notifications from me and my children, in the
		 * correct time-linear order.
		 * 
		 * @param purpose a bit indicating what kind of notifications
		 *     to collect (for what purpose we are collecting them)
		 * 
		 * @return my notifications (which might be an empty list)
		 * 
		 * @see ReadWriteValidatorImpl#VALIDATION
		 * @see ReadWriteValidatorImpl#PRECOMMIT
		 * @see ReadWriteValidatorImpl#POSTCOMMIT
		 */
		List<Notification> collectNotifications(byte purpose) {
			List<Notification> result;
			
			if ((notificationMask & purpose) == purpose) {
				result = new java.util.ArrayList<Notification>();
				collectNotifications(result, purpose);
			} else {
				result = Collections.emptyList();
			}
			
			return result;
		}
		
		/**
		 * Recursive implementation of the {@link #collectNotifications()} method.
		 * 
		 * @param notifications the accumulator list
		 * @param purpose a bit indicating what kind of notifications
		 *     to collect (for what purpose we are collecting them)
		 * 
		 * @see #collectNotifications()
		 */
		private void collectNotifications(List<? super Notification> notifications, byte purpose) {
			if ((notificationMask & purpose) == purpose) {
				int lastIndex = 0;
				List<Notification> parentNotifications = getNotifications();
				
				for (NotificationTree next : children) {
					// append the parent transaction's notifications from the
					//    last position to this child's position
					notifications.addAll(parentNotifications.subList(
							lastIndex,
							next.parentNotificationCount));
					lastIndex = next.parentNotificationCount;
					
					next.collectNotifications(notifications, purpose);
				}
				
				// append the remaining notifications following the last child
				notifications.addAll(parentNotifications.subList(
						lastIndex,
						parentNotifications.size()));
			}
		}
		
		/**
		 * Indicates that my transaction has been rolled back.  This will
		 * reduce the list of notifications that I store to only those indicating
		 * changes that rollback did not revert (i.e., resource-level changes
		 * that are not semantic changes, such as resource load/unload, URI
		 * change, etc.).
		 */
		void setRolledBack() {
			Iterator<NotificationTree> children = getChildren().iterator();
			boolean inPlace = transaction == null;
			
            ListIterator<Notification> iter;
			if (inPlace) {
			    // already committed?  Some ancestor transaction is rolling back
			    iter = notifications.listIterator();
			} else {
			    // transaction has the notifications
			    iter = transaction.getNotifications().listIterator();
	            notifications = new org.eclipse.emf.common.util.BasicEList.FastCompare<Notification>();
			}
			
			// filter out all undoable notifications, leaving only those that
			//    indicate changes that rollback could not undo (resource-level
			//    changes).  In doing so, adjust the information indicating
			//    where in the notification ordering the child transactions fit
			//    so that we retain correct linear ordering overall
			for (int i = 0; children.hasNext();) {
				NotificationTree child = children.next();
				int parentNotificationCount = child.parentNotificationCount;
				
				if (inPlace) {
	                for (; (i < parentNotificationCount) && iter.hasNext(); i++) {
					    if (isUndoableObjectChange(iter.next())) {
					        iter.remove();
					    }
	                }
                    
	                // we have reached the point in the original notifications
	                //    where this child transaction started.  Adjust for the
	                //    reduced list of notifications
	                child.parentNotificationCount = iter.nextIndex();
				} else {
                    for (; (i < parentNotificationCount) && iter.hasNext(); i++) {
                        Notification next = iter.next();
                        
    					if (!isUndoableObjectChange(next)) {
    						notifications.add(next);
    					}
					}
                    
                    // we have reached the point in the original notifications
                    //    where this child transaction started.  Adjust for the
                    //    reduced list of notifications
                    child.parentNotificationCount = notifications.size();
				}
				
				// recurse onto its children
				child.setRolledBack();
			}
			
			// filter the remaining notifications
			if (inPlace) {
    			while (iter.hasNext()) {
    				if (isUndoableObjectChange(iter.next())) {
    					iter.remove();
    				}
    			}
			} else {
                while (iter.hasNext()) {
                    Notification next = iter.next();
                    
                    if (!isUndoableObjectChange(next)) {
                        notifications.add(next);
                    }
                }
			}
		}
		
		/**
		 * Determines whether the specified notification indicates an undoable
		 * change to a model element.  This filters out non-model changes such
		 * as changes to the modification/loaded state of resources, their
		 * URIs, etc.
		 * 
		 * @param notification a notification
		 * @return <code>true</code> if it represents an undoable change to an
		 *     object or a resource (the contents list, in particular)
		 */
		private boolean isUndoableObjectChange(Notification notification) {
			return (notification.getNotifier() instanceof EObject) ||
				((notification.getNotifier() instanceof Resource)
						&& (notification.getFeatureID(Resource.class) == Resource.RESOURCE__CONTENTS));
		}
		
		/**
		 * Obtains my corresponding transaction's notifications.
		 * 
		 * @return my notifications
		 */
		List<Notification> getNotifications() {
			return (notifications != null)? notifications :
				transaction.getNotifications();
		}
		
		/**
		 * Detaches the node from its transaction.
		 */
		void detachTransaction() {
			if (notifications == null) {
				// we may already have the notifications if our transaction
				//    rolled back and we had to filter them
				notifications = transaction.getNotifications();
			}
			
			transaction = null;
		}
	}
}
