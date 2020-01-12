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
package org.eclipse.emf.transaction.tests.fixtures;

import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;

/**
 * A job change listener that listens for lifecycle events in the AcquireJob
 * that implements UI-safe lock acquisition.
 *
 * @author Christian W. Damus (cdamus)
 */
public class JobListener implements IJobChangeListener {
	private final Object runningMonitor = new Object();
	private boolean notifiedRunning;
	
	private final Object doneMonitor = new Object();
	private boolean notifiedDone;
	
	private Job acquireJob = null;
	
	/**
	 * Blocks the current thread until the AcquireJob starts running.
	 * 
	 * @return the AcquireJob
	 * 
	 * @throws InterruptedException if the current thread is interrupted
	 */
	public Job waitUntilRunning() throws InterruptedException {
		synchronized (runningMonitor) {
			while (!notifiedRunning) {
				runningMonitor.wait();
			}
		}
		
		return acquireJob;
	}
	
	/**
	 * Blocks the current thread until the AcquireJob finishes.
	 * 
	 * @return the AcquireJob
	 * 
	 * @throws InterruptedException if the current thread is interrupted
	 */
	public Job waitUntilDone() throws InterruptedException {
		synchronized (doneMonitor) {
			while (!notifiedDone) {
				doneMonitor.wait();
			}
		}
		
		return acquireJob;
	}

	// Documentation copied from the inherited specification
	public void running(IJobChangeEvent event) {
		if (event.getJob() == acquireJob) {
			synchronized (runningMonitor) {
				runningMonitor.notify();
				notifiedRunning = true;
			}
		}
	}

	// Documentation copied from the inherited specification
	public void done(IJobChangeEvent event) {
		if (event.getJob() == acquireJob) {
			synchronized (doneMonitor) {
				doneMonitor.notify();
				notifiedDone = true;
			}
		}
	}

	// Documentation copied from the inherited specification
	public void scheduled(IJobChangeEvent event) {
		Job job = event.getJob();
		
		if (acquireJob == null) {
			if (job.getClass().getName().endsWith("AcquireJob")) { //$NON-NLS-1$
				acquireJob = job;
			}
		}
	}

	// Documentation copied from the inherited specification
	public void sleeping(IJobChangeEvent event) {
		// not interesting
	}
	
	// Documentation copied from the inherited specification
	public void aboutToRun(IJobChangeEvent event) {
		// not interesting
	}

	// Documentation copied from the inherited specification
	public void awake(IJobChangeEvent event) {
		// not interesting
	}
}
