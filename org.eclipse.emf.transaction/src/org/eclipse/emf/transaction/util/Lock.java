/**
 * <copyright>
 *
 * Copyright (c) 2005, 2006 IBM Corporation and others.
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
 * $Id: Lock.java,v 1.5.2.1 2006/07/13 19:07:07 cdamus Exp $
 */
package org.eclipse.emf.transaction.util;

import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.emf.transaction.internal.EMFTransactionDebugOptions;
import org.eclipse.emf.transaction.internal.EMFTransactionPlugin;
import org.eclipse.emf.transaction.internal.Tracing;
import org.eclipse.emf.transaction.internal.l10n.Messages;

/**
 * Implementation of a lock.  The lock is recursive; it can be
 * {@link #acquire(boolean) acquired} any number of times by the same thread,
 * but it records how many times it was acquired (its {@link #getDepth() depth}).
 * It must be {@link #release() released} as many times as it was acquired.
 * <p>
 * If there is a possibility that the thread attempting to acquire a lock may
 * be the Eclipse UI thread, then it is advisable to use the
 * {@link #uiSafeAcquire(boolean)} method to acquire the lock.  This method
 * uses the Eclipse Jobs API to ensure that the UI thread maintains the
 * liveness of the event loop and shows the "Blocked" dialog when necessary to
 * inform the user of what other activities in the resource that it is waiting
 * for.
 * </p>
 * <p>
 * A lock may be acquired for exclusive or non-exclusive access to the resource
 * that it is protecting.  Any thread that holds the lock non-exclusively can
 * {@link #yield() yield} it to other threads that are waiting for it as long
 * as those threads are trying to acquire it for non-exclusive access.  If the
 * <code>yield</code> returns <code>true</code>, then the thread must release
 * it (to whatever depth it currently holds it) so that others may acquire it.
 * While the lock is being yielded, it cannot be acquired for exclusive access.
 * </p>
 * <p>
 * This lock implementation ensures fairness of awakening threads waiting to
 * acquire it by enqueuing them in FIFO fashion.  In addition, if a thread
 * times out of a timed <code>acquire</code> call, it maintains its position in
 * the queue if it re-attempts the acquire before it is dequeued.  This helps
 * threads that need to time out regularly (e.g., to check for progress monitor
 * cancellation) to still benefit from the fairness of the scheduling strategy.
 * </p>
 * <p>
 * The interaction of threads with <code>Lock</code> instances can be debugged
 * by enabling the <code>org.eclipse.emf.transaction/debug/locking</code>
 * trace option.
 * </p>
 *
 * @author Christian W. Damus (cdamus)
 */
public class Lock {
	private static final IJobManager jobmgr = Platform.getJobManager();
	
	private static long nextId = 0;
	
	/**
	 * The status object returned by an {@link AcquireJob} when it detects that
	 * the thread for which it is acquiring the lock has somehow obtained it
	 * independently.
	 */
	static final IStatus UI_REENTERED_STATUS = new Status(
			IStatus.INFO,
			EMFTransactionPlugin.getPluginId(),
			1,
			"UI thread re-entered to get the lock", //$NON-NLS-1$
			null);

	private final long id;
	
	private volatile Thread owner = null;

	private int depth = 0;
	
	// threads currently waiting for the lock
	private final Queue waiting = new Queue();
	
	// threads currently yielding read access
	// must use identity map because threads can override equals()
	private final Map yielders = new java.util.IdentityHashMap();
	
	/**
	 * Initializes me.
	 */
	public Lock() {
		synchronized (Lock.class) {
			this.id = ++nextId;
		}
	}

	/**
	 * Queries the current owner of the lock.
	 * 
	 * @return the thread that owns me, or <code>null</code> if I am available
	 */
	public Thread getOwner() {
		// note that there is no need to synchronize this method because, if
		//   calling thread is currently the owner, it cannot cease to be while
		//   invoking this method.  Likewise, if it is not the owner, it cannot
		//   become the owner while invoking this method
		return owner;
	}
	
