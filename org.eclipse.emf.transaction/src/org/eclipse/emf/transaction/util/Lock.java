/**
 * <copyright>
 *
 * Copyright (c) 2005, 2009 IBM Corporation, Zeligsoft Inc., and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *   Zeligsoft - Bugs 248717, 262175
 *
 * </copyright>
 *
 * $Id: Lock.java,v 1.15 2009/08/11 11:21:08 bgruschko Exp $
 */
package org.eclipse.emf.transaction.util;

import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.ILock;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.emf.transaction.internal.EMFTransactionDebugOptions;
import org.eclipse.emf.transaction.internal.EMFTransactionPlugin;
import org.eclipse.emf.transaction.internal.ITransactionLock;
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
public class Lock implements ITransactionLock {
	private static final IJobManager jobmgr = Job.getJobManager();
	
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
	private final Map<Thread, Lock> yielders =
		new java.util.IdentityHashMap<Thread, Lock>();
	
	// every thread has its own ILock that it acquires while it owns the
	//    transaction lock, to ensure that the thread is registered as a
	//    "lock owner" in the lock table.  This ensures that Display.syncExec()
	//    calls from these threads will use the work queue to communicate
	//    runnables to a waiting UI thread
	private final ThreadLocal<ILock> threadLock = new ThreadLocal<ILock>() {
		@Override
		protected ILock initialValue() {
			return Job.getJobManager().newLock();
		}};
		
