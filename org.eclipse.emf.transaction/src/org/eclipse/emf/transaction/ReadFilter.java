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
 * $Id: ReadFilter.java,v 1.4 2006/04/21 14:59:07 cdamus Exp $
 */
package org.eclipse.emf.transaction;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
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
			Object notifier = notification.getNotifier();
			
			if (notifier instanceof Resource) {
				return checkResource(notification);
			}
			
			if (notifier instanceof EObject) {
				EObject eobject = (EObject) notifier;
				
				// ignore changes to a container feature because these will
				//    always be accomanied by changes in the opposite
				//    containment feature (because a container is defined by
				//    being the opposite of a containment).  A special case is
				//    resolution of a containment proxy, in which case the
				//    eInverseAdd() will notify on the container feature but
				//    we do not get a containment notification; this is a
				//    read-compatible change
				Object feature = notification.getFeature();
				if ((feature instanceof EReference)
						&& ((EReference) feature).isContainer()) {
					return true;
				}
				
				return isLoadingOrUnloading(eobject.eResource());
			}
			
			return true;  // not an EObject
		default:
			// any other event type is not one that is supported by EMF, so
			//    we will not prevent it
			return true;
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
			return isLoadingOrUnloading((Resource) notification.getNotifier());
		default:
			return true;
		}
	}
	
	/**
	 * Checks whether a resource is currently in the process of loading or
	 * unloading.
	 * 
	 * @param res a resource
	 * @return <code>true</code> if the resource is currently loading or
	 *    unloading; <code>false</code>, otherwise (fully loaded or unloaded)
	 */
	private boolean isLoadingOrUnloading(Resource res) {
		return ResourceSetManager.getInstance().isLoading(res)
			|| ResourceSetManager.getInstance().isUnloading(res);
	}
}
