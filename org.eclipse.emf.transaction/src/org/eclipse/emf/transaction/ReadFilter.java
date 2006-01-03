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
 * $Id: ReadFilter.java,v 1.1 2006/01/03 20:41:55 cdamus Exp $
 */
package org.eclipse.emf.transaction;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.transaction.impl.ResourceSetManager;

/**
 * Implementation of the filter that matches notifications from changes that are
 * permitted during read-only transactions.  These are only "concrete" changes
 * (implementation details), not "abstract" (semantically significant) model
 * changes.
 * <p>
 * See the documentation on the {@link NotificationFilter#READ} constant for
 * details of how notifications are determined to be "read compatible."
 * </p>
 *
 * @author Christian W. Damus (cdamus)
 * 
 * @see NotificationFilter#READ
 */
class ReadFilter extends NotificationFilter {
	/**
	 * Initializes me.
	 */
	ReadFilter() {
		super();
	}
	
	// Documentation inherited from the method specification
	public boolean matches(Notification notification) {
		switch (notification.getEventType()) {
		case Notification.RESOLVE:
			return true;
		case Notification.SET:
		case Notification.UNSET:
		case Notification.ADD:
		case Notification.ADD_MANY:
		case Notification.REMOVE:
		case Notification.REMOVE_MANY:
			if (notification.getNotifier() instanceof Resource) {
				return checkResource(notification);
			}
			
			return (notification.getNotifier() instanceof ResourceSet);
		default:
			return false;
		}
	}

	/**
	 * Determines whether the specified change to a resource is permitted during
	 * a read-only transaction.
	 * 
	 * @param notification indication of a change in a resource
	 * 
	 * @return whether the notification is "read compatible"
	 */
	private boolean checkResource(Notification notification) {
		switch (notification.getFeatureID(null)) {
		case Resource.RESOURCE__CONTENTS:
			// changes to the contents list are allowed during reading only while
			//    loading or unloading the resource (because loading/unloading
			//    is implemented by adding/removing root objects, respectively)
			Resource res = (Resource) notification.getNotifier();
			
			return ResourceSetManager.getInstance().isLoading(res)
				|| ResourceSetManager.getInstance().isUnloading(res);
		default:
			return true;
		}
	}
}