	/**
	 * Queries the depth to which I am acquired by the calling thread.  This is
	 * the number of times the calling thread has acquired me and not yet
	 * released.  Note that if the calling thread does not own me, I appear to
	 * have a depth of zero.  Acquiring in this case will wait for the owning
	 * thread to finish releasing.
	 * 
	 * @return my depth
	 */
	public int getDepth() {
		// note that there is no need to synchronize this method because, if
		//   calling thread is currently the owner, it cannot cease to be while
		//   invoking this method.  Likewise, if it is not the owner, it cannot
		//   become the owner while invoking this method
		if (Thread.currentThread() != owner) {
			return 0;
		}
		
		return depth;
	}

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
	public void acquire(boolean exclusive) throws InterruptedException {
		acquire(0L, exclusive);
	}

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
	public boolean acquire(long timeout, boolean exclusive) throws InterruptedException {
		if (timeout < 0) {
			IllegalArgumentException exc = new IllegalArgumentException("negative timeout"); //$NON-NLS-1$
			Tracing.throwing(Lock.class, "acquire", exc); //$NON-NLS-1$
			throw exc;
		}
		
		// should always check whether a thread is already interrupted before
		//     trying to get a lock
		if (Thread.interrupted()) {
			InterruptedException exc = new InterruptedException();
			Tracing.throwing(Lock.class, "acquire", exc); //$NON-NLS-1$
			throw exc;
		}
		
		final Thread current = Thread.currentThread();
		
		if (Tracing.shouldTrace(EMFTransactionDebugOptions.LOCKING)) {
			if (timeout > 0L) {
				Tracing.trace("::: Timed Acquire    [id=" //$NON-NLS-1$
						+ id + ", thread=" + current.getName() //$NON-NLS-1$
						+ ", exclusive=" + exclusive //$NON-NLS-1$
						+ ", timeout=" + timeout + ']' //$NON-NLS-1$
						+ " at " + Tracing.now()); //$NON-NLS-1$
			} else {
				Tracing.trace("::: Acquire          [id=" //$NON-NLS-1$
						+ id + ", thread=" + current.getName() //$NON-NLS-1$
						+ ", exclusive=" + exclusive + ']' //$NON-NLS-1$
						+ " at " + Tracing.now()); //$NON-NLS-1$
			}
		}
		
		boolean result = false;
		Queue.Wait node = null;
		
		synchronized (this) {
			if (!exclusive || notYielded()) {
				// check whether I am easily available
				if ((current == owner)) {
					// trivially re-acquire the lock, increasing the depth
					depth++;
					result = true;
				} else if (owner == null) {
					// first to try to get the lock
					depth = 1;
					owner = current;
					result = true;
				} else {
					// add myself to the queue of waiting threads
					node = waiting.put(timeout, exclusive);
				}
			} else if (owner == current) {
				// I can already appear to own the lock if I am the
				//    UI thread re-entering lock acquisition in processing the
				//    event queue while waiting to get the lock, and some job
				//    that is acquiring it for me higher on the stack has
				//    already transfered it to me.  In this case, I must
				//    interrupt the attempt to upgrade the read lock to a
				//    write lock.  If we don't interrupthere, then we will
				//    deadlock
				throw new InterruptedException(Messages.upgradeReadLock);
			} else {
				// add myself to the queue of waiting threads
				node = waiting.put(timeout, exclusive);
			}
		}
		
		if (node != null) {
			// must not be holding the lock's monitor when we block on the node
			node.waitFor(timeout);
			
			synchronized (this) {
				if (node.wasNotified()) {
					depth = 1;
					owner = current;
					result = true;
				}
			}
		}
		
		if (Tracing.shouldTrace(EMFTransactionDebugOptions.LOCKING)) {
			if (result) {
				Tracing.trace("::: Taken            [id=" //$NON-NLS-1$
						+ id + ", thread=" + current.getName() //$NON-NLS-1$
						+ ", depth=" + depth + ']' //$NON-NLS-1$
						+ " at " + Tracing.now()); //$NON-NLS-1$
			} else {
				Tracing.trace("::: Timed Out        [id=" //$NON-NLS-1$
						+ id + ", thread=" + current.getName() + ']' //$NON-NLS-1$
						+ " at " + Tracing.now()); //$NON-NLS-1$
			}
		}
		
		// if I successfully acquired a lock, then I cannot be yielding
		if (result) {
			resume();
		}
		
		return result;
	}

