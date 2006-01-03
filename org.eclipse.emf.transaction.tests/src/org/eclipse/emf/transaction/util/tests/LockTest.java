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
 * $Id: LockTest.java,v 1.1 2006/01/03 20:51:13 cdamus Exp $
 */
package org.eclipse.emf.transaction.util.tests;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.emf.transaction.tests.AbstractTest;
import org.eclipse.emf.transaction.tests.fixtures.JobListener;
import org.eclipse.emf.transaction.util.Lock;
import org.eclipse.ui.PlatformUI;

/**
 * Tests the {@link Lock} class.
 *
 * @author Christian W. Damus (cdamus)
 */
public class LockTest extends TestCase {
	
	private Lock lock;
	private Object monitor;
	private volatile boolean interrupted;
	
	public LockTest(String name) {
		super(name);
	}

	public static Test suite() {
		return new TestSuite(LockTest.class, "Transaction Lock Tests"); //$NON-NLS-1$
	}

	/**
	 * Tests that the depth of an unacquired lock is zero.
	 */
	public void test_depth() {
		assertNull(lock.getOwner());
		assertEquals(0, lock.getDepth());
	}
	
	/**
	 * Tests that a thread can acquire and release the lock.
	 */
	public void test_acquire() {
		try {
			lock.acquire(false);
			lock.acquire(false);
			lock.release();
			lock.release();
		} catch (Exception e) {
			fail(e);
		}
	}
	
