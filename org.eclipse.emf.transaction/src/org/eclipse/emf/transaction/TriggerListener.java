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
 * $Id: TriggerListener.java,v 1.4 2007/06/07 14:25:59 cdamus Exp $
 */
package org.eclipse.emf.transaction;

import java.util.Iterator;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.transaction.util.ConditionalRedoCommand;

/**
 * A convenient superclass for listeners that implement "triggers" to process
 * {@link Notification}s one at a time, generating a command for each that will
 * make dependent updates to the model.
 *
 * @author Christian W. Damus (cdamus)
 */
public abstract class TriggerListener extends ResourceSetListenerImpl {
	/**
	 * Initializes me with the default filter.
	 */
	public TriggerListener() {
		super();
	}

	/**
	 * Initializes me with the specified filter.
	 * 
	 * @param filter a notification filter, or <code>null</code> for the default
	 */
	public TriggerListener(NotificationFilter filter) {
		super(filter);
	}

	/**
	 * Implements the trigger callback by processing the <code>event</code>'s
	 * notifications one by one, delegating to the {@link #trigger} method for each to
	 * generate a command.  The commands created by the subclass are chained in
	 * the order that they are received from the subclass.
	 * 
	 * @return a composite of the commands returned by the subclass
	 *     implementation of the {@link #trigger} method
	 */
	public Command transactionAboutToCommit(ResourceSetChangeEvent event) throws RollbackException {
		Command result = null;
		
		for (Iterator iter = event.getNotifications().iterator(); iter.hasNext();) {
			Notification next = (Notification) iter.next();
			
			Command trigger = trigger(event.getEditingDomain(), next);
			if (trigger != null) {
				if (result == null) {
					result = trigger;
				} else {
					if (result instanceof ConditionalRedoCommand.Compound) {
						result = result.chain(trigger);
					} else {
						Command previous = result;
						result = new ConditionalRedoCommand.Compound();
						result.chain(previous);
						result.chain(trigger);
					}
				}
			}
		}
		
		return result;
	}
	
	/**
	 * Implemented by subclasses to provide a trigger command for a single
	 * change in the model.
	 * 
	 * @param domain the editing domain
	 * @param notification the notification describing a change in the model
	 * 
	 * @return the command, or <code>null</code> if none is required for this
	 *     particular notification
	 */
	protected abstract Command trigger(TransactionalEditingDomain domain, Notification notification);

	/**
	 * I want only ppre-commit events, not post-commit events.
	 */
	public boolean isPrecommitOnly() {
		return true;
	}
}
