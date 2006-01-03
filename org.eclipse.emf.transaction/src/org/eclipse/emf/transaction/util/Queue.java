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
 * $Id: Queue.java,v 1.1 2006/01/03 20:41:55 cdamus Exp $
 */
package org.eclipse.emf.transaction.util;

import org.eclipse.emf.transaction.internal.Tracing;


/**
 * Simplistic implementation of a FIFO wait queue for fair notification of
 * threads that are waiting for a lock.
 *
 * @author Christian W. Damus (cdamus)
 */
final class Queue {
	private Wait head;
	private Wait tail;
	private int size;
	private int exclusiveCount;  // number of threads waiting for exclusive access
	
	/**
	 * Initializes me.
	 */
	public Queue() {
		super();
	}
	
	/**
	 * Queries whether the queue is empty of threads.
	 * 
	 * @return <code>true</code> if I have no threads; <code>false</code>, otherwise
	 */
	public synchronized boolean isEmpty() {
		return size == 0;
	}
	
	/**
	 * Queries the number of waiting threads.
	 * 
	 * @return my size
	 */
	public synchronized int size() {
		return size;
	}
	
	/**
	 * Queries the number of threads waiting for exclusive access.
	 * 
	 * @return my count of exclusive threads
	 */
	public synchronized int exclusiveCount() {
		return exclusiveCount;
	}
	
	/**
	 * Adds the current thread to the queue, blocking until either:
	 * <ul>
	 *    <li>the thread is awakened again by being dequeued</li>
	 *    <li>the thread times out (if a time-out is specified)</li>
	 *    <li>the thread is interrupted while it is waiting</li>
	 * </ul>
	 * 
	 * @param timeout the time-out interval, in millis, or <code>0L</code>
	 *     if no time-out is desired (i.e., wait as long as necessary)
	 * @param exclusive <code>true</code> if the current thread needs exclusive
	 *     access (i.e., no other threads may currently be
	 *     {@link #yield() yielding}); <code>false</code>, otherwise
	 *     
	 * @return the new wait node
	 *    
	 * @throws InterruptedException if the thread was interrupted while
	 *    waiting for notification.  If it is interrupted after it has been
	 *    notified but before it awakes, no exception is thrown
	 */
	public synchronized Wait put(long timeout, boolean exclusive) throws InterruptedException {
		final Thread current = Thread.currentThread();
		Wait result;
		
		// first, see whether we can find an existing node that timed out, but
		//    that we have not yet dequeued.  If found, just re-use it to
		//    to preserve seniority.  This allows threads to loop with short
		//    time-outs for the sake of liveness (e.g., to check for cancellation
		//    of progress monitors)
		result = findNode(current);
		
		if (result == null) {
			// must enqueue a new node
			result = new Wait(current);
		
			if (tail == null) {
				tail = result;
				head = tail;
			} else {
				tail.next = result;
				tail = result;
			}			
			
			size++;
			
			if (exclusive) {
				exclusiveCount++;
			}
		}
		
		result.initialize(exclusive);
		
		return result;
	}
	
	/**
	 * Dequeues the next eligible (not timed-out or interrupted)
	 * thread.  If any threads are currently yielding, then we look for the
	 * the next eligible non-exclusive thread.
	 * 
	 * @param allowExclusive whether to allow dequeueing of threads waiting
	 *     for exclusive access
	 * 
	 * @return the next eligible thread, or <code>null</code> if
	 *     no threads were waiting or they all timed out
	 */
	public synchronized Wait take(boolean allowExclusive) {
		Wait result = null;
		
		Wait prev = null;
		for (Wait node = head; (result == null) && (node != null); node = node.next) {
			if (allowExclusive || !node.isExclusive()) {
				// this is a candidate.  Remove it from the list
				if (prev != null) {
					prev.next = node.next;
				} else {
					head = node.next;
				}
				
				if (head == null) {
					tail = null;
				} else if (node == tail) {
					tail = prev;
				}
				
				// help the garbage collector
				node.next = null;
				
				size--;
				
				if (node.isExclusive()) {
					exclusiveCount--;
				}
				
				result = node;
			} else {
				prev = node;
			}
		}
		
		return result;
	}
	