	/**
	 * Tests that a thread attempting to acquire will wait for a thread
	 * that owns the lock to release it.
	 */
	public void test_waitForAcquire() {
		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					synchronized (monitor) {
						lock.acquire(false);
						
						// wake up the main thread so that it will try to acquire
						monitor.notifyAll();
					}
					
					Thread.sleep(1000);
				} catch (Exception e) {
					fail();
				} finally {
					if (lock != null) {
						lock.release();
					}
				}
			}});
		
		try {
			long start = System.currentTimeMillis();
			
			synchronized (monitor) {
				t.start();
				
				// wait for the other thread to acquire the lock
				monitor.wait();
			}
			
			// now attempt to acquire the lock while the other thread sleeps
			lock.acquire(false);
			
			// check that we did actually wait for the lock  :-)
			assertTrue(System.currentTimeMillis() - start >= 1000);
			
			lock.release();
		} catch (Exception e) {
			fail(e);
		}
	}
	
	/**
	 * Tests the timeout capability of acquiring.
	 */
	public void test_waitForAcquire_timeout() {
		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					synchronized (monitor) {
						lock.acquire(false);
						
						// wake up the main thread so that it will try to acquire
						monitor.notifyAll();
					}
					
					Thread.sleep(5000);
				} catch (Exception e) {
					fail();
				} finally {
					if (lock != null) {
						// will be cleared already by tearDown()
						lock.release();
					}
				}
			}});
		t.setDaemon(true);
		
		try {
			synchronized (monitor) {
				t.start();
				
				// wait for the other thread to acquire the lock
				monitor.wait();
			}
			
			// now attempt to acquire the lock with a timeout.  Should give up
			assertFalse(lock.acquire(1000, false));
			
			// we did not get it
			assertEquals(0, lock.getDepth());
		} catch (Exception e) {
			fail(e);
		}
	}
	
	/**
	 * Tests that when the UI thread attempts to acquire, liveness is maintained
	 * as the UI thread continues to process sync runnables.
	 */
	public void test_uiSafeWaitForAcquire() {
		final int longInterval = PlatformUI.getWorkbench().getProgressService()
				.getLongOperationTime();
		
		final boolean syncRunnableFinished[] = new boolean[1];
		
		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					synchronized (monitor) {
						lock.acquire(false);
						
						// wake up the main thread so that it will try to acquire
						monitor.notifyAll();
					}
					
					Thread.sleep(longInterval);
					
					PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
						public void run() {
							syncRunnableFinished[0] = true;
						}});
					
					Thread.sleep(longInterval);
				} catch (Exception e) {
					fail();
				} finally {
					if (lock != null) {
						lock.release();
					}
				}
			}});
		
		try {
			long start = System.currentTimeMillis();
			
			synchronized (monitor) {
				t.start();
				
				// wait for the other thread to acquire the lock
				monitor.wait();
			}
			
			// now attempt to acquire the lock while the other thread sleeps
			lock.uiSafeAcquire(false);
			
			// check that we did actually wait for the lock  :-)
			assertTrue(System.currentTimeMillis() - start >= longInterval);
			
			// check that the UI processed the synchronous Runnable
			assertTrue(syncRunnableFinished[0]);
			
			lock.release();
		} catch (Exception e) {
			fail(e);
		}
	}
	
	/**
	 * Tests that a non-exclusive thread can yield to another non-exclusive
	 * thread.
	 */
	public void test_yield() {
		final boolean token[] = new boolean[1];
		
		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					synchronized (monitor) {
						// wake up the main thread so that it will try to acquire
						monitor.notifyAll();
					}
					
					lock.acquire(false);
					
					synchronized(monitor) {
						token[0] = true;
						monitor.notify();
					}
				} catch (Exception e) {
					fail();
				} finally {
					if (lock != null) {
						lock.release();
					}
				}
			}});
		
		try {
			lock.acquire(false);
			
			synchronized (monitor) {
				t.start();
				
				// wait for the other thread to acquire the lock
				monitor.wait();
			}
			
			Thread.sleep(500);
			
			// now yield to the other thread
			assertTrue(lock.yield());
			
			synchronized (monitor) {
				lock.release();
				
				// wait for the other thread to set the token
				monitor.wait();
				assertTrue(token[0]);
			}
			
			// now attempt to re-acquire the lock
			lock.acquire(false);
			lock.release();
		} catch (Exception e) {
			fail(e);
		}
	}
	
	/**
	 * Tests that a non-exclusive thread cannot yield to an exclusive thread.
	 */
	public void test_yieldExclusion() {
		final boolean token[] = new boolean[1];
		
		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					synchronized (monitor) {
						// wake up the main thread so that it will try to acquire
						monitor.notifyAll();
					}
					
					lock.acquire(true);  // exclusive
					
					synchronized(monitor) {
						token[0] = true;
						monitor.notify();
					}
				} catch (Exception e) {
					fail();
				} finally {
					if (lock != null) {
						lock.release();
					}
				}
			}});
		
		try {
			lock.acquire(false);
			
			synchronized (monitor) {
				t.start();
				
				// wait for the other thread to acquire the lock
				monitor.wait();
			}
			
			Thread.sleep(500);
			
			// now attempt to yield to the other thread
			assertFalse(lock.yield());
			
			synchronized (monitor) {
				lock.release();
				
				// now, we're done.  The other thread can proceed
				monitor.wait();
				assertTrue(token[0]);
			}
		} catch (Exception e) {
			fail(e);
		}
	}
	
	/**
	 * Tests that a thread cannot yield when no other threads are waiting.
	 */
	public void test_yield_noneWaiting() {
		try {
			lock.acquire(false);
			
			assertFalse(lock.yield());
			
			lock.release();
		} catch (Exception e) {
			fail(e);
		}
	}
	
	/**
	 * Tests for correct propagation of thread interrupt from the acquire()
	 * method.
	 */
	public void test_interrupt_acquire() {
		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					synchronized (monitor) {
						// wake up the main thread so that it will try to acquire
						monitor.notifyAll();
					}
					
					lock.acquire(false);
				} catch (InterruptedException e) {
					// pass
					interrupted = true;
				} catch (Exception e) {
					fail();
				}
			}});
		
		try {
			lock.acquire(false);
			
			synchronized (monitor) {
				t.start();
				
				// wait for the other thread to acquire the lock
				monitor.wait();
			}
			
			Thread.sleep(500);
			
			// now interrupt the other thread
			t.interrupt();
			
			Thread.sleep(500);
			
			lock.release();
			
			assertTrue(interrupted);
		} catch (Exception e) {
			fail(e);
		}
	}
	
	/**
	 * Tests for correct propagation of thread interrupt from the acquire()
	 * method when the thread is already interrupted upon entering it.
	 */
	public void test_interrupt_acquire_alreadyInterrupted() {
		try {
			Thread.currentThread().interrupt();
			lock.acquire(false);
			
			fail("Should have thrown InterruptedException"); //$NON-NLS-1$
		} catch (InterruptedException e) {
			// pass
			AbstractTest.trace("Got the expected InterruptedException"); //$NON-NLS-1$
		} catch (Exception e) {
			fail(e);
		}
	}
	
	/**
	 * Tests for correct propagation of thread interrupt when the AcquireJob
	 * of a uiSafeAcquire() call is interrupted.
	 */
	public void test_interrupt_uiSafeAcquire_jobInterrupted() {
		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					synchronized (monitor) {
						// wake up the main thread
						monitor.notifyAll();
					}
					
					lock.uiSafeAcquire(false);
				} catch (InterruptedException e) {
					// pass
					interrupted = true;
				} catch (Exception e) {
					fail(e);
				}
			}});
		
		JobListener jl = new JobListener();
		
		try {
			lock.acquire(false);
			
			Platform.getJobManager().addJobChangeListener(jl);
			
			synchronized (monitor) {
				t.start();
				
				// wait for the other thread
				monitor.wait();
			}
			
			Job acquireJob = jl.waitUntilRunning();
			
			// wait a moment for the job to actually find a worker thread
			//    (J2SE 5.0 on Mac is very fast)
			Thread.sleep(500);
			
			// now interrupt the acquire Job
			acquireJob.getThread().interrupt();
			
			// be sure to sleep again, so that the job has time to detect its
			//    interruption before we release the lock
			Thread.sleep(500);
			
			lock.release();
			
			jl.waitUntilDone();
			
			t.join();
			
			assertTrue(interrupted);
		} catch (Exception e) {
			fail(e);
		} finally {
			Platform.getJobManager().removeJobChangeListener(jl);
		}
	}
	
	/**
	 * Tests for correct propagation of thread interrupt when the acquire job
	 * of a uiSafeAcquire() is cancelled.
	 */
	public void test_interrupt_uiSafeAcquire_jobCancelled() {
		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					synchronized (monitor) {
						// wake up the main thread
						monitor.notifyAll();
					}
					
					lock.uiSafeAcquire(false);
				} catch (InterruptedException e) {
					// pass
					interrupted = true;
				} catch (Exception e) {
					fail();
				}
			}});
		
		JobListener jl = new JobListener();
		
		try {
			lock.acquire(false);
			
			Platform.getJobManager().addJobChangeListener(jl);
			
			synchronized (monitor) {
				t.start();
				
				// wait for the other thread
				monitor.wait();
			}
			
			// wait until the acquire job is running so that we will know that
			//     the other thread is blocked
			Job acquireJob = jl.waitUntilRunning();
			
			Thread.sleep(500);
			
			// now cancel the job via the progress monitor
			acquireJob.cancel();
			
			jl.waitUntilDone();
			
			lock.release();
			
			t.join();
			
			assertTrue(interrupted);
		} catch (Exception e) {
			fail(e);
		} finally {
			Platform.getJobManager().removeJobChangeListener(jl);
		}
	}
	
	//
	// Fixture methods
	//
	
	protected void setUp()
		throws Exception {
		
		AbstractTest.trace("===> Begin : " + getName()); //$NON-NLS-1$
		
		lock = new Lock();
		monitor = new Object();
	}
	
	protected void tearDown()
		throws Exception {
		
		lock = null;
		monitor = null;
		interrupted = false;
		
		AbstractTest.trace("===> End   : " + getName()); //$NON-NLS-1$
	}
	
	/**
	 * Records a failure due to an exception that should not have been thrown.
	 * 
	 * @param e the exception
	 */
	protected void fail(Exception e) {
		e.printStackTrace();
		fail("Should not have thrown: " + e.getLocalizedMessage()); //$NON-NLS-1$
	}
}
