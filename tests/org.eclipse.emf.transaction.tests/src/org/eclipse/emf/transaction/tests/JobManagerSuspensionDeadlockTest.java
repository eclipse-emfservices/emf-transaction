/**
 * Copyright (c) 2008 Innovations Softwaretechnologie GmbH, Zeligsoft Inc., and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *   Innovations Softwaretechnologie - Initial API and implementation
 *   Zeligsoft - Bug 248717 (ensure that a failure does not hang the suite)
 */
package org.eclipse.emf.transaction.tests;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.emf.edit.provider.ReflectiveItemProviderAdapterFactory;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.impl.TransactionalEditingDomainImpl;

/**
 * Tests that transactions do not dead-lock when the Eclipse Job Manager is
 * suspended.
 */
@SuppressWarnings("nls")
public class JobManagerSuspensionDeadlockTest extends TestCase {

	public static Test suite() {
		return new TestSuite(JobManagerSuspensionDeadlockTest.class,
			"JobManager Suspension Dead-lock Tests");
	}

	public void testDeadlock()
			throws Exception {
		
		final TransactionalEditingDomain domain = new TransactionalEditingDomainImpl(
			new ReflectiveItemProviderAdapterFactory());

		final Object lock = new Object();

		Thread t1 = new Thread("Thread-1") {

			@Override
			public void run() {
				try {
					System.out.println("Thread-1: Started");
					// wait for thread 2
					synchronized (lock) {
						lock.wait();
					}

					// do something in a transactional context
					System.out.println("Thread-1: Invoke runExclusive");
					domain.runExclusive(new Runnable() {

						public void run() {
							System.out
								.println("Thread-1: Do something Do something in exclusive mode");
						}
					});

					System.out.println("Thread-1: Finished");
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		};

		Thread t2 = new Thread("Thread-2") {

			@Override
			public void run() {
				System.out.println("Thread-2: Started");
				try {
					// do something in a transactional context
					System.out.println("Thread-2: Invoke runExclusive");
					domain.runExclusive(new Runnable() {

						public void run() {
							// Notify thread 1
							synchronized (lock) {
								lock.notifyAll();
							}

							System.out
								.println("Thread-2: Do something in exclusive mode for 1 second");
							try {
								sleep(1000L);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
					});
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				System.out.println("Thread-2: Finished");
			}
		};

		try {
			// Suspend the job manager like
			// org.eclipse.ui.internal.ide.applicationIDEWorkbenchAdvisor.preStartup()
			// until workbench startup
			Job.getJobManager().suspend();
	
			// Start Thread-1 and make sure that it is alive
			t1.start();
			t1.join(250);
	
			// Thread-1 should be alive now...
			assertTrue(t1.isAlive());
	
			t2.start();
			t2.join(5000L);
	
			// Thread-2 should have finished now
			assertFalse(t2.isAlive());
	
			t1.join(5000L);
	
			// Thread-1 should have finished now
			assertFalse(t1.isAlive());
		} finally {
			Job.getJobManager().resume();
		}
	}
}
