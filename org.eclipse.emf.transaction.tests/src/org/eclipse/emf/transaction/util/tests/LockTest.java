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
 *   Boris Gruschko - Bug 262175
 *
 * </copyright>
 *
 * $Id: LockTest.java,v 1.6 2009/02/01 02:17:34 cdamus Exp $
 */
package org.eclipse.emf.transaction.util.tests;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
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
		if (!PlatformUI.isWorkbenchRunning()) {
			// can only execute this test case in a workbench
			AbstractTest.trace("*** Test skipped because not running in a workbench ***"); //$NON-NLS-1$
			return;
		}
		
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
			
			Job.getJobManager().addJobChangeListener(jl);
			
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
			Job.getJobManager().removeJobChangeListener(jl);
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
			
			Job.getJobManager().addJobChangeListener(jl);
			
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
			Job.getJobManager().removeJobChangeListener(jl);
		}
	}
	
	/**
	 * Tests that when the UI thread attempts to acquire, liveness is maintained
	 * even if the UI thread is running an implicit job.
	 */
	public void test_uiSafeWaitForAcquire_implicitJob_bug162141() {
		if (!PlatformUI.isWorkbenchRunning()) {
			// can only execute this test case in a workbench
			AbstractTest.trace("*** Test skipped because not running in a workbench ***"); //$NON-NLS-1$
			return;
		}

		// an identity rule
		ISchedulingRule rule = new ISchedulingRule() {
			public boolean isConflicting(ISchedulingRule rule) {
				return rule == this;
			}
		
			public boolean contains(ISchedulingRule rule) {
				return rule == this;
			}};
		
		final TransactionalEditingDomain domain =
			TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain();
		
		lock = getLock(domain);
		
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
			
			// start an implicit job on our fake rule
			Job.getJobManager().beginRule(rule, null);
			
			try {
				// now attempt to acquire the lock while the other thread sleeps
				lock.uiSafeAcquire(false);
				
				// check that we did actually wait for the lock  :-)
				assertTrue(System.currentTimeMillis() - start >= longInterval);
				
				// check that the UI processed the synchronous Runnable
				assertTrue(syncRunnableFinished[0]);
				
				lock.release();
			} finally {
				Job.getJobManager().endRule(rule);
			}
		} catch (Exception e) {
			fail(e);
		} finally {
			domain.dispose();
		}
	}
	
	public void test_uiSafeWaitForAcquire_explicitJob_beginRule_262175() {
		final TransactionalEditingDomain domain =
			TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain();
		
		final CountDownLatch	latch = new CountDownLatch(1);
		
		lock = getLock(domain);
		
		final boolean[]	status = {false};
		
		Job	job	=	new Job("TestJob") { //$NON-NLS-1$
			
			@Override
			protected IStatus run(IProgressMonitor monitor) {

				// test rule
				ISchedulingRule rule = new ISchedulingRule() {

					public boolean contains(ISchedulingRule rule) {
						return rule == this;
					}

					public boolean isConflicting(ISchedulingRule rule) {
						return rule == this;
					}
				};
				
				// simulates ownership of a rule
				Job.getJobManager().beginRule(rule, new NullProgressMonitor());
				
				try {
					latch.countDown();
					lock.uiSafeAcquire(false);
					
					synchronized(status) {
						status[0] = true;
					}
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				} finally {
					lock.release();
					Job.getJobManager().endRule(rule);
				}
				
				return Status.OK_STATUS;
			}
		};
		
		try {
			lock.acquire(true);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		
		try {
		
			job.schedule();
			
			latch.await();
			
			Thread.sleep(1000); // make sure the Job entered the wait for this.lock
			
			lock.release();
		
			try {
				job.join();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		
		} catch (InterruptedException e) {
			fail(e);
		} finally {
			synchronized(status) {
				assertTrue("Job did not acquire a rule", status[0]); //$NON-NLS-1$
			}
		}
	}
    
    /**
     * Tests that when IJobManager::beginRule() method fails, we don't end up
     * with an AcquireJob getting and transfering the lock after we've already
     * given up.
     */
    public void test_uiSafeWaitForAcquire_beginRuleThrows_bug205857() {
        final ISchedulingRule rule = ResourcesPlugin.getWorkspace().getRoot();
        
        final TransactionalEditingDomain domain =
            TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain();
        
        lock = getLock(domain);
        
        Thread t = new Thread(new Runnable() {
        
            public void run() {
                try {
                    synchronized (monitor) {
                        // wake up the main thread to give us a sched rule
                        monitor.notifyAll();
                    }
                    
                    lock.uiSafeAcquire(false);
                    fail("Should have thrown InterruptedException"); //$NON-NLS-1$
                } catch (InterruptedException e) {
                    // success
                    System.out.println("Got expected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
                } catch (Exception e) {
                    // success
                    fail(e);
                } finally {
                    // we were given this rule, so we must end it
                    Job.getJobManager().endRule(rule);
                }
            }});
        
        try {
            Job.getJobManager().beginRule(rule, null);
            lock.acquire(false);
            
            synchronized (monitor) {
                t.start();
                
                // wait for the other thread to start
                monitor.wait();
            }
            
            // sleep just a bit to let the other thread start waiting for
            // the lock on its initial 250-millis hard wait
            Thread.sleep(50L);
            
            // hand over the scheduling rule before the other thread starts
            // the AcquireJob-based wait
            Job.getJobManager().transferRule(rule, t);
            
            // now, wait long enough for the other thread to start its
            // AcquireJob-based-wait and then release the lock
            Thread.sleep(250L);
            lock.release();
            
            // now, wait for the other thread to finish
            t.join();
            
            // sleep a bit
            Thread.sleep(250L);
            
            Thread owner = lock.getOwner();
            if (owner != null) {
                fail("Lock still owned by thread " + owner.getName()); //$NON-NLS-1$
            }
        } catch (Exception e) {
            fail(e);
        } finally {
            domain.dispose();
        }
    }
	
	
	//
	// Fixture methods
	//
	
	@Override
	protected void setUp()
		throws Exception {
		
		AbstractTest.trace("===> Begin : " + getName()); //$NON-NLS-1$
		
		lock = new Lock();
		monitor = new Object();
	}
	
	@Override
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
	
	/**
	 * A reflective hack to get the transaction lock of an editing domain.
	 * 
	 * @param domain the editing domain
	 * 
	 * @return its transaction lock
	 */
	private Lock getLock(TransactionalEditingDomain domain) {
		Lock result = null;
		Field field = null;
		
		try {
			Class<?> clazz = domain.getClass();
			
			field = clazz.getDeclaredField("transactionLock"); //$NON-NLS-1$
			field.setAccessible(true);
			
			result = (Lock) field.get(domain);
		} catch (Exception e) {
			fail("Could not access transactionLock field: " + e.getLocalizedMessage()); //$NON-NLS-1$
		} finally {
			if (field != null) {
				field.setAccessible(false);
			}
		}
		
		return result;
	}
}
