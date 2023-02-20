/**
 * Copyright (c) 2006, 2007 IBM Corporation and others.
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

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.transaction.RunnableWithResult;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.internal.EMFTransactionPlugin;
import org.eclipse.emf.transaction.internal.EMFTransactionStatusCodes;

/**
 * Implementation of the privileged runnable, which allows a thread to lend
 * its transaction to another cooperating thread for synchronous execution.
 * 
 * @param <T> the result type of the runnable
 * 
 * @author Christian W. Damus (cdamus)
 */
public final class PrivilegedRunnable<T> extends RunnableWithResult.Impl<T> {
	private final InternalTransaction transaction;
	private final Runnable delegate;
	private final Thread owner;
	
	/**
	 * Initializes me.
	 * 
	 * @param transaction the transaction that I share
	 * @param delegate the runnable that I will execute in this transaction
	 */
	PrivilegedRunnable(InternalTransaction transaction, Runnable delegate) {
		this.transaction = transaction;
		this.delegate = delegate;
		
		this.owner = Thread.currentThread();
	}
	
	/**
	 * Obtains the transaction to which I provide access.
	 * 
	 * @return my transaction
	 */
	public Transaction getTransaction() {
		return transaction;
	}
	
	/**
	 * Obtains the thread that created and owns me.  This thread is the
	 * original owner of my {@linkplain #getTransaction() transaction}.
	 * 
	 * @return my owner
	 */
	public Thread getOwner() {
		return owner;
	}
	
	/**
	 * Runs my delegate in the context of the transaction that I share.
	 */
	public void run() {
		@SuppressWarnings("unchecked")
		final RunnableWithResult<T> rwr = (delegate instanceof RunnableWithResult)?
			(RunnableWithResult<T>) delegate : null;
		
		boolean needPrivilege = transaction.getOwner() != Thread.currentThread();
		if (needPrivilege) {
			// no need for fancy stuff if the same thread is executing me as
			//    created me
			transaction.startPrivileged(this);
		}
		
		try {
			delegate.run();
			
			if (rwr != null) {
				if (rwr.getStatus() != null) {
					setStatus(rwr.getStatus());
				} else {
					setStatus(Status.OK_STATUS);
				}
				
				setResult(rwr.getResult());
			} else {
				setStatus(Status.OK_STATUS);
			}
		} catch (RuntimeException e) {
			String message = e.getLocalizedMessage();
			if (message == null) {
				message = e.getClass().getName();
			}
			setStatus(new Status(
					IStatus.ERROR, EMFTransactionPlugin.getPluginId(),
					EMFTransactionStatusCodes.PRIVILEGED_RUNNABLE_FAILED,
					message, e));
			throw e;
		} catch (Error e) {
			String message = e.getLocalizedMessage();
			if (message == null) {
				message = e.getClass().getName();
			}
			setStatus(new Status(
					IStatus.ERROR, EMFTransactionPlugin.getPluginId(),
					EMFTransactionStatusCodes.PRIVILEGED_RUNNABLE_FAILED,
					message, e));
			throw e;
		} finally {
			if (needPrivilege) {
				transaction.endPrivileged(this);
			}
		}
	}
}
