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
 * $Id: NonEMFTransaction.java,v 1.2.2.1 2006/08/30 16:10:14 cmcgee Exp $
 */
package org.eclipse.emf.workspace.impl;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.impl.TransactionImpl;
import org.eclipse.emf.workspace.util.OperationChangeDescription;

/**
 * A transaction encapsulating non-EMF changes (as represented by an
 * unoable operation).
 *
 * @author Christian W. Damus (cdamus)
 */
public class NonEMFTransaction extends TransactionImpl {
	private final IUndoableOperation operation;
	private IAdaptable info;
	
	/**
	 * Initializes me with the undoable operation that represents my non-EMF
	 * changes.
	 * 
	 * @param domain my editing domain
	 * @param operation the non-EMF changes that I record
	 * @param info the adaptable object provided to the operation when it is
	 *     executed
	 */
	public NonEMFTransaction(TransactionalEditingDomain domain,
			IUndoableOperation operation,
			IAdaptable info) {
		this(domain, operation, info, null);
	}

	/**
	 * Initializes me with the undoable operation that represents my non-EMF
	 * changes and transaction options.
	 * 
	 * @param domain my editing domain
	 * @param operation the non-EMF changes that I record
	 * @param options my options
	 * @param info the adaptable object provided to the operation when it is
	 *     executed
	 */
	public NonEMFTransaction(TransactionalEditingDomain domain,
			IUndoableOperation operation,
			IAdaptable info,
			Map options) {
		super(domain, false, customizeOptions(options));
		
		this.operation = operation;
		this.info = info;
	}
	
	/**
	 * Customizes the provided options for this transaction.
	 * 
	 * @param options The options provided by the call to the constructor that
	 *  should be customized.
	 *  
	 * @return A new map of options that should be passed to the superclass to
	 *  become our official set of options.
	 */
	private static Map customizeOptions(Map options) {
		// Copy the options and add the special non-change description propagation
		//  option. We do this because if by any chance that the child operation
		//  invokes some AbstractEMFOperation again then it will be handling the
		//  applying and reversing of its own change description, not us. However,
		//  transactions by default will propagate all of the change descriptions upward.
		//  This can cause situations where the same change description object is applied
		//  and reversed twice.
		options = new HashMap(options);
		options.put(TransactionImpl.ALLOW_CHANGE_PROPAGATION_BLOCKING, Boolean.TRUE);
		
		return options;
	}

	/**
	 * Appends my non-EMF change and commits.
	 */
	public void commit() throws RollbackException {
		change.add(new OperationChangeDescription(operation, info));
		
		info = null;  // don't need the info any longer
		
		super.commit();
	}
}