	/**
	 * Attempts to acquire me (without a timeout) in a manner that is safe to
	 * execute on the UI thread.  This ensures that, in an Eclipse UI
	 * environment, if the UI thread is blocked waiting for me, the Job Manager
	 * will show the block dialog to inform the user of what is happening.
	 * <p>
	 * If this method is called from a thread that is running as a Job, then
	 * it behaves identically to {@link #acquire()}.
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
	public void uiSafeAcquire(boolean exclusive) throws InterruptedException {
		// Only try the special acquiring procedure for Display thread and when
		// no begin rule is done on the display thread.
		boolean acquired = false;
		
		final Thread current = Thread.currentThread();
		
		final Job currentJob = jobmgr.currentJob();
		if ((currentJob != null) && (currentJob.getRule() != null)) {
			// running as a job on a scheduling rule?  No UI feedback needed.
			//    Note that when the UI is showing the Blocked dialog, it is
			//    doing so via a delayed UI job without a scheduling rule
			acquire(exclusive);
		} else {
			// if we already are interrupted, clear the interrupt status
			//     because we will be performing a timed wait that must not
			//     be interrupted.  We ignore interrupts because we know that
			//     the UI thread will often be interrupted by Display.syncExec
			Thread.interrupted();
			
			if (Tracing.shouldTrace(EMFTransactionDebugOptions.LOCKING)) {
				Tracing.trace("::: UI-Safe Acquire  [id=" //$NON-NLS-1$
						+ id + ", thread=" + current.getName() + ']' //$NON-NLS-1$
						+ " at " + Tracing.now()); //$NON-NLS-1$
			}
			
			// try acquiring it just in case we can avoid scheduling a job.
			//   Don't allow the UI thread to be interrupted during this
			//   interval
			acquired = uninterruptibleAcquire(250L, exclusive);

			if (acquired) {
				assert getOwner() == current;
				return;
			}
			
			// loop until the lock is acquired
			AcquireJob job = new AcquireJob(current, exclusive);
			while (!acquired) {
				Object sync = job.getSync();
				ISchedulingRule rule = job.getRule();
				
				synchronized (sync) {
					if (Tracing.shouldTrace(EMFTransactionDebugOptions.LOCKING)) {
						Tracing.trace("::: Scheduling       [id=" //$NON-NLS-1$
								+ id + ", thread=" + current.getName() + ']' //$NON-NLS-1$
								+ " at " + Tracing.now()); //$NON-NLS-1$
					}
					
					job.schedule();
					
					// wait for the job to tell us it's running.  Don't allow
					//   the UI thread to be interrupted during this interval
					uninterruptibleWait(sync);
				}
				
				// begin the job's rule. This ensures that if we are the
				// display thread, then the job manager shows a Blocked
				// dialog until the job finishes
				try {
					if (Tracing.shouldTrace(EMFTransactionDebugOptions.LOCKING)) {
						Tracing.trace("::: Blocking         [id=" //$NON-NLS-1$
								+ id + ", thread=" + current.getName() + ']' //$NON-NLS-1$
								+ " at " + Tracing.now()); //$NON-NLS-1$
					}
					
					jobmgr.beginRule(rule, null);
					
					if (Tracing.shouldTrace(EMFTransactionDebugOptions.LOCKING)) {
						Tracing.trace("::: Unblocked        [id=" //$NON-NLS-1$
								+ id + ", thread=" + current.getName() + ']' //$NON-NLS-1$
								+ " at " + Tracing.now()); //$NON-NLS-1$
					}
					
					if (job.getResult().getSeverity() < IStatus.WARNING) {
						synchronized (this) {
							// if the job finished with this warning, then it did
							//    not actually get the lock, but it is telling us
							//    that we already have it
							if (job.getResult() == UI_REENTERED_STATUS) {
								if (Tracing.shouldTrace(EMFTransactionDebugOptions.LOCKING)) {
									Tracing.trace("::: Lock Recursion   [id=" //$NON-NLS-1$
											+ id + ", thread=" + current.getName() + ']' //$NON-NLS-1$
											+ " at " + Tracing.now()); //$NON-NLS-1$
								}
								
								// try again quickly
								acquired = acquire(250L, exclusive);
							} else {
								acquired = getOwner() == current;
								
								if (Tracing.shouldTrace(EMFTransactionDebugOptions.LOCKING)) {
									if (acquired) {
										Tracing.trace("::: Taken            [id=" //$NON-NLS-1$
												+ id + ", thread=" + current.getName() //$NON-NLS-1$
												+ ", depth=" + depth + ']' //$NON-NLS-1$
												+ " at " + Tracing.now()); //$NON-NLS-1$
									}
								}
							}								
						}
						
						resume();
					} else if (job.getResult().getSeverity() == IStatus.CANCEL) {
						// user canceled.  Interrupt
						Thread.interrupted();
						InterruptedException exc = new InterruptedException();
						Tracing.throwing(Lock.class, "uiSafeAcquire", exc); //$NON-NLS-1$
						throw exc;
					}
				} catch (OperationCanceledException e) {
					// user canceled.  Interrupt
					Thread.interrupted();
					InterruptedException exc = new InterruptedException();
					Tracing.throwing(Lock.class, "uiSafeAcquire", exc); //$NON-NLS-1$
					throw exc;
				} finally {
					jobmgr.endRule(rule);
				}
			}
		}
		
		assert getOwner() == current;
	}

	/**
	 * Performs a timed wait, during which I ignore any attempt to interrupt.
	 * Because this method ignores interrupts, it must time out, so the
	 * <tt>timeout</tt> must be positive.
	 * 
	 * @param timeout the positive timeout, in millis
	 * @param exclusive whether the lock is to be obtained for exclusive
	 *     access
	 *     
	 * @return <code>true</code> if the lock was successfully acquired;
	 *     <code>false</code> if it timed out
	 * 
	 * @throws IllegalArgumentException if the <tt>timeout</tt> is not more
	 *     than zero
	 */
	private boolean uninterruptibleAcquire(long timeout, boolean exclusive) {
		if (timeout <= 0L) {
			IllegalArgumentException exc = new IllegalArgumentException("nonpositive timeout"); //$NON-NLS-1$
			Tracing.throwing(Lock.class, "uninterruptibleAcquire", exc); //$NON-NLS-1$
			throw exc;
		}
		
		long start = System.currentTimeMillis();
		boolean result = false;
		
		while (timeout > 0L) {
			try {
				result = acquire(timeout, exclusive);
				break;
			} catch (InterruptedException e) {
				// ignore it and clear the interrupt status
				Thread.interrupted();
				
				// see how much longer we have to wait
				long when = System.currentTimeMillis();
				timeout -= (when - start);  // how long did we just wait?
				start = when;               // restart the clock
			}
		}
		
		return result;
	}
	