	/**
	 * Initializes me.
	 */
	public Lock() {
		synchronized (Lock.class) {
			this.id = ++nextId;
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.emf.transaction.util.ITransactionLock#getOwner()
	 */
	public Thread getOwner() {
		// note that there is no need to synchronize this method because, if
		//   calling thread is currently the owner, it cannot cease to be while
		//   invoking this method.  Likewise, if it is not the owner, it cannot
		//   become the owner while invoking this method
		return owner;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.emf.transaction.util.ITransactionLock#getDepth()
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

	/* (non-Javadoc)
	 * @see org.eclipse.emf.transaction.util.ITransactionLock#acquire(boolean)
	 */
	public void acquire(boolean exclusive) throws InterruptedException {
		acquire(0L, exclusive);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.emf.transaction.util.ITransactionLock#acquire(long, boolean)
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
					
					// ensure that the current thread has an ILock for Display.syncExec() safety
					getThreadLock().acquire();
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

	/* (non-Javadoc)
	 * @see org.eclipse.emf.transaction.util.ITransactionLock#uiSafeAcquire(boolean)
	 */
	public void uiSafeAcquire(boolean exclusive) throws InterruptedException {
        // Only try the special acquiring procedure for Display thread and when
        // no begin rule is done on the display thread.
        boolean acquired = false;

        final Thread current = Thread.currentThread();

        final Job currentJob = jobmgr.currentJob();
        final ISchedulingRule jobRule;
        if (currentJob != null) {
            // running as a job? Cannot use JobManager.beginRule() to show the
        	// "UI blocked" dialog
            jobRule = null;
        } else {
            jobRule = new AcquireRule();
        }

        // if we already are interrupted, clear the interrupt status
        // because we will be performing a timed wait that must not
        // be interrupted. We ignore interrupts because we know that
        // the UI thread will often be interrupted by Display.syncExec
        Thread.interrupted();

        if (Tracing.shouldTrace(EMFTransactionDebugOptions.LOCKING)) {
            Tracing.trace("::: UI-Safe Acquire  [id=" //$NON-NLS-1$
                + id + ", thread=" + current.getName() + ']' //$NON-NLS-1$
                + " at " + Tracing.now()); //$NON-NLS-1$
        }

        // try acquiring it just in case we can avoid scheduling a job.
        // Don't allow the UI thread to be interrupted during this interval
        acquired = uninterruptibleAcquire(250L, exclusive);

        if (acquired) {
            assert getOwner() == current;
            return;
        }

        // loop until the lock is acquired
        AcquireJob job = new AcquireJob(current, exclusive);
        job.setRule(jobRule);
        while (!acquired) {
            Object sync = job.getSync();
            ILock jobLock = job.getILock();

            synchronized (sync) {
                if (Tracing.shouldTrace(EMFTransactionDebugOptions.LOCKING)) {
                    Tracing.trace("::: Scheduling       [id=" //$NON-NLS-1$
                        + id + ", thread=" + current.getName() + ']' //$NON-NLS-1$
                        + " at " + Tracing.now()); //$NON-NLS-1$
                }

                job.schedule();
                
                if (Job.getJobManager().isSuspended()) {
					// the Job Manager is suspended. We cannot use a job to
					// acquire.
					// We check only after scheduling the job because Eclipse
					// ensures that all pending jobs run before the JobManager
					// actually halts. Thus, we have a chance to abort the job,
					// in case the job manager was already suspended before we
					// scheduled it.
                	job.abort();
                	
					// If the Job Manager is suspended, then under normal
					// Eclipse circumstances, this is not the UI thread, anyway
                	acquire(exclusive);
                	return;
                }

                // wait for the job to tell us it's running. Don't allow
                // the UI thread to be interrupted during this interval
                uninterruptibleWait(sync);
            }

            // wait for the job's ILock. This ensures that if we are the
            // display thread, then the job manager shows a Blocked
            // dialog until the job finishes
            try {
                if (Tracing.shouldTrace(EMFTransactionDebugOptions.LOCKING)) {
                    Tracing.trace("::: Blocking         [id=" //$NON-NLS-1$
                        + id + ", thread=" + current.getName() + ']' //$NON-NLS-1$
                        + " at " + Tracing.now()); //$NON-NLS-1$
                }

                if (jobRule == null) {
                    // can only wait on the lock, then
                    jobLock.acquire();
                } else {
                    jobmgr.beginRule(jobRule, null);
                }

                if (Tracing.shouldTrace(EMFTransactionDebugOptions.LOCKING)) {
                    Tracing.trace("::: Unblocked        [id=" //$NON-NLS-1$
                        + id + ", thread=" + current.getName() + ']' //$NON-NLS-1$
                        + " at " + Tracing.now()); //$NON-NLS-1$
                }

                IStatus jobStatus = job.getAcquireStatus();

                if (jobStatus == null) {
                    // job didn't finish: we were broken out of a deadlock.
                    throw new InterruptedException(
                        "Interrupted because a deadlock was detected"); //$NON-NLS-1$
                }

                if (jobStatus.getSeverity() < IStatus.WARNING) {
                    synchronized (this) {
                        // if the job finished with this warning, then it did
                        // not actually get the lock, but it is telling us
                        // that we already have it
                        if (jobStatus == UI_REENTERED_STATUS) {
                            if (Tracing.shouldTrace(EMFTransactionDebugOptions.LOCKING)) {
                                Tracing.trace("::: Lock Recursion   [id=" //$NON-NLS-1$
                                    + id
                                    + ", thread=" + current.getName() + ']' //$NON-NLS-1$
                                    + " at " + Tracing.now()); //$NON-NLS-1$
                            }

                            // try again quickly
                            acquired = acquire(250L, exclusive);
                        } else {
                            acquired = getOwner() == current;

                            // need to acquire this because we will release it, later
                            getThreadLock().acquire();

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
                } else if (jobStatus.getSeverity() == IStatus.CANCEL) {
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
            } catch (IllegalArgumentException e) {
                // most likely cause is that another thread concurrently transferred
                // a scheduling rule to the current thread, and subsequently our
                // beginRule() failed.  Should be a CoreException.  Communicate
                // failure to lock via InterruptedException because, basically,
                // its wait for the lock was interrupted
                Tracing.catching(Lock.class, "uiSafeAcquire", e); //$NON-NLS-1$
                InterruptedException exc = (e.getLocalizedMessage() != null) ? new InterruptedException(
                    e.getLocalizedMessage())
                    : new InterruptedException();
                Tracing.throwing(Lock.class, "uiSafeAcquire", exc); //$NON-NLS-1$
                throw exc;
            } finally {
                synchronized (sync) {
                    if (!acquired && !job.abort()) {
                        // failed to wait for the job to get the lock but the
                        // job has already transferred the lock, so release
                        release();
                    }
                }
                
                if (jobRule == null) {
                    jobLock.release();
                } else {
                    jobmgr.endRule(jobRule);
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
	
	/* (non-Javadoc)
	 * @see org.eclipse.emf.transaction.util.ITransactionLock#release()
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
			// no longer need this thread's dummy ILock
			getThreadLock().release();
			
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
	
	/* (non-Javadoc)
	 * @see org.eclipse.emf.transaction.util.ITransactionLock#yield()
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
	 * Transfers ownership of me to the specified <code>thread</code>, if I am
	 * currently owned.  Otherwise, does nothing.
	 * <p>
	 * <b>Note</b> that this should only be called by the {@link AcquireJob}
	 * after it has acquired me, to transfer me to the thread that scheduled
	 * it, or by a privileged runnable to lend the lock from its current owner
	 * to another thread.
	 * </p>
	 * 
	 * @param thread my new owner (must not be null)
	 */
	synchronized void transfer(Thread thread) {
		if (thread == null) {
			throw new IllegalArgumentException("thread is null"); //$NON-NLS-1$
		}
		
		if (owner != null) {
			final Thread current = Thread.currentThread();
			
		if (Tracing.shouldTrace(EMFTransactionDebugOptions.LOCKING)) {
			Tracing.trace("::: Transfer         [id=" //$NON-NLS-1$
						+ id + ", src=" + current.getName() //$NON-NLS-1$
					+ ", dst=" + thread.getName() + ']' //$NON-NLS-1$
					+ " at " + Tracing.now()); //$NON-NLS-1$
		}
		
			if (current == thread) {
				// ensure that the new owner has an ILock for Display.syncExec() safety
				getThreadLock().acquire();
			} else if (current == owner) {
				// current thread no longer needs the dummy lock
				getThreadLock().release();
			} // else non-owner is transferring.  Shouldn't happen
			
		owner = thread;
	}
	}
	
	/**
	 * Obtains the thread-private <tt>ILock</tt> for the current thread.
	 * 
	 * @return the current thread's thread lock
	 */
	private ILock getThreadLock() {
		return threadLock.get();
	}
	
	@Override
	public String toString() {
		Thread lastKnownOwner = owner;
		
		return "Lock[id=" + id + ", depth=" + depth //$NON-NLS-1$ //$NON-NLS-2$
			+ ", owner=" + ((lastKnownOwner == null) ? null : lastKnownOwner.getName()) //$NON-NLS-1$
			+ ", waiting=" + waiting + ']'; //$NON-NLS-1$
	}
	
	/**
	 * @since 1.4
	 */
	public void checkedTransfer(Thread thread) {
		new Access() {}.transfer(thread);
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

        private final ILock ilock = jobmgr.newLock();
        private IStatus acquireStatus;
        
        private boolean aborted;
        private boolean transferred;

        AcquireJob(Thread schedulingThread, boolean exclusive) {
            super(Messages.acquireJobLabel);

            this.thread = schedulingThread;
            this.exclusive = exclusive;

            setSystem(true);
        }

        /**
         * @see org.eclipse.core.runtime.jobs.Job#run(org.eclipse.core.runtime.IProgressMonitor)
         */
        @Override
		protected IStatus run(IProgressMonitor monitor) {
            try {
                ilock.acquire();
                
                // notify the lock that scheduled me that I am now running
                synchronized (sync) {
                    sync.notifyAll();
                }

                // attempt to acquire the lock. Time out so that we may check
                // regularly for user cancellation
                try {
                    while (!acquire(250L, exclusive)) {
                        synchronized (Lock.this) {
                            // the UI thread can re-enter the uiSafeAcquire() method
                            // and schedule additional AcquireJobs because it
                            // continues to process events and synchronous runnables
                            // on the event queue. When this occurs, we will see
                            // that the thread for which we are waiting to get the
                            // lock already has it, so we stop waiting. Not only
                            // that, but because the UI thread will think that
                            // we have acquired the lock, we must increase the depth
                            if (Lock.this.getOwner() == thread) {
                                acquireStatus = UI_REENTERED_STATUS;
                                return acquireStatus;
                            }
                        }

                        synchronized (sync) {
                            if (aborted || monitor.isCanceled()) {
                                acquireStatus = Status.CANCEL_STATUS;
                                return acquireStatus;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    // I was interrupted: give up
                    Thread.interrupted(); // clear interrupt flag
                    acquireStatus = Status.CANCEL_STATUS;
                    return acquireStatus;
                }

                // now transfer it to the thread that scheduled me
                synchronized (sync) {
                    if (aborted) {
                        // abort the lock acquisition (thread failed to block)
                        Lock.this.release();
                        acquireStatus = Status.CANCEL_STATUS;
                    } else {
                        Lock.this.transfer(thread);
                        transferred = true;
                        acquireStatus = Status.OK_STATUS;
                    }
                }
            } finally {
                ilock.release();
            }

            return acquireStatus;
        }

        /**
         * The synchronization object for this job.  The thread that scheduled
         * me must do so while it holds this monitor, and then must wait for me
         * to notify it before proceeding.
         * 
         * @return the synchronization object to wait on
         */
        public final Object getSync() {
            return sync;
        }

        ILock getILock() {
            return ilock;
        }

        /**
         * A status indicating success or problem in acquisition of the lock.
         * 
         * @return my status
         */
        IStatus getAcquireStatus() {
            return acquireStatus;
        }
        
        /**
         * Aborts my attempt to acquire a lock on my originating thread's behalf.
         * If I have already acquired the lock, I will release it.  If I haven't
         * yet acquired it, I will give up waiting.  Otherwise, I have already
         * transferred it to my originating thread, which will have to take
         * responsibility for releasing it.
         * 
         * @return <code>true</code> on successful abort; <code>false</code>
         *    if I have already acquired the lock and transferred it to my
         *    originating thread (in which case, it will have to release the
         *    lock) 
         */
        boolean abort() {
            boolean result;
            
            synchronized (sync) {
                result = !transferred;
                transferred = false;  // avoid re-release
                aborted = true;
                
                if (Tracing.shouldTrace(EMFTransactionDebugOptions.LOCKING)) {
                    Tracing.trace("::: Aborted          [id=" //$NON-NLS-1$
                        + id + ", thread=" + Thread.currentThread().getName() //$NON-NLS-1$
                        + ", for=" + thread.getName() + ']' //$NON-NLS-1$
                        + " at " + Tracing.now()); //$NON-NLS-1$
                }
            }
            
            return result;
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
	
	/**
	 * A class that grants special {@link Lock} manipulation privileges to its
	 * subclasses, that it knows as particular friends.
	 * <p>
	 * <b>Note</b> that this class is intended for use within the EMF Transaction API
	 * only.  It may not be extended by clients.
	 * </p>
	 * 
	 * @author Christian W. Damus (cdamus)
	 * 
	 * @since 1.0.2
	 */
	public abstract class Access {
		/**
		 * Initializes me.
		 */
		protected Access() {
			checkSubclass();
		}
		
		/**
		 * Transfers the lock's ownership to the specified thread.
		 * 
		 * @param thread the new owner thread
		 */
		public void transfer(Thread thread) {
			Lock.this. transfer(thread);
		}
		
		private void checkSubclass() {
			String name = getClass().getName();
			String packageName = name.substring(0, name.lastIndexOf('.') + 1);
			
			if ( !packageName.startsWith("org.eclipse.emf.transaction")) { //$NON-NLS-1$
				throw new IllegalArgumentException("Illegal subclass"); //$NON-NLS-1$
				
			}
		}
	}
}
