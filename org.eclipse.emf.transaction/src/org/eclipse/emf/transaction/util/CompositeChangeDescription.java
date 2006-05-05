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
 * $Id: CompositeChangeDescription.java,v 1.4 2006/05/05 14:29:43 cdamus Exp $
 */
package org.eclipse.emf.transaction.util;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.BasicEMap;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.ecore.change.ChangeDescription;
import org.eclipse.emf.ecore.change.impl.ChangeDescriptionImpl;
import org.eclipse.emf.transaction.TransactionChangeDescription;

/**
 * A composition of zero or more change descriptions representing the
 * changes performed by transactions in a possibly nested structure.  In the
 * execution of a transaction, the changes that it makes in between nested
 * transactions and the changes made by those nested transactions are stored as
 * discrete {@link ChangeDescription}s in a composite.  The composite structure
 * is recursive.
 *
 * @author Christian W. Damus (cdamus)
 */
public class CompositeChangeDescription
	extends ChangeDescriptionImpl
	implements TransactionChangeDescription {

	private final List changes = new java.util.ArrayList();
	
	/**
	 * Queries whether I have no composed change descriptions.
	 * 
	 * @return <code>false</code> if I have any change descriptions;
	 *     <code>true</code>, otherwise
	 */
	public final boolean isEmpty() {
		return changes.isEmpty();
	}
	
	/**
	 * Removes any change descriptions that I may have.
	 */
	public final void clear() {
		changes.clear();
	}
	
	/**
	 * I can apply myself if all of my composed changes can apply themselves.
	 */
	public boolean canApply() {
		boolean result = true;
		
		for (Iterator iter = changes.iterator(); result && iter.hasNext();) {
			Object next = iter.next();
			
			if (next instanceof TransactionChangeDescription) {
				result = ((TransactionChangeDescription) next).canApply();
			}
		}
		
		return result;
	}
	
	// Documentation copied from the inherited method
	public void apply() {
		// must apply changes in the reverse order that they were added
		for (ListIterator iter = changes.listIterator(changes.size()); iter.hasPrevious();) {
			((ChangeDescription) iter.previous()).apply();
		}
		
		changes.clear();
	}

	// Documentation copied from the inherited method
	public void applyAndReverse() {
		// must apply changes in the reverse order that they were added
		for (ListIterator iter = changes.listIterator(changes.size()); iter.hasPrevious();) {
			((ChangeDescription) iter.previous()).applyAndReverse();
		}
		
		// invert the order of the changes for next apply-and-reverse
		Collections.reverse(changes);
	}
	
	/**
	 * Adds a change description to me.
	 * 
	 * @param change a new change description to add
	 */
	public void add(ChangeDescription change) {
		if (!isEmpty(change)) {
			// automatically flatten composites
			if (change instanceof CompositeChangeDescription) {
				CompositeChangeDescription other = ((CompositeChangeDescription) change);
				
				for (Iterator iter = other.changes.iterator(); iter.hasNext();) {
					add((ChangeDescription) iter.next());
				}
			} else {
				changes.add(change);
				
				if (objectChanges != null) {
					// already computed object changes.  Keep them up-to-date
					objectChanges.addAll(change.getObjectChanges());
				}
				
				if (objectsToAttach != null) {
					// already computed objects to attach.  Keep them up-to-date
					objectsToAttach.addAll(change.getObjectsToAttach());
				}
				
				if (objectsToDetach != null) {
					// already computed objects to detach.  Keep them up-to-date
					objectsToDetach.addAll(change.getObjectsToDetach());
				}
				
				if (resourceChanges != null) {
					// already computed resource changes.  Keep them up-to-date
					resourceChanges.addAll(change.getResourceChanges());
				}
			}
		}
	}
	
	/**
	 * Queries whether a change description has no changes.
	 * 
	 * @param changeDescription a change description (maybe <code>null</code>)
	 * 
	 * @return <code>true</code> if the specified change is <code>null</code>
	 *     or contains no changes; <code>false</code>, otherwise
	 */
	private boolean isEmpty(ChangeDescription changeDescription) {
		boolean result = changeDescription == null;
		
		if (!result) {
			if (changeDescription instanceof TransactionChangeDescription) {
				result = ((TransactionChangeDescription) changeDescription).isEmpty();
			} else {
				result = changeDescription.getObjectChanges().isEmpty()
					&& changeDescription.getObjectsToAttach().isEmpty()
					&& changeDescription.getResourceChanges().isEmpty();
			}
		}
		
		return result;
	}

	/**
	 * My object changes are the concatenation of the changes in my composed
	 * descriptions.
	 */
	public EMap getObjectChanges() {
		if (objectChanges == null) {
			objectChanges = new BasicEMap();
			
			for (Iterator iter = changes.iterator(); iter.hasNext();) {
				objectChanges.addAll(((ChangeDescription) iter.next()).getObjectChanges());
			}
		}
		
		return objectChanges;
	}

	/**
	 * My objects to attach are the concatenation of the changes in my composed
	 * descriptions.
	 */
	public EList getObjectsToDetach() {
		if (objectsToDetach == null) {
			objectsToDetach = new BasicEList();
			
			for (Iterator iter = changes.iterator(); iter.hasNext();) {
				objectsToDetach.addAll(((ChangeDescription) iter.next()).getObjectsToDetach());
			}
		}
		
		return objectsToDetach;
	}

	/**
	 * My objects to detach are the concatenation of the changes in my composed
	 * descriptions.
	 */
	public EList getObjectsToAttach() {
		if (objectsToAttach == null) {
			objectsToAttach = new BasicEList();
			
			for (Iterator iter = changes.iterator(); iter.hasNext();) {
				objectsToAttach.addAll(((ChangeDescription) iter.next()).getObjectsToAttach());
			}
		}
		
		return objectsToAttach;
	}

	/**
	 * My resource changes are the concatenation of the changes in my composed
	 * descriptions.
	 */
	public EList getResourceChanges() {
		if (resourceChanges == null) {
			resourceChanges = new BasicEList();
			
			for (Iterator iter = changes.iterator(); iter.hasNext();) {
				resourceChanges.addAll(((ChangeDescription) iter.next()).getResourceChanges());
			}
		}
		
		return resourceChanges;
	}
	
	public String toString() {
		StringBuffer result = new StringBuffer("CompositeChangeDescription["); //$NON-NLS-1$
		result.append(getObjectChanges().size()).append(", "); //$NON-NLS-1$
		result.append(getObjectsToAttach().size()).append(", "); //$NON-NLS-1$
		result.append(getObjectsToDetach().size()).append(", "); //$NON-NLS-1$
		result.append(getResourceChanges().size()).append(']');
		return result.toString();
	}
}
