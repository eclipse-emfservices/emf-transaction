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
 * $Id: TestListener.java,v 1.1 2006/01/03 20:51:12 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests.fixtures;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.transaction.NotificationFilter;
import org.eclipse.emf.transaction.ResourceSetChangeEvent;
import org.eclipse.emf.transaction.ResourceSetListenerImpl;
import org.eclipse.emf.transaction.RollbackException;

/**
 * A listener that records the pre-commit and post-commit events that it
 * receives, for later inspection by a test case.
 *
 * @author Christian W. Damus (cdamus)
 */
public class TestListener extends ResourceSetListenerImpl {
	/** The last pre-commit event received. */
	public ResourceSetChangeEvent precommit;
	
	/** The last post-commit event received. */
	public ResourceSetChangeEvent postcommit;
	
	public TestListener() {
		super(NotificationFilter.ANY);
	}

	public TestListener(NotificationFilter filter) {
		super(filter);
	}
	
	public Command transactionAboutToCommit(ResourceSetChangeEvent event)
		throws RollbackException {
		
		precommit = event;
		
		return null;
	}
	
	public void resourceSetChanged(ResourceSetChangeEvent event) {
		postcommit = event;
	}
	
	/**
	 * Clears the stored events.
	 */
	public void reset() {
		precommit = null;
		postcommit = null;
	}
}