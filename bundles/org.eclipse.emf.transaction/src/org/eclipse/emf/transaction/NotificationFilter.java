/**
 * Copyright (c) 2005, 2008 IBM Corporation, Zeligsoft Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *   Zeligsoft - Bug 225711
 */
package org.eclipse.emf.transaction;

import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

/**
 * A filter that determines which {@link Notification}s will be passed to
 * {@link ResourceSetListener}s.  Filters can be combined using simple
 * boolean operations.
 * <p>
 * The default filter for listeners that do not otherwise declare one is
 * {@link #NOT_TOUCH}.
 * </p>
 * <p>
 * <b>Note</b> that {@link ResourceSetListener}s never receive
 * {@link Notification#REMOVING_ADAPTER} notifications because these are
 * intended only to inform an adapter that it is being removed; they are
 * not broadcast to all adapters of an object.  Besides which, resource set
 * listeners are not adapters.
 * </p>
 * <p>
 * Since the EMF Transaction 1.3 release, clients can implement arbitrary
 * filter criteria by specializing the {@link NotificationFilter.Custom} class.
 * </p>
 *
 * @author Christian W. Damus (cdamus)
 * 
 * @see ResourceSetListener
 * @see Notification
 */
public abstract class NotificationFilter {
	/** As its name implies, this filter matches any notification. */
	public static final NotificationFilter ANY = new NotificationFilter() {
		@Override
		public boolean matches(Notification notification) {
			return true;
		}};
	
	/**
	 * Matches only notifications that are not "touches."
	 * 
	 * @see Notification#isTouch()
	 */
	public static final NotificationFilter NOT_TOUCH = new NotificationFilter() {
		@Override
		public boolean matches(Notification notification) {
			return !notification.isTouch();
		}};
	
	/**
	 * Matches any notification that can occur during the normal course of
	 * a read-only transaction.  These include:
	 * <ul>
	 *   <li>proxy resolution</li>
	 *   <li>any change to the {@link ResourceSet#getResources() resources}
	 *       list of a resource set</li>
	 *   <li>any change to the <code>isLoaded</code>, <code>isModified</code>,
	 *       <code>URI</code>, <code>errors</code>, <code>warnings</code>, and
	 *       <code>resourceSet</code> features of a resource</li>
	 *   <li>any change to the {@link Resource#getContents() contents} of
	 *       a resource while it is loading or unloading</li>
	 * </ul>
	 */
	public static final NotificationFilter READ = new ReadFilter();
	
	/**
	 * A filter matching "resource loaded" events.
	 */
	public static final NotificationFilter RESOURCE_LOADED = new NotificationFilter() {
		@Override
		public boolean matches(Notification notification) {
			return (notification.getNotifier() instanceof Resource)
					&& (notification.getFeatureID(Resource.class)
							== Resource.RESOURCE__IS_LOADED)
					&& !notification.getOldBooleanValue()
					&& notification.getNewBooleanValue();
		}};
	
	/**
	 * A filter matching "resource unloaded" events.
	 */
	public static final NotificationFilter RESOURCE_UNLOADED = new NotificationFilter() {
		@Override
		public boolean matches(Notification notification) {
			return (notification.getNotifier() instanceof Resource)
					&& (notification.getFeatureID(Resource.class)
							== Resource.RESOURCE__IS_LOADED)
					&& notification.getOldBooleanValue()
					&& !notification.getNewBooleanValue();
		}};
	
	/** Cannot be instantiated by clients. */
	NotificationFilter() {
		super();
	}
	
	/**
	 * Creates a filter matches any notification from a resource of the
	 * specified content type.  Because the determination of a resource's
	 * content type is costly, it is cached on the resource via an adapter and
	 * is cleared on any change to the resource's contents or other properties.
	 * 
	 * @param contentType the content type identifier to match
	 * 
	 * @return the filter
	 * 
	 * @throws IllegalArgumentException if the specified content type is not
	 *     defined
	 * 
	 * @see IContentType
	 */
	public static NotificationFilter createResourceContentTypeFilter(String contentType) {
		return new ResourceContentTypeFilter(contentType);
	}

	/**
	 * Creates a filter matching any notification from the specified notifier.
	 * 
	 * @param notifier a notifier (usually an {@link EObject}, {@link Resource},
	 *     or {@link ResourceSet}
	 * 
	 * @return the filter
	 */
	public static NotificationFilter createNotifierFilter(final Object notifier) {
		return new NotificationFilter() {
			@Override
			public boolean matches(Notification notification) {
				return notification.getNotifier() == notifier;
			}};
	}

	/**
	 * Creates a filter matching any notification of the specified type.
	 * 
	 * @param eventType the notification type (as defined by the
	 *    {@link Notification} interface)
	 * 
	 * @return the filter
	 */
	public static NotificationFilter createEventTypeFilter(final int eventType) {
		return new NotificationFilter() {
			@Override
			public boolean matches(Notification notification) {
				return notification.getEventType() == eventType;
			}};
	}

