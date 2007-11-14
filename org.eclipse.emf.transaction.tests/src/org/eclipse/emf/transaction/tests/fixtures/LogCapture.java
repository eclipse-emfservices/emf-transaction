/**
 * <copyright>
 *
 * Copyright (c) 2006, 2007 IBM Corporation and others.
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
 * $Id: LogCapture.java,v 1.3 2007/11/14 18:14:13 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests.fixtures;

import java.util.List;

import junit.framework.Assert;

import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.transaction.ExceptionHandler;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.TransactionalCommandStack;
import org.eclipse.emf.transaction.tests.TestsPlugin;
import org.osgi.framework.Bundle;

/**
 * A log listener that captures the last entry (if any) logged by a specified
 * bundle during an interval.
 *
 * @author Christian W. Damus (cdamus)
 */
public class LogCapture {
	private final Bundle targetBundle;
	private final ILogListener listener = new ILogListener() {
		public void logging(IStatus status, String plugin) {
			if (status.getPlugin().equals(targetBundle.getSymbolicName())) {
				record(status);
			}
		}};
	private final TransactionalCommandStack stack;
	
	private final List<IStatus> logs = new java.util.ArrayList<IStatus>();
	private IStatus lastLog;
	
	/**
	 * Initializes me to capture logs from the specified bundle.
	 * 
	 * @param targetBundle the bundle to listen to
	 */
	public LogCapture(Bundle targetBundle) {
		this(null, targetBundle);
	}
	
	/**
	 * Initializes me to capture logs from the specified command stack.  The
	 * implicit bundle to listen for logs is this test plug-in.
	 * 
	 * @param stack the command stack to listen to
	 */
	public LogCapture(TransactionalCommandStack stack) {
		this(stack, TestsPlugin.instance.getBundle());
	}
	
	/**
	 * Initializes me to capture logs from the specified bundle and attach an
	 * exception handler to the specified command stack.
	 * 
	 * @param stack the command stack to handle
	 * @param targetBundle the bundle for which to listen for logs
	 */
	public LogCapture(TransactionalCommandStack stack, Bundle targetBundle) {
		this.targetBundle = targetBundle;
		this.stack = stack;
		
		if (stack != null) {
			stack.setExceptionHandler(new ExceptionHandler() {
				public void handleException(Exception e) {
					if (e instanceof RollbackException) {
						TestsPlugin.instance.getLog().log(((RollbackException) e).getStatus());
					} else {
						TestsPlugin.instance.getLog().log(new Status(
								IStatus.ERROR,
								TestsPlugin.instance.getBundle().getSymbolicName(),
								1,
								"Uncaught exception", //$NON-NLS-1$
								e));
					}
				}});
		}
		
		Platform.addLogListener(listener);
	}
	
	/**
	 * Stops me, detaching my log listener from the platform.
	 */
	public void stop() {
		Platform.removeLogListener(listener);
		
		if (stack != null) {
			stack.setExceptionHandler(null);
		}
	}
	
	/**
	 * Gets the last log, if any, from my target bundle.
	 * 
	 * @return the last log, or <code>null</code> if none
	 */
	public IStatus getLastLog() {
		return lastLog;
	}
	
	/**
	 * Obtains the list of logs from my target bundle.
	 * 
	 * @return a list (possibly empty) of {@link IStatus}es
	 */
	public List<IStatus> getLogs() {
		return logs;
	}
	
	/**
	 * Asserts that I captured a status that logged the specified throwable.
	 * 
	 * @param throwable a throwable that should have been logged
	 */
	public void assertLogged(Throwable throwable) {
        IStatus log = getLastLog();
        Assert.assertNotNull(log);
        log = findStatus(log, throwable);
        Assert.assertNotNull(log);
	}
	
	private void record(IStatus log) {
		logs.add(log);
		lastLog = log;
	}
	
	/**
	 * Finds the status in a (potentially multi-) status that carries the
	 * specified exception.
	 * 
	 * @param status a status
	 * @param exception a throwable to look for
	 * 
	 * @return the matching status, or <code>null</code> if not found
	 */
	private IStatus findStatus(IStatus status, Throwable exception) {
		IStatus result = (status.getException() == exception)? status : null;

		if (status.isMultiStatus()) {
			IStatus[] children = status.getChildren();
			
			for (int i = 0; (result == null) && (i < children.length); i++) {
				result = findStatus(children[i], exception);
			}
		}
		
		return result;
	}
}
