/**
 * <copyright>
 *
 * Copyright (c) 2005, 2007 IBM Corporation and others.
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
 * $Id: TestListener.java,v 1.4 2007/11/14 18:14:13 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests.fixtures;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.notify.Notification;
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
	public List<Notification> precommitNotifications;
	
	/** The last post-commit event received. */
	public ResourceSetChangeEvent postcommit;
	
	/** The copied list of postcommit notifications from the postcommit event.*/
	public List<Notification> postcommitNotifications;
	
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
		precommitNotifications = new ArrayList<Notification>(event.getNotifications());
		
		return null;
	}
	
	@Override
	public void resourceSetChanged(ResourceSetChangeEvent event) {
		postcommit = event;
		postcommitNotifications = new ArrayList<Notification>(event.getNotifications());
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