	/**
	 * Creates a filter matching any notification from the specified feature.
	 * 
	 * @param feature a structural feature meta-object
	 * 
	 * @return the filter
	 */
	public static NotificationFilter createFeatureFilter(final EStructuralFeature feature) {
		return new NotificationFilter() {
			@Override
			public boolean matches(Notification notification) {
				return notification.getFeature() == feature;
			}};
	}

	/**
	 * Creates a filter matching any notification from the specified feature.
	 * This variant is useful for notifiers that are not modeled via Ecore.
	 * 
	 * @param ownerType the notifier type as a Java class or interface
	 * @param featureId the feature's numeric ID
	 * 
	 * @return the filter
	 */
	public static NotificationFilter createFeatureFilter(
			final Class<?> ownerType, final int featureId) {
		return new NotificationFilter() {
			@Override
			public boolean matches(Notification notification) {
				return ownerType.isInstance(notification.getNotifier())
						&& (notification.getFeatureID(ownerType) == featureId);
			}};
	}


	/**
	 * Creates a filter matching any notification from the specified feature.
	 * This variant is useful for notifiers that are not modeled as
	 * {@link EClass}es.  For example, this supports the features of the
	 * {@link Resource} data type.
	 * 
	 * @param ownerType the notifier type as an Ecore classifier
	 * @param featureId the feature's numeric ID
	 * 
	 * @return the filter
	 */
	public static NotificationFilter createFeatureFilter(final EClassifier ownerType, final int featureId) {
		return new NotificationFilter() {
			@Override
			public boolean matches(Notification notification) {
				return ownerType.isInstance(notification.getNotifier())
						&& (notification.getFeatureID(ownerType.getInstanceClass()) == featureId);
			}};
	}

	/**
	 * Creates a filter matching notifications from any instance of the
	 * specified type.  This variant is useful for notifiers that are not
	 * modeled via Ecore.
	 * 
	 * @param type the notifier type as a Java class or interface
	 * 
	 * @return the filter
	 */
	public static NotificationFilter createNotifierTypeFilter(final Class<?> type) {
		return new NotificationFilter() {
			@Override
			public boolean matches(Notification notification) {
				return type.isInstance(notification.getNotifier());
			}};
	}

	/**
	 * Creates a filter matching notifications from any instance of the
	 * specified type.  This variant is useful for notifiers that are
	 * modeled via Ecore.
	 * 
	 * @param type the notifier type as an Ecore classifier
	 * 
	 * @return the filter
	 */
	public static NotificationFilter createNotifierTypeFilter(final EClassifier type) {
		return new NotificationFilter() {
			@Override
			public boolean matches(Notification notification) {
				return type.isInstance(notification.getNotifier());
			}};
	}
	
	/**
	 * Creates a new filter combining me with another as a boolean conjunction.
	 * The "and" operation short-circuits; the <code>other</code> filter is not
	 * consulted when I (the first filter) do not match.
	 * 
	 * @param other another filter (must not be <code>null</code>)
	 * 
	 * @return a new "and" filter
	 */
	public final NotificationFilter and(final NotificationFilter other) {
		return new NotificationFilter() {
			@Override
			public boolean matches(Notification notification) {
				return NotificationFilter.this.matches(notification)
						&& other.matches(notification);
			}};
	}
	
	/**
	 * Creates a new filter combining me with another as a boolean disjunction.
	 * The "or" operation short-circuits; the <code>other</code> filter is not
	 * consulted when I (the first filter) match.
	 * 
	 * @param other another filter (must not be <code>null</code>)
	 * 
	 * @return a new "or" filter
	 */
	public final NotificationFilter or(final NotificationFilter other) {
		return new NotificationFilter() {
			@Override
			public boolean matches(Notification notification) {
				return NotificationFilter.this.matches(notification)
						|| other.matches(notification);
			}};
	}
	
	/**
	 * Creates a new filter that is the boolean negation of me.
	 * 
	 * @return the opposite of me
	 */
	public final NotificationFilter negated() {
		return new NotificationFilter() {
			@Override
			public boolean matches(Notification notification) {
				return !NotificationFilter.this.matches(notification);
			}};
	}
	
	/**
	 * Determines whether a notification matches my filtering criteria.
	 * Notifications that match are passed to the listener for which I am
	 * filtering.
	 * 
	 * @param notification a notification
	 * 
	 * @return <code>true</code> if the notification should be passed to my
	 *     listener; <code>false</code>, otherwise
	 */
	public abstract boolean matches(Notification notification);

	/**
	 * A custom notification filter, implementing user-defined
	 * {@linkplain NotificationFilter#matches(Notification) selection criteria}.
	 * Custom notification filters are distinguished from the stock
	 * implementations created by the factory methods of the
	 * {@link NotificationFilter} class to ensure that the base class remains
	 * abstract.
	 * 
	 * @author Christian W. Damus (cdamus)
	 * 
	 * @since 1.3
	 */
	public abstract static class Custom extends NotificationFilter {
		
		/**
		 * Initializes me.
		 */
		public Custom() {
			super();
		}
	}
}
