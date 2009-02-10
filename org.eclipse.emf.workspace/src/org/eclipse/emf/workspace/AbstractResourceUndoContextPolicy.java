/**
 * <copyright>
 * 
 * Copyright (c) 2008, 2009 Zeligsoft Inc., Christian W. Damus, and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *   Zeligsoft - Initial API and implementation
 *   Christian W. Damus - Bug 264220
 * 
 * </copyright>
 *
 * $Id: AbstractResourceUndoContextPolicy.java,v 1.2 2009/02/10 04:04:35 cdamus Exp $
 */

package org.eclipse.emf.workspace;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

/**
 * <p>
 * The default implementation of the resource undo-context policy, suitable for
 * clients to extend/override as required. The default policy is to consider any
 * resource as affected by an operation if either
 * </p>
 * <ol>
 * <li>A non-touch {@link Notification} is received from a contained object or
 * from the resource, itself, or</li>
 * <li>A notification matching (1) is received from a uni-directional
 * {@link EReference} (i.e., one having no opposite) has an old value or a new
 * value in the resource
 * </ol>
 * <p>
 * In the first case, above, a subclass can choose to include only changes to a
 * resource's contents-list and URI as being significant (other resource
 * properties not affecting the serialization of the resource).
 * </p>
 * <p>
 * The second case, above, is intended for applications that use
 * {@link ResourceUndoContext}s to manage the Undo menus of their editors. It is
 * a pessimistic assumption that the referenced resource may either have derived
 * attributes whose values influenced precursor or successor operations, or that
 * such operations are influenced by the references incoming to the resource.
 * Thus, the concern is not so much with the dirty state of the resource as it
 * is with the integrity of the undo history for the associated editor and the
 * dependencies between successive operations. Subclasses can disable this case
 * by overriding the {@link #pessimisticCrossReferences()} method.
 * </p>
 * 
 * @author Christian W. Damus (cdamus)
 * @since 1.3
 * 
 * @see #pessimisticCrossReferences()
 * @see #considerAllResourceChanges()
 */
