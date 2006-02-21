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
 * $Id: TestListener.java,v 1.2 2006/02/21 22:16:40 cmcgee Exp $
 */
package org.eclipse.emf.transaction.tests.fixtures;

import java.util.ArrayList;
import java.util.List;

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
	
	/** The copied list of precommit notifications from the precommit event. */
	public List precommitNotifications;
	
	/** The last post-commit event received. */
	public ResourceSetChangeEvent postcommit;
	
	/** The copied list of postcommit notifications from the postcommit event.*/
	public List postcommitNotifications;
	
	public TestListener() {
		super(NotificationFilter.ANY);
	}

	public TestListener(NotificationFilter filter) {
		super(filter);
	}
	
	public Command transactionAboutToCommit(ResourceSetChangeEvent event)
		throws RollbackException {
		
		precommit = event;
		precommitNotifications = new ArrayList(event.getNotifications());
		
		return null;
	}
	
	public void resourceSetChanged(ResourceSetChangeEvent event) {
		postcommit = event;
		postcommitNotifications = new ArrayList(event.getNotifications());
	}
	
	/**
	 * Clears the stored events.
	 */
	public void reset() {
		precommit = null;
		precommitNotifications = null;
		postcommit = null;
		postcommitNotifications = null;
	}
}