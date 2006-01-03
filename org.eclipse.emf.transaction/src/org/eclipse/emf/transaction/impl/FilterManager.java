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
 * $Id: FilterManager.java,v 1.1 2006/01/03 20:41:54 cdamus Exp $
 */
package org.eclipse.emf.transaction.impl;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.transaction.NotificationFilter;

/**
 * An object that manages the filtering of notifications.  This class can implement
 * optimizations to reduce the effort of filtering notification lists for listeners
 * that have similar filters.
 *
 * @author Christian W. Damus (cdamus)
 */
public final class FilterManager {
	private static final FilterManager INSTANCE = new FilterManager();
	
	/**
	 * Not instantiable by clients.
	 */
	private FilterManager() {
		super();
	}
	
	/**
	 * Obtains the singleton instance of this class.
	 * 
	 * @return the singleton instance
	 */
	public static FilterManager getInstance() {
		return INSTANCE;
	}
	
	/**
	 * Selects the notifications in the given list that match the specified
	 * filter.
	 * <p>
	 * For unbatched notifications, it is better to use the
	 * {@link #selectUnbatched(List, NotificationFilter)} method.
	 * </p>
	 * 
	 * @param notifications a list of notifications to select from
	 * @param filter a notification filter
	 * 
	 * @return the notifications that match the filter
	 * 
	 * @see #selectUnbatched(List, NotificationFilter)
	 */
	public List select(List notifications, NotificationFilter filter) {
		List result = new java.util.ArrayList();
		
		if (filter == null) {
			// the default filter
			filter = NotificationFilter.NOT_TOUCH;
		}
		
		for (Iterator iter = notifications.iterator(); iter.hasNext();) {
			Notification next = (Notification) iter.next();
			
			if (filter.matches(next)) {
				result.add(next);
			}
		}
		
		return result;
	}
	
	/**
	 * Selects the notifications in the given singleton list of an unbatched
	 * notification that match the specified filter.  The result is, thus,
	 * either an empty list or the original list back again.
	 * <p>
	 * This method is more efficient for processing unbatched notifications than
	 * is the {@link #select(List, NotificationFilter)} method.
	 * </p>
	 * 
	 * @param notification a singleton list containing the unbatched
	 *     notification
	 * @param filter a notification filter
	 * 
	 * @return the original list or an empty list, according to the filter
	 * 
	 * @see #select(List, NotificationFilter)
	 */
	public List selectUnbatched(List notification, NotificationFilter filter) {
		List result;
		
		if (filter == null) {
			// the default filter
			filter = NotificationFilter.NOT_TOUCH;
		}

		if (filter.matches((Notification) notification.get(0))) {
			result = notification;
		} else {
			result = Collections.EMPTY_LIST;
		}
		
		return result;
	}
}
