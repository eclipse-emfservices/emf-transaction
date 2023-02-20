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
package org.eclipse.emf.workspace.tests.fixtures;

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
	
	@Override
	public Command transactionAboutToCommit(ResourceSetChangeEvent event)
		throws RollbackException {
		
		precommit = event;
		
		return null;
	}
	
	@Override
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
