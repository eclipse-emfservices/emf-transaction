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
 * $Id: LongRunningReadAction.java,v 1.1 2006/01/30 16:30:09 cdamus Exp $
 */
package org.eclipse.emf.workspace.examples.extlibrary.actions;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.emf.edit.domain.IEditingDomainProvider;
import org.eclipse.emf.transaction.RunnableWithResult;
import org.eclipse.emf.transaction.TXEditingDomain;
import org.eclipse.emf.workspace.examples.extlibrary.console.ConsoleUtil;
import org.eclipse.emf.workspace.examples.extlibrary.internal.l10n.Messages;
import org.eclipse.jface.action.Action;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IWorkbenchPart;

/**
 * A pathetic example of a background job that simulates reading from the model
 * (though not actually reading anything, only obtaining a read lock).
 * The job simply loops sixty times, sleeping for 500 millis and printing a message,
 * all within the context of a read-only transaction on the editing domain.  At
 * each iteration, after sleeping and checking the progress monitor for cancellation,
 * the job yields the editing domain to any other transactions that need to read.
 * Such transactions might include UI refreshes or even other concurrent instances
 * of this job. 
 *
 * @author Christian W. Damus (cdamus)
 */
public class LongRunningReadAction extends Action {
	private static final String CONSOLE = Messages.readConsole_title;
	
	private TXEditingDomain domain;
	
	public LongRunningReadAction() {
		super(Messages.readJob_title);
	}

	public void run() {
		if (domain != null) {
			ConsoleUtil.showConsole(CONSOLE);
			Job readJob = new ReadJob(getText(), domain);
			
			readJob.schedule();
		}
	}
	
	public void setActiveWorkbenchPart(IWorkbenchPart workbenchPart) {
		if (workbenchPart instanceof IEditingDomainProvider) {
			domain = (TXEditingDomain) ((IEditingDomainProvider) workbenchPart)
					.getEditingDomain();
		}
		
		setEnabled(domain != null);
	}
	
	/**
	 * Implementation of a long-running read operation as a Job.
	 *
	 * @author Christian W. Damus (cdamus)
	 */
	private static class ReadJob extends Job {
		private static int nextId = 0;
		
		private final TXEditingDomain domain;
		private final int id;
		
		ReadJob(String label, TXEditingDomain domain) {
			super(label);
			this.domain = domain;
			
			synchronized (ReadJob.class) {
				this.id = nextId++;
			}
			
			// I am a long-running action, so the UI should have priority
			//    over me
			setPriority(Job.LONG);
		}
		
		protected IStatus run(final IProgressMonitor monitor) {
			try {
				return (IStatus) domain.runExclusive(
					new RunnableWithResult.Impl() {
						public void run() {
							setResult(longRunningRead(monitor));
						}});
			} catch (InterruptedException e) {
				// assume cancellation
				return Status.CANCEL_STATUS;
			}
		}
		
		/**
		 * Called within a read transaction to perform the long-running
		 * read operation.
		 * 
		 * @param monitor the job's progress monitor
		 * 
		 * @return the exit status of the job
		 */
		private IStatus longRunningRead(IProgressMonitor monitor) {
			for (int i = 0; i < 60; i++) {
				// every half-second, check for cancellation and also
				//    yield to other readers
				try {
					ConsoleUtil.println(
							CONSOLE,
							NLS.bind(
									Messages.readJob_msg,
									new Integer(id),
									new Integer(i)));
					Thread.sleep(500);
				} catch (InterruptedException e) {
					// assume cancellation
					return Status.CANCEL_STATUS;
				}
				
				if (monitor.isCanceled()) {
					return Status.CANCEL_STATUS;
				}
				
				domain.yield();
			}
			
			return Status.OK_STATUS;
		}
	}
}