	/**
	 * Waits for the specified object's monitor, ignoring any interrupt
	 * requests received in the interim.
	 * 
	 * @param o the object to wait for without interruption
	 */
	private static void uninterruptibleWait(Object o) {
		synchronized (o) {
			for (;;) {
				try {
					o.wait();
					break;
				} catch (InterruptedException e) {
					// ignore it and clear the interrupt status
					Thread.interrupted();
				}
			}
		}
	}
	
	/**
	 * Releases me.  Note that my depth may still be positive, in which case
	 * I would need to be released again (recursively).
	 * 
	 * @throws IllegalStateException if the calling thread does not own me
	 */
	public synchronized void release() {
		if (Tracing.shouldTrace(EMFTransactionDebugOptions.LOCKING)) {
			Tracing.trace("::: Release          [id=" //$NON-NLS-1$
					+ id + ", thread=" + Thread.currentThread().getName() //$NON-NLS-1$
					+ ", depth=" + (depth - 1) + ']' //$NON-NLS-1$
					+ " at " + Tracing.now()); //$NON-NLS-1$
			
		}
		
		if (Thread.currentThread() != owner) {
			IllegalArgumentException exc = new IllegalArgumentException("Lock not owned by current thread"); //$NON-NLS-1$
			Tracing.throwing(Lock.class, "release", exc); //$NON-NLS-1$
			throw exc;
		}
		
		depth--;

		if (depth == 0) {
			// wake up next thread that wants this lock
			
			boolean allowExclusive = notYielded();
			for (;;) {
				Queue.Wait node = waiting.take(allowExclusive);
				
				if (node == null) {
					owner = null;
					// nobody left to wake up
					break;
				} else if (node.wakeUp()) {
					// this will be the new owner
					owner = node.getThread();
					break;
				} else {
					// just loop around again to look for another candidate
					//    because this one had timed out
				}
			}
		}
	}
	
