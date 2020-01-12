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
package org.eclipse.emf.transaction;

import org.eclipse.core.runtime.IStatus;


/**
 * Extends the core Java {@link Runnable} interface with the ability to return
 * a result.  Useful for returning results of read operations from the
 * {@link TransactionalEditingDomain#runExclusive(Runnable)} method.
 * <p>
 * Also, because read transactions can roll back on commit if, for example, some
 * other thread performs a concurrent write that corrupts the data being read,
 * this interface also provides a means to set a status to indicate success
 * or failure of the transaction.
 * </p>
 * 
 * @param <T> the result type of the runnable
 * 
 * @author Christian W. Damus (cdamus)
 * 
 * @see TransactionalEditingDomain#runExclusive(Runnable)
 */
public interface RunnableWithResult<T>
	extends Runnable {

	/**
	 * Returns a result computed by my {@link Runnable#run()} method.
	 * 
	 * @return my result, or <code>null</code> if none
	 */
	T getResult();
	
	/**
	 * Sets the commit status after completion of the {@link Runnable#run()} method.
	 *
	 * @param status an OK status if commit succeeds, or an error status
	 *     if it fails (in which case the transaction rolled back and the status
	 *     provides details in human-readable form)
	 */
	void setStatus(IStatus status);
	
	/**
	 * Queries my commit status.  My status is only available after I have
	 * finished running and after the editing domain has attempted to commit
	 * my transaction.
	 * 
	 * @return the status of my commit (as set by the {@link #setStatus(IStatus)} method)
	 */
	IStatus getStatus();
	
	/**
	 * A convenient partial implementation of the {@link RunnableWithResult}
	 * interface that implements a settable {@link #setResult(Object) result}
	 * field and commit status.
	 *
	 * @author Christian W. Damus (cdamus)
	 */
	static abstract class Impl<T> implements RunnableWithResult<T> {
		private T result;
		private IStatus status;
		
		/**
		 * Sets my result.
		 * 
		 * @param result my result
		 */
		protected final void setResult(T result) {
			this.result = result;
		}
		
		// Documentation copied from interface
		public final IStatus getStatus() {
			return status;
		}
		
		// Documentation copied from interface
		public final T getResult() {
			return result;
		}
		
		// Documentation copied from interface
		public final void setStatus(IStatus status) {
			this.status = status;
		}
	}
}
