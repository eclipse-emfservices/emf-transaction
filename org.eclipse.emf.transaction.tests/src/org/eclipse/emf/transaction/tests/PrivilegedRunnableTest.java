/**
 * <copyright>
 *
 * Copyright (c) 2006 IBM Corporation and others.
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
 * $Id: PrivilegedRunnableTest.java,v 1.2 2006/06/15 13:33:34 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.transaction.RunnableWithResult;


/**
 * Tests the sharing of transactions between cooperating threads using
 * privileged runnables.
 *
 * @author Christian W. Damus (cdamus)
 */
public class PrivilegedRunnableTest extends AbstractTest {
	static final IStatus TEST_STATUS = new Status(
			IStatus.OK, TestsPlugin.instance.getBundle().getSymbolicName(),
			0, "OK", null); //$NON-NLS-1$
	
	private TestThread thread;
	private TestThread thread2;
	
	private TestRead read;
	private TestReadWithResult readWithResult;
	private TestWrite write;
	private TestWriteWithResult writeWithResult;
	
	public PrivilegedRunnableTest(String name) {
		super(name);
	}

	public static Test suite() {
		return new TestSuite(PrivilegedRunnableTest.class, "Privileged Runnable Tests"); //$NON-NLS-1$
	}

	/**
	 * Tests the sharing of a read-only transaction.
	 */
	public void test_sharingReadOnlyTransaction() {
		RunnableWithResult privileged;
		startReading();
		
		privileged = domain.createPrivilegedRunnable(read);
		
		// try a regular runnable
		thread.syncExec(privileged);

		assertTrue(read.wasExecuted);
		assertTrue(privileged.getStatus().isOK());
		
		commit();
		
		
		startReading();
		
		privileged = domain.createPrivilegedRunnable(readWithResult);
		
		// try a runnable with result
		thread.syncExec(privileged);
		
		assertTrue(readWithResult.wasExecuted);
		assertSame(TEST_STATUS, readWithResult.getStatus());
		assertEquals(root.getBooks(), readWithResult.getResult());

		assertSame(readWithResult.getStatus(), privileged.getStatus());
		assertSame(readWithResult.getResult(), privileged.getResult());
		
		commit();
	}

	/**
	 * Tests the sharing of a read-write transaction.
	 */
	public void test_sharingReadWriteTransaction() {
		RunnableWithResult privileged;
		startWriting();
		
		privileged = domain.createPrivilegedRunnable(write);
		
		// try a regular runnable
		thread.syncExec(privileged);

		assertTrue(write.wasExecuted);
		assertTrue(privileged.getStatus().isOK());
		
		assertTrue(root.getBooks().isEmpty());
		
		rollback(); // want to repeat the change
		
		
		startWriting();
		
		// check that we have books once again
		assertFalse(root.getBooks().isEmpty());
		
		privileged = domain.createPrivilegedRunnable(writeWithResult);
		
		// try a runnable with result
		thread.syncExec(privileged);
		
		assertTrue(writeWithResult.wasExecuted);
		assertSame(TEST_STATUS, writeWithResult.getStatus());
		assertEquals(root.getBooks(), writeWithResult.getResult());

		assertSame(writeWithResult.getStatus(), privileged.getStatus());
		assertSame(writeWithResult.getResult(), privileged.getResult());
		
		assertTrue(root.getBooks().isEmpty());
		
		// verify that I got my transaction back by trying to write
		
		root.getWriters().clear();
		
		rollback();
	}
	
	/**
	 * Tests that a thread that has borrowed a transaction via a privileged
	 * runnable can lend it along to yet another thread.
	 */
	public void test_nestedSharing() {
		thread2 = new TestThread();
		thread2.start(); // will be killed by doTearDown()
		
		Runnable nestingRunnable = new Runnable() {
		
			public void run() {
				thread2.syncExec(domain.createPrivilegedRunnable(write));
			}};
		
		RunnableWithResult privileged;
		startWriting();
		
		privileged = domain.createPrivilegedRunnable(nestingRunnable);
		
		thread.syncExec(privileged);

		assertTrue(write.wasExecuted);
		assertTrue(privileged.getStatus().isOK());
		
		assertTrue(root.getBooks().isEmpty());
		
		rollback(); // want to repeat the change
	}
	