	/**
	 * Queries whether there are no threads currently yielding me.
	 * 
	 * @return <code>false</code> if any thread is in my yielders set;
	 *    <code>true</code>, otherwise
	 */
	private boolean notYielded() {
		return yielders.isEmpty();
	}
	
	/**
	 * Temporarily yields the lock to another thread that does not require
	 * exclusive access, if any such thread exists.  Note that, if this method
	 * returns <code>true</code>, then the caller must actually {@link release}
	 * me before another thread can take me.  It then resumes by acquiring me
	 * again, layer.
	 * 
	 * @return <code>true</code> if the lock was successfully yielded to another
	 *     thread; <code>false</code>, otherwise
	 */
	public synchronized boolean yield() {
		boolean result = waiting.size() > waiting.exclusiveCount();
		
		if (result) {
			// do not yield if no other non-exclusive threads are waiting for access
			yielders.put(Thread.currentThread(), this);
		}
		
		if (result && Tracing.shouldTrace(EMFTransactionDebugOptions.LOCKING)) {
			Tracing.trace("::: Yielding         [id=" + id //$NON-NLS-1$
					+ ", thread=" + Thread.currentThread().getName() + ']' //$NON-NLS-1$
					+ " at " + Tracing.now()); //$NON-NLS-1$
		}
		
		return result;
	}
	
	/**
	 * Resumes the current thread from a yield, after it has acquired me.
	 */
	private void resume() {
		boolean removed = yielders.remove(Thread.currentThread()) == this;
		
		if (removed) {
			if (Tracing.shouldTrace(EMFTransactionDebugOptions.LOCKING)) {
				Tracing.trace("::: Resuming         [id=" + id //$NON-NLS-1$
						+ ", thread=" + Thread.currentThread().getName() + ']' //$NON-NLS-1$
						+ " at " + Tracing.now()); //$NON-NLS-1$
			}
		}		
	}
	
