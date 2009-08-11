/******************************************************************************
 * Copyright (c) 2009 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation 
 ****************************************************************************/
package org.eclipse.emf.transaction.internal;

/**
 * Common interface for locks in transactionable editing domains.
 * 
 * @author Boris Gruschko
 * @since 1.4
 *
 */
public interface ITransactionLock {

	/**
	 * Queries the current owner of the lock.
	 * 
	 * @return the thread that owns me, or <code>null</code> if I am available
	 */
	public abstract Thread getOwner();

	/**
	 * Queries the depth to which I am acquired by the calling thread.  This is
	 * the number of times the calling thread has acquired me and not yet
	 * released.  Note that if the calling thread does not own me, I appear to
	 * have a depth of zero.  Acquiring in this case will wait for the owning
	 * thread to finish releasing.
	 * 
	 * @return my depth
	 */
	public abstract int getDepth();

	/**
	 * Acquires me, waiting as long as necessary or until I am interrupted.
	 * if I already own this lock, then its lock depth is increased.  That means
	 * one more call to {@link #release()} for me to make.
	 * <p>
	 * <b>Note:</b>  The current thread must not own my monitor when it calls
	 * this method, otherwise it will cause deadlock.  Deadlock would be
	 * guaranteed because every thread waits on a different object that is
	 * not me, so my monitor is <b>not</b> released when the calling thread
	 * blocks.
	 * </p>
	 * 
	 * @param exclusive <code>true</code> if the current thread needs exclusive
	 *     access (i.e., no other threads may currently be
	 *     {@link #yield() yielding} me); <code>false</code>, otherwise
	 *     
	 * @throws InterruptedException on interruption of the calling thread
	 */
	public abstract void acquire(boolean exclusive) throws InterruptedException;

	/**
	 * Attempts to acquire me, timing out after the specified number of millis.
	 * <p>
	 * <b>Note:</b>  The current thread must not own my monitor when it calls
	 * this method, otherwise it will cause deadlock.  Deadlock would be
	 * guaranteed because every thread waits on a different object that is
	 * not me, so my monitor is <b>not</b> released when the calling thread
	 * blocks.
	 * </p>
	 * 
	 * @param timeout the number of milliseconds to wait before giving up on
	 *     the lock, or <code>0</code> to wait as long as necessary
	 * @param exclusive <code>true</code> if the current thread needs exclusive
	 *     access (i.e., no other threads may currently be
	 *     {@link #yield() yielding} me); <code>false</code>, otherwise
	 *     
	 * @return <code>true</code> if the caller successfully acquired me;
	 *    <code>false</code> if it did not within the <code>timeout</code>
	 *    
	 * @throws IllegalArgumentException if <code>timeout</code> is negative
	 * @throws InterruptedException on interruption of the calling thread
	 */
	public abstract boolean acquire(long timeout, boolean exclusive)
			throws InterruptedException;

	/**
	 * Attempts to acquire me (without a timeout) in a manner that is safe to
	 * execute on the UI thread.  This ensures that, in an Eclipse UI
	 * environment, if the UI thread is blocked waiting for me, the Job Manager
	 * will show the block dialog to inform the user of what is happening.
	 * <p>
	 * If this method is called from a thread that is running as a Job, then
	 * it behaves identically to {@link #acquire(boolean)}.
	 * </p>
	 * <p>
	 * <b>Note:</b>  The current thread must not own my monitor when it calls
	 * this method, otherwise it will cause deadlock.  Deadlock would be
	 * guaranteed because every thread waits on a different object that is
	 * not me, so my monitor is <b>not</b> released when the calling thread
	 * blocks.
	 * </p>
	 * 
	 * @param exclusive <code>true</code> if the current thread needs exclusive
	 *     access (i.e., no other threads may currently be
	 *     {@link #yield() yielding} me); <code>false</code>, otherwise
	 * 
	 * @throws InterruptedException in case of interrupt while waiting
	 *     or if the user cancels the lock-acquisition job that is blocking
	 *     the UI thread
	 */
	public abstract void uiSafeAcquire(boolean exclusive)
			throws InterruptedException;

	/**
	 * Releases me.  Note that my depth may still be positive, in which case
	 * I would need to be released again (recursively).
	 * 
	 * @throws IllegalStateException if the calling thread does not own me
	 */
	public abstract void release();

	/**
	 * Temporarily yields the lock to another thread that does not require
	 * exclusive access, if any such thread exists.  Note that, if this method
	 * returns <code>true</code>, then the caller must actually
	 * {@linkplain #release() release}
	 * me before another thread can take me.  It then resumes by acquiring me
	 * again, layer.
	 * 
	 * @return <code>true</code> if the lock was successfully yielded to another
	 *     thread; <code>false</code>, otherwise
	 */
	public abstract boolean yield();

	/**
	 * @noreference This method is not intended to be referenced by clients.
	 */
	public void checkedTransfer(Thread thread);
}