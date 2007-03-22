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
 * $Id: TransactionValidator.java,v 1.3 2007/03/22 19:11:49 cdamus Exp $
 */
package org.eclipse.emf.transaction.impl;

import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.Transaction;

/**
 * Interface for an object that validates a transaction when it commits.
 * Different implementations are provided for read and for write transactions.
 *
 * @author Christian W. Damus (cdamus)
 * 
 * @see ReadOnlyValidatorImpl
 * @see ReadWriteValidatorImpl
 * @see Transaction#commit()
 */
public interface TransactionValidator {
	/**
	 * A "null" instance that is suitable for use when there is no active
	 * transaction. It does not provide any notifications, nor does it validate
	 * anything.
	 */
	TransactionValidator NULL = new TransactionValidator() {
		// Documentation copied from the inherited specification
		public IStatus validate(Transaction tx) {
			return Status.OK_STATUS;
		}

		// Documentation copied from the inherited specification
		public void add(InternalTransaction transaction) {
			// nothing to do.  We do not record notifications
		}

		// Documentation copied from the inherited specification
		public void remove(InternalTransaction transaction) {
			// nothing to do.  We do not record notifications
		}

		// Documentation copied from the inherited specification
		public List getNotificationsForValidation(Transaction tx) {
			return Collections.EMPTY_LIST;
		}

		// Documentation copied from the inherited specification
		public List getNotificationsForPrecommit(Transaction tx) {
			return Collections.EMPTY_LIST;
		}

		// Documentation copied from the inherited specification
		public List getNotificationsForPostcommit(Transaction tx) {
			return Collections.EMPTY_LIST;
		}

		// Documentation copied from the inherited specification
		public void dispose() {
			// nothing to do.  We do not record notifications
		}
	};
	
	/**
	 * Adds a transaction for eventual validation.  Transactions must be added
	 * to the editing domain's current validator as soon as they are activated,
	 * so that it does not miss any notifications.
	 * 
	 * @param transaction the transaction (root or a nested transaction) to add
	 */
	void add(InternalTransaction transaction);
	
	/**
	 * Removes a transaction that has rolled back.  Transactions must be removed
	 * as soon as they roll back.  Removal ensures that we do not pass to the
	 * resource set listeners any notifications of changes that were rolled back.
	 * <p>
	 * Note that, for efficiency, transactions that have successfully committed
	 * should also be removed after they have been deactivated.  This ensures
	 * that they are no longer referenced by validator and can, therefore, be
	 * reclaimed (the validator retains the notifications, only).
	 * </p>
	 * 
	 * @param transaction the transaction (root or a nested transaction) to remove
	 */
	void remove(InternalTransaction transaction);
	
	/**
	 * Performs the validation step of a commit.
	 * 
	 * @param tx the transaction to validate
	 * 
	 * @return the status of validation.  If the severity is error or worse,
	 *     then the transaction <em>must</em> roll back, and this status
	 *     included in the exception
	 *     
	 * @see Transaction#commit()
	 * @see RollbackException
	 */
	IStatus validate(Transaction tx);
	
	/**
	 * Obtains the notifications received, in order, during the execution of
	 * the (possibly nested) transaction(s) that I am validating.
	 * 
	 * @param tx the transaction to be validated
	 * 
	 * @return the transaction's notifications, or <code>null</code> if the
	 *     transaction has not started yet
	 */
	List getNotificationsForValidation(Transaction tx);
	
	/**
	 * Obtains the notifications that I need to broadcast in a pre-commit
	 * resource-change event for the specified transaction.  Note that this
	 * does not include notifications from "no-triggers" transactions.
	 * 
	 * @param tx the transaction to be broadcast
	 * 
	 * @return those of the transaction's notifications that are eligible to
	 *     be broadcast, or <code>null</code> if the transaction has not started
	 */
	List getNotificationsForPrecommit(Transaction tx);
	
	/**
	 * Obtains the notifications that I need to broadcast in a post-commit
	 * resource-change event for the specified transaction.  Note that this
	 * does not include notifications from "silent" transactions.
	 * 
	 * @param tx the transaction to be broadcast
	 * 
	 * @return those of the transaction's notifications that are eligible to
	 *     be broadcast, or <code>null</code> if the transaction has not started
	 */
	List getNotificationsForPostcommit(Transaction tx);
	
	/**
	 * Disposes me by clearing my state and cleaning up any resources that I
	 * am retaining.
	 */
	void dispose();
    
    /**
     * Interface that clients implement to define a validator factory.
     * 
     * @since 1.1
     * 
     * @author David Cummings (dcummin)
     */
    interface Factory {
        /**
         * The shared default implementation of the validator factory interface.
         */
        Factory INSTANCE = new TransactionalEditingDomainImpl.ValidatorFactoryImpl();
        
        /**
         * Creates and returns a <code>TransactionValidator</code> which is
         * used to validate a read write transaction.
         * 
         * @since 1.1
         *
         * @return the transaction validator that will validate the 
         *          read write transaction
         */
        public TransactionValidator createReadOnlyValidator();
        
        /**
         * Creates and returns a <code>TransactionValidator</code> which is
         * used to validate a read only transaction.
         * 
         * @since 1.1
         *
         * @return the transaction validator that will validate the 
         *          read only transaction
         */
        public TransactionValidator createReadWriteValidator();
    }
}