	/**
	 * Finds an existing node for the specified <code>thread</code>, to be
	 * re-used for another wait following a time-out.  This ensures the
	 * preservation of ordering for threads that time-out periodically but
	 * with the intention of waiting again each time.
	 * 
	 * @param thread a thread to look for in the queue
	 * 
	 * @return the thread's node, or <code>null</code> if it is not found
	 */
	private Wait findNode(Thread thread) {
		Wait result = null;
		
		for (Wait next = head; next != null; next = next.next) {
			if (next.thread == thread) {
				result = next;
				break;
			}
		}
		
		return result;
	}
	
	public String toString() {
		StringBuffer result = new StringBuffer();
		boolean first = true;
		
		result.append("Queue["); //$NON-NLS-1$
		for (Wait next = head; head != null; next = next.next) {
			if (first) {
				first = false;
			} else {
				result.append(", "); //$NON-NLS-1$
			}
			
			result.append(next);
		}
		
		result.append(']');
		
		return result.toString();
	}
	
	/**
	 * Implementation of a linked node in the wait queue. 
	 *
	 * @author Christian W. Damus (cdamus)
	 */
	static class Wait {
		Wait next;
		private final Thread thread;
		private boolean exclusive;
		private boolean notified;
		private boolean timedOut;
		
		/**
		 * Initializes me with the thread that is waiting on me.
		 * 
		 * @param thread my thread
		 */
		Wait(Thread thread) {
			this.thread = thread;
		}
		
		/**
		 * Queries whether my thread was successfully notified.
		 * 
		 * @return whether my thread was notified
		 */
		boolean wasNotified() {
			return notified && !timedOut;
		}
		
		/**
		 * Queries whether I require exclusive scheduling.
		 * 
		 * @return whether I am exclusive
		 */
		boolean isExclusive() {
			return exclusive;
		}
		
		/**
		 * Initializes my state.
		 * 
		 * @param excl <code>true</code> if I should not be dequeue while
		 *    there are any yielders; <code>false</code>, otherwise
		 */
		void initialize(boolean excl) {
			this.exclusive = excl;
			timedOut = false;
			notified = false;
		}
		
		/**
		 * Retrieves that thread that is/was waiting on me.
		 * 
		 * @return my thread
		 */
		Thread getThread() {
			return thread;
		}
		
		/**
		 * Attempts to wake my thread.  Wake-up can be attempted once only
		 * upon dequeueing the thread.  It will succeed only if the thread
		 * did not already time-out or interrupt.
		 * 
		 * @return <code>true</code> if my thread was awakened;
		 *    <code>false</code> if it had already been awakened or timed out,
		 *    or if it had been interrupted
		 */
		synchronized boolean wakeUp() {
			boolean result = false;
			
			if (!(notified || timedOut)) {
				notified = true;
				result = true;
				notify();  // wake me up!
			}
			
			return result;
		}
		
		/**
		 * Waits for the specified time-out.
		 * 
		 * @param timeout the time-out, in millis, or <code>0L</code> to wait
		 *    indefinitely
		 *    
		 * @throws InterruptedException if the waiting thread was interrupted
		 */
		synchronized void waitFor(long timeout) throws InterruptedException {
			// first, check whether perhaps another thread has already awakened
			//   me *after* I was put on the queue but *before* calling this
			//   method (as there is an unsynchronized gap in the
			//   Lock.acquire() method)
			if (notified) {
				return;
			}
			
			long waitTime = timeout;
			long start = System.currentTimeMillis();
			
			try {
				for (;;) {
					wait(waitTime);
					
					if (notified) {
						break;
					}
					
					if (timeout > 0L) {
						waitTime = timeout - (System.currentTimeMillis() - start);
						
						if (waitTime <= 0L) {
							// giving up waiting
							timedOut = true;
							break;
						}
					}
				}
			} catch (InterruptedException e) {
				Tracing.catching(Wait.class, "waitFor", e); //$NON-NLS-1$
				if (!notified) {
					// thread was interrupted while it was waiting.
					//   Pretend like we timed out (just in case), but
					//   propagate the exception
					timedOut = true;
					Tracing.throwing(Wait.class, "waitFor", e); //$NON-NLS-1$
					throw e;
				} else {
					// the thread was interrupted after notification, but
					//    before it woke up from the wait().  Must propagate
					//    the interrupt status but not fail this operation
					Thread.currentThread().interrupt();
				}
			}
		}
		
		public String toString() {
			return thread.getName() + "[" + notified + ", " + timedOut + ']'; //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
}