	/**
	 * Tests the assertion that a transaction be active when executing
	 * a privileged runnable.
	 */
	public void test_transactionMustBeActive() {
		RunnableWithResult privileged;
		startWriting();
		
		privileged = domain.createPrivilegedRunnable(write);
		
		commit();
		
		thread.syncExec(privileged);
		
		Exception e = thread.getException();
		assertNotNull("Should have thrown IllegalStateException", e); //$NON-NLS-1$
		System.out.println("Got expected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
	}
	
	/**
	 * Tests the assertion that a transaction be the editing domain's current
	 * transaction when executing a privileged runnable.
	 */
	public void test_transactionMustBeCurrent() {
		RunnableWithResult privileged;
		startWriting();
		
		privileged = domain.createPrivilegedRunnable(write);
		
		startWriting(); // nested transaction is now the "current"
		
		thread.syncExec(privileged);
		
		Exception e = thread.getException();
		assertNotNull("Should have thrown IllegalStateException", e); //$NON-NLS-1$
		System.out.println("Got expected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
		
		commit();
		commit();
	}
	
	/**
	 * Ensure that the catching of run-time exceptions in privileged runnables
	 * does not cause an overriding <code>IllegalArgumentException</code> on
	 * initializing a <code>Status</code> with a <code>null</code> message from
	 * the exception.  The privileged runnable must have a status when finished,
	 * whether successfully or not.
	 */
	public void test_runtimeExceptionInRunnable_146625() {
		final RuntimeException e = new RuntimeException();
		
		RunnableWithResult privileged;
		startReading();
		
		privileged = domain.createPrivilegedRunnable(new Runnable() {
			public void run() {
				// throw the run-time exception
				throw e;
			}});
		
		thread.syncExec(privileged);

		// check that we have the same exception as was thrown by the runnable
		assertNotNull(privileged.getStatus());
		assertFalse(privileged.getStatus().isOK());
		assertSame(e, privileged.getStatus().getException());
		
		commit();
	}

	//
	// Fixture methods
	//
	
	protected void doSetUp() throws Exception {
		super.doSetUp();
		
		thread = new TestThread();
		thread.start();
		
		read = new TestRead();
		readWithResult = new TestReadWithResult();
		write = new TestWrite();
		writeWithResult = new TestWriteWithResult();
	}
	
	protected void doTearDown() throws Exception {
		read = null;
		readWithResult = null;
		write = null;
		writeWithResult = null;
		
		thread.die();
		thread = null;
		
		if (thread2 != null) {
			thread2.die();
			thread2 = null;
		}
		
		super.doTearDown();
	}
	
	class TestThread extends Thread {
		private boolean shouldDie = false;
		private Runnable runnable;
		private Exception exception = null;
		
		TestThread() {
			super("Privilege Test thread"); //$NON-NLS-1$
			setDaemon(true);
		}
		
		public synchronized void die() {
			shouldDie = true;
		}
		
		public void syncExec(Runnable runnable) {
			synchronized (this) {
				this.runnable = runnable;
				try {
					wait();
				} catch (InterruptedException e) {
					TestCase.fail("Interrupted while waiting for runnable"); //$NON-NLS-1$
				}
			}
		}
		
		public Exception getException() {
			Exception result = exception;
			exception = null;
			return result;
		}
		
		public void run() {
			for (;;) {
				synchronized (this) {
					if (shouldDie) {
						// in case someone was waiting for a runnable to run
						notifyAll();
						break;
					}
					
					if (runnable != null) {
						execute(runnable);
						runnable = null;
						notifyAll();  // wake up the thread waiting for the runnable
					}
				}
				
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					// doesn't matter.  We just won't sleep as long
				}
			}
		}
		
		private void execute(Runnable runnable) {
			try {
				runnable.run();
			} catch (Exception e) {
				exception = e;
			}
		}
	}
	
	class TestRead implements Runnable {
		boolean wasExecuted = false;
		
		public void run() {
			root.getBooks();
			wasExecuted = true;
		}
	}
	
	class TestReadWithResult extends RunnableWithResult.Impl {
		boolean wasExecuted = false;
		
		public void run() {
			setResult(root.getBooks());
			setStatus(TEST_STATUS);
			wasExecuted = true;
		}
	}
	
	class TestWrite implements Runnable {
		boolean wasExecuted = false;
		
		public void run() {
			root.getBooks().clear();
			wasExecuted = true;
		}
	}
	
	class TestWriteWithResult extends RunnableWithResult.Impl {
		boolean wasExecuted = false;
		
		public void run() {
			root.getBooks().clear();
			setResult(root.getBooks());
			setStatus(TEST_STATUS);
			wasExecuted = true;
		}
	}
}