public abstract class AbstractResourceUndoContextPolicy
		implements IResourceUndoContextPolicy {

	/**
	 * Initializes me.
	 */
	protected AbstractResourceUndoContextPolicy() {
		super();
	}

	public Set<Resource> getContextResources(IUndoableOperation operation,
			List<? extends Notification> notifications) {

		Set<Resource> result;

		if (notifications.isEmpty()) {
			result = Collections.emptySet();
		} else {
			result = new java.util.HashSet<Resource>();

			for (Notification next : notifications) {
				if (isAbstractChange(next)) {
					Object notifier = next.getNotifier();

					if (notifier instanceof Resource) {
						resourceChange(result, (Resource) notifier, next);
					} else if (notifier instanceof EObject) {
						objectChange(result, (EObject) notifier, next);
					} else if (notifier instanceof ResourceSet) {
						resourceSetChange(result, (ResourceSet) notifier, next);
					}
				}
			}
		}

		return result;
	}

	/**
	 * Queries whether the specified <tt>notification</tt> signals an abstract
	 * change to a resource, which would potentially put that resource into the
	 * operation context. Subclasses may override or extend as needed.
	 * 
	 * @param notification
	 *            a notification of some concrete change in the resource set
	 * @return whether this change is an abstract change to some resource, for
	 *         the purpose of tracking undo context
	 */
	protected boolean isAbstractChange(Notification notification) {
		return !notification.isTouch();
	}

	/**
	 * May be overridden by subclasses to disable pessimistic handling of
	 * cross-resource references. The default implementation returns
	 * <code>true</code> always.
	 * 
	 * @return whether to consider changes to directed cross-resource references
	 *         as affecting the referenced resource
	 */
	protected boolean pessimisticCrossReferences() {
		return true;
	}

	/**
	 * May be overridden by subclasses to consider changes to any feature of a
	 * resource, not just its contents-list or URI, as affecting it.
	 * 
	 * @return <code>true</code> if all changes to a resource are considered as
	 *         affecting it for the purposes of undo context; <code>false</code>
	 *         if only the contents-list and URI are
	 */
	protected boolean considerAllResourceChanges() {
		return false;
	}

	/**
	 * Adds to the collection of <tt>resources</tt> any that are affected by the
	 * specified <tt>notification</tt> from a <tt>resource</tt>.
	 * 
	 * @param resources
	 *            collects the affected resources
	 * @param resource
	 *            a resource that sent a notification
	 * @param notification
	 *            the notification sent by the resource
	 */
	protected void resourceChange(Set<Resource> resources, Resource resource,
			Notification notification) {

		if (considerAllResourceChanges()
			|| (notification.getFeatureID(Resource.class) == Resource.RESOURCE__CONTENTS)
			|| (notification.getFeatureID(Resource.class) == Resource.RESOURCE__URI)) {

			resources.add(resource);
		}
	}

	/**
	 * Adds to the collection of <tt>resources</tt> any that are affected by the
	 * specified <tt>notification</tt> from an <tt>object</tt>.
	 * 
	 * @param resources
	 *            collects the affected resources
	 * @param object
	 *            a object that sent a notification
	 * @param notification
	 *            the notification sent by the object
	 */
	protected void objectChange(Set<Resource> resources, EObject object,
			Notification notification) {

		Resource resource = object.eResource();

		if (resource != null) {
			resources.add(resource);
		}

		if (pessimisticCrossReferences()) {
			// if the reference has an opposite, then we will get
			// the
			// notification from the other end, anyway
			final Object feature = notification.getFeature();
			if ((feature instanceof EReference)
				&& (((EReference) feature).getEOpposite() == null)) {
				
				crossResourceReference(resources, object, notification);
			}
		}
	}

	/**
	 * Adds to the collection of <tt>resources</tt> any that are affected by the
	 * specified <tt>notification</tt> from a <tt>resourceSet</tt>.
	 * 
	 * @param resources
	 *            collects the affected resources
	 * @param resourceSet
	 *            a resource set that sent a notification
	 * @param notification
	 *            the notification sent by the resource set
	 */
	protected void resourceSetChange(Set<Resource> resources, ResourceSet resourceSet,
			Notification notification) {

		// nothing to do
	}

	/**
	 * Handles notifications that can potentially represent cross-resource
	 * references. Helper to the
	 * {@link #objectChange(Set, EObject, Notification)} method.
	 * 
	 * @param resources
	 *            collects the affected resources
	 * @param owner
	 *            the owner of the reference that changed
	 * @param notification
	 *            a potential cross-resource reference change notification
	 */
	protected void crossResourceReference(Set<Resource> resources,
			EObject owner, Notification notification) {

		Object oldValue = notification.getOldValue();
		Object newValue = notification.getNewValue();
		Resource resource;

		switch (notification.getEventType()) {
			case Notification.SET :
			case Notification.UNSET :
				// bug 264220: in case of UNSET of a multi-valued reference,
				// the old and new values could be Booleans, for the extra
				// notification of change to the is-set state of the reference
				// (a previous REMOVE_MANY indicated the clearing of the list)
				if (oldValue instanceof EObject) {
					resource = ((EObject) oldValue).eResource();

					if (resource != null) {
						resources.add(resource);
					}
				}
				if (newValue instanceof EObject) {
					resource = ((EObject) newValue).eResource();

					if (resource != null) {
						resources.add(resource);
					}
				}
				break;
			case Notification.ADD :
				resource = ((EObject) newValue).eResource();

				if (resource != null) {
					resources.add(resource);
				}
				break;
			case Notification.ADD_MANY : {
				@SuppressWarnings("unchecked")
				Collection<EObject> newReferences = (Collection<EObject>) newValue;
				for (EObject next : newReferences) {
					resource = next.eResource();

					if (resource != null) {
						resources.add(resource);
					}
				}
				break;
			}
			case Notification.REMOVE :
				resource = ((EObject) oldValue).eResource();

				if (resource != null) {
					resources.add(resource);
				}
				break;
			case Notification.REMOVE_MANY : {
				@SuppressWarnings("unchecked")
				Collection<EObject> oldReferences = (Collection<EObject>) oldValue;
				for (EObject next : oldReferences) {
					resource = next.eResource();

					if (resource != null) {
						resources.add(resource);
					}
				}
				break;
			}
		}
	}

}