	/**
	 * Transfers ownership of me to the specified <code>thread</code>.
	 * <p>
	 * <b>Note</b> that this should only be called by the {@link AcquireJob}
	 * after it has acquired me, to transfer me to the thread that scheduled
	 * it.
	 * </p>
	 * 
	 * @param thread my new owner
	 */
	synchronized void transfer(Thread thread) {
		if (Tracing.shouldTrace(EMFTransactionDebugOptions.LOCKING)) {
			Tracing.trace("::: Transfer         [id=" //$NON-NLS-1$
					+ id + ", src=" + Thread.currentThread().getName() //$NON-NLS-1$
					+ ", dst=" + thread.getName() + ']' //$NON-NLS-1$
					+ " at " + Tracing.now()); //$NON-NLS-1$
		}
		
		owner = thread;
	}
	
	public String toString() {
		Thread lastKnownOwner = owner;
		
		return "Lock[id=" + id + ", depth=" + depth //$NON-NLS-1$ //$NON-NLS-2$
			+ ", owner=" + ((lastKnownOwner == null) ? null : lastKnownOwner.getName()) //$NON-NLS-1$
			+ ", waiting=" + waiting + ']'; //$NON-NLS-1$
	}
	
	/**
	 * A job that does the work of acquiring the lock.  We use jobs because
	 * the Eclipse UI detects when the UI thread is blocked on a job, and
	 * automatically provides feed-back via a dialog to inform the user of the
	 * blocking task.
	 *
	 * @author Christian W. Damus (cdamus)
	 */
	class AcquireJob extends Job {
		private final Object sync = new Object();
		private final Thread thread;
		private final boolean exclusive;
		
		private AcquireRule rule = new AcquireRule();

		AcquireJob(Thread schedulingThread, boolean exclusive) {
			super(Messages.acquireJobLabel);
			
			this.thread = schedulingThread;
			this.exclusive = exclusive;
			
			setSystem(true);
			setRule(rule);
		}

		/**
		 * @see org.eclipse.core.runtime.jobs.Job#run(org.eclipse.core.runtime.IProgressMonitor)
		 */
		protected IStatus run(IProgressMonitor monitor) {
			// notify the lock that scheduled me that I am now running
			synchronized (sync) {
				sync.notify();
			}

			// attempt to acquire the lock.  Time out so that we may check
			//    regularly for user cancellation
			try {
				while (!acquire(250L, exclusive)) {
					synchronized (Lock.this) {
						// the UI thread can re-enter the uiSafeAcquire() method
						//    and schedule additional AcquireJobs because it
						//    continues to process events and synchronous runnables
						//    on the event queue.  When this occurs, we will see
						//    that the thread for which we are waiting to get the
						//    lock already has it, so we stop waiting.  Not only
						//    that, but because the UI thread will think that
						//    we have acquired the lock, we must increase the
						//    depth
						if (Lock.this.getOwner() == thread) {
							return UI_REENTERED_STATUS;
						}
					}
					
					if (monitor.isCanceled()) {
						return Status.CANCEL_STATUS;
					}
				}
			} catch (InterruptedException e) {
				// I was interrupted:  give up
				Thread.interrupted();  // clear interrupt flag
				return Status.CANCEL_STATUS;
			}

			// now transfer it to the thread that scheduled me
			Lock.this.transfer(thread);
			
			return Status.OK_STATUS;
		}

		/**
		 * The symchronization object for this job.  The thread that scheduled
		 * me must do so while it holds this monitor, and then must wait for me
		 * to notify it before proceeding.
		 * 
		 * @return the synchronization object to wait on
		 */
		public final Object getSync() {
			return sync;
		}
	}
	
	/**
	 * Scheduling rule used to block the UI thread on the {@link AcquireJob}
	 * that is acquiring a lock on its behalf.
	 *
	 * @author Christian W. Damus (cdamus)
	 */
	static class AcquireRule implements ISchedulingRule {
		AcquireRule() {
			super();
		}
		
		// Documentation copied from the interface
		public boolean contains(ISchedulingRule rule) {
			// containment must be reflexive
			return rule == this;
		}

		// Documentation copied from the interface
		public boolean isConflicting(ISchedulingRule rule) {
			// conflict must be reflexive
			return rule == this;
		}
	}
}
